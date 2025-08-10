# Cost-optimized storage module with lifecycle management

locals {
  # S3 bucket naming
  bucket_prefix = replace(lower(var.name_prefix), "_", "-")
  
  # Lifecycle transitions for cost optimization
  lifecycle_transitions = var.s3_lifecycle_rules.enable_intelligent_tiering ? [
    {
      days          = 0
      storage_class = "INTELLIGENT_TIERING"
    }
  ] : [
    {
      days          = var.s3_lifecycle_rules.transition_to_ia_days
      storage_class = "STANDARD_IA"
    },
    {
      days          = var.s3_lifecycle_rules.transition_to_glacier_days
      storage_class = "GLACIER"
    },
    {
      days          = var.s3_lifecycle_rules.transition_to_glacier_days + 90
      storage_class = "DEEP_ARCHIVE"
    }
  ]
}

# Main S3 bucket with cost optimization
resource "aws_s3_bucket" "main" {
  bucket_prefix = "${local.bucket_prefix}-"
  
  tags = merge(var.tags, {
    Name        = "${var.name_prefix}-main-bucket"
    CostOptimized = "true"
  })
}

# Enable versioning for data protection
resource "aws_s3_bucket_versioning" "main" {
  bucket = aws_s3_bucket.main.id
  
  versioning_configuration {
    status = "Enabled"
  }
}

# Server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "main" {
  bucket = aws_s3_bucket.main.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"  # Use S3-managed keys (free)
    }
  }
}

# Lifecycle rules for cost optimization
resource "aws_s3_bucket_lifecycle_configuration" "main" {
  bucket = aws_s3_bucket.main.id
  
  # Intelligent Tiering or manual transitions
  rule {
    id     = "cost-optimization"
    status = "Enabled"
    
    dynamic "transition" {
      for_each = local.lifecycle_transitions
      content {
        days          = transition.value.days
        storage_class = transition.value.storage_class
      }
    }
    
    # Delete old versions to save costs
    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "GLACIER"
    }
    
    noncurrent_version_transition {
      noncurrent_days = 90
      storage_class   = "DEEP_ARCHIVE"
    }
    
    noncurrent_version_expiration {
      noncurrent_days = 365
    }
    
    # Expire objects after defined period
    expiration {
      days = var.s3_lifecycle_rules.expire_days
    }
    
    # Clean up incomplete multipart uploads
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
  
  # Delete expired object delete markers
  rule {
    id     = "delete-markers"
    status = "Enabled"
    
    expiration {
      expired_object_delete_marker = true
    }
  }
}

# Intelligent Tiering configuration
resource "aws_s3_bucket_intelligent_tiering_configuration" "main" {
  count = var.s3_lifecycle_rules.enable_intelligent_tiering ? 1 : 0
  
  bucket = aws_s3_bucket.main.id
  name   = "entire-bucket"
  
  # Archive configurations for additional savings
  tiering {
    access_tier = "ARCHIVE_ACCESS"
    days        = 90
  }
  
  tiering {
    access_tier = "DEEP_ARCHIVE_ACCESS"
    days        = 180
  }
}

# Request metrics for cost analysis
resource "aws_s3_bucket_metric" "main" {
  bucket = aws_s3_bucket.main.id
  name   = "entire-bucket"
}

# Public access block for security
resource "aws_s3_bucket_public_access_block" "main" {
  bucket = aws_s3_bucket.main.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 bucket for logs (using cheaper storage)
resource "aws_s3_bucket" "logs" {
  bucket_prefix = "${local.bucket_prefix}-logs-"
  
  tags = merge(var.tags, {
    Name    = "${var.name_prefix}-logs-bucket"
    Purpose = "Centralized logging"
  })
}

# Lifecycle for log bucket - aggressive archival
resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  
  rule {
    id     = "log-archival"
    status = "Enabled"
    
    # Move to cheaper storage quickly
    transition {
      days          = 1
      storage_class = "STANDARD_IA"
    }
    
    transition {
      days          = 30
      storage_class = "GLACIER"
    }
    
    transition {
      days          = 90
      storage_class = "DEEP_ARCHIVE"
    }
    
    # Delete old logs
    expiration {
      days = 365
    }
  }
}

# EBS Snapshot lifecycle manager for cost optimization
resource "aws_dlm_lifecycle_policy" "ebs_snapshots" {
  description        = "Cost-optimized EBS snapshot policy"
  execution_role_arn = aws_iam_role.dlm.arn
  state              = "ENABLED"
  
  policy_details {
    resource_types = ["VOLUME"]
    
    schedule {
      name = "daily-snapshots"
      
      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["03:00"]
      }
      
      retain_rule {
        count = var.ebs_optimization.snapshot_retention_days
      }
      
      tags_to_add = {
        SnapshotCreator = "DLM"
        Environment     = var.tags["Environment"]
      }
      
      copy_tags = true
    }
    
    target_tags = {
      Snapshot = "true"
    }
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-snapshot-policy"
  })
}

# IAM role for DLM
resource "aws_iam_role" "dlm" {
  name_prefix = "${var.name_prefix}-dlm-"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "dlm.amazonaws.com"
        }
      }
    ]
  })
  
  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "dlm" {
  role       = aws_iam_role.dlm.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}

# EFS for shared storage (pay-per-use)
resource "aws_efs_file_system" "shared" {
  count = var.enable_efs ? 1 : 0
  
  # Use One Zone storage for 47% cost savings
  availability_zone_name = var.efs_single_az ? data.aws_availability_zones.available.names[0] : null
  
  # Lifecycle management for cost optimization
  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }
  
  lifecycle_policy {
    transition_to_primary_storage_class = "AFTER_1_ACCESS"
  }
  
  # Use Standard storage class (cheaper than Max I/O)
  performance_mode = "generalPurpose"
  throughput_mode  = "bursting"  # Pay-per-use instead of provisioned
  
  encrypted = true
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-efs"
    Type = var.efs_single_az ? "OneZone" : "Regional"
  })
}

# Data source for AZs
data "aws_availability_zones" "available" {
  state = "available"
}