# Phase 2: EC2-based PostgreSQL Infrastructure
# Cost-optimized PostgreSQL deployment using EC2 instances

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

# Get latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]
  
  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
  
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Local values
locals {
  project_name = "oddiya-ec2-postgresql"
  environment  = var.environment
  
  common_tags = {
    Environment = var.environment
    Project     = "Oddiya"
    Phase       = "2-EC2-PostgreSQL"
    ManagedBy   = "Terraform"
    CostCenter  = "Development"
  }
}

# ==========================================
# VPC and Networking
# ==========================================

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-vpc"
  })
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-igw"
  })
}

# Public Subnets
resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 1}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-public-subnet-${count.index + 1}"
    Type = "Public"
  })
}

# Private Subnets for Database
resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 10}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-private-subnet-${count.index + 1}"
    Type = "Private"
  })
}

# NAT Gateway for private subnets
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? 1 : 0
  domain = "vpc"

  depends_on = [aws_internet_gateway.main]

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-nat-eip"
  })
}

resource "aws_nat_gateway" "main" {
  count = var.enable_nat_gateway ? 1 : 0

  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  depends_on = [aws_internet_gateway.main]

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-nat-gateway"
  })
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-public-rt"
  })
}

resource "aws_route_table" "private" {
  count  = var.enable_nat_gateway ? 1 : 0
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[0].id
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-private-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count = var.enable_nat_gateway ? 2 : 0

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[0].id
}

# ==========================================
# S3 Bucket for Database Backups
# ==========================================

resource "aws_s3_bucket" "db_backups" {
  bucket = "${local.project_name}-db-backups-${random_id.bucket_suffix.hex}"

  tags = merge(local.common_tags, {
    Name    = "${local.project_name}-db-backups"
    Purpose = "PostgreSQL Backups"
  })
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket_versioning" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "db_backups" {
  bucket = aws_s3_bucket.db_backups.id

  rule {
    id     = "backup_lifecycle"
    status = "Enabled"

    expiration {
      days = var.backup_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

# ==========================================
# EC2 Instance for PostgreSQL Database
# ==========================================

# Key pair for EC2 access
resource "aws_key_pair" "db_key" {
  key_name   = "${local.project_name}-db-key"
  public_key = var.ec2_public_key

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-key"
  })
}

# Random password for PostgreSQL
resource "random_password" "db_password" {
  length  = 16
  special = true
}

# Store database credentials in AWS Secrets Manager
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${local.project_name}-db-credentials"
  description = "PostgreSQL database credentials"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-credentials"
  })
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    host     = aws_instance.postgresql.private_ip
    port     = "5432"
    database = var.db_name
    username = var.db_username
    password = random_password.db_password.result
  })
}

# PostgreSQL EC2 Instance
resource "aws_instance" "postgresql" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = var.db_instance_type
  key_name                    = aws_key_pair.db_key.key_name
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.database.id]
  associate_public_ip_address = false

  # EBS optimization for better I/O performance
  ebs_optimized = true

  # Root volume configuration
  root_block_device {
    volume_type = "gp3"
    volume_size = var.db_root_volume_size
    encrypted   = true
    throughput  = 125
    iops        = 3000

    tags = merge(local.common_tags, {
      Name = "${local.project_name}-db-root-volume"
    })
  }

  # Dedicated EBS volume for PostgreSQL data
  ebs_block_device {
    device_name = "/dev/sdf"
    volume_type = "gp3"
    volume_size = var.db_data_volume_size
    encrypted   = true
    throughput  = 250
    iops        = 4000

    tags = merge(local.common_tags, {
      Name = "${local.project_name}-db-data-volume"
    })
  }

  # IAM instance profile for AWS access
  iam_instance_profile = aws_iam_instance_profile.db_instance_profile.name

  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    db_name         = var.db_name
    db_username     = var.db_username
    db_password     = random_password.db_password.result
    s3_bucket       = aws_s3_bucket.db_backups.bucket
    aws_region      = var.aws_region
    backup_schedule = var.backup_schedule
  }))

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-postgresql-db"
    Role = "Database"
  })

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# Elastic IP for database instance (optional, for consistent access)
resource "aws_eip" "db_eip" {
  count    = var.enable_db_eip ? 1 : 0
  instance = aws_instance.postgresql.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-eip"
  })

  depends_on = [aws_internet_gateway.main]
}