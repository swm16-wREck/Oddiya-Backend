# Simple ElastiCache Module for Redis
# Cost-optimized configuration for small services

terraform {
  required_version = ">= 1.5.0"
  
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
data "aws_vpc" "main" {
  id = var.vpc_id
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ElastiCache Subnet Group
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name_prefix}-cache-subnet"
  subnet_ids = var.subnet_ids
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-cache-subnet-group"
  })
}

# Security Group for ElastiCache
resource "aws_security_group" "elasticache" {
  name_prefix = "${var.name_prefix}-elasticache-"
  vpc_id      = var.vpc_id
  description = "Security group for ElastiCache Redis"
  
  ingress {
    from_port       = var.redis_port
    to_port         = var.redis_port
    protocol        = "tcp"
    security_groups = var.allowed_security_groups
    description     = "Redis access from application"
  }
  
  ingress {
    from_port   = var.redis_port
    to_port     = var.redis_port
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
    description = "Redis access from allowed CIDRs"
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-elasticache-sg"
  })
  
  lifecycle {
    create_before_destroy = true
  }
}

# Parameter Group for Redis
resource "aws_elasticache_parameter_group" "redis" {
  name   = "${var.name_prefix}-redis-params"
  family = var.redis_family
  
  # Cost-optimized parameters
  parameter {
    name  = "maxmemory-policy"
    value = var.eviction_policy
  }
  
  parameter {
    name  = "timeout"
    value = "300"  # Close idle connections after 5 minutes
  }
  
  parameter {
    name  = "tcp-keepalive"
    value = "300"
  }
  
  parameter {
    name  = "tcp-backlog"
    value = "511"
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis-params"
  })
}

# Option 1: Single Node Redis (Cheapest - ~$12/month for t4g.micro)
resource "aws_elasticache_cluster" "single_node" {
  count = var.deployment_mode == "single" ? 1 : 0
  
  cluster_id           = "${var.name_prefix}-redis"
  engine              = "redis"
  engine_version      = var.redis_version
  node_type           = var.node_type
  num_cache_nodes     = 1
  parameter_group_name = aws_elasticache_parameter_group.redis.name
  port                = var.redis_port
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.elasticache.id]
  
  # Maintenance window (lowest usage time)
  maintenance_window = var.maintenance_window
  
  # Backup configuration (minimal for cost)
  snapshot_retention_limit = var.backup_retention_days
  snapshot_window         = var.backup_window
  
  # Notifications
  notification_topic_arn = var.sns_topic_arn
  
  # Auto Minor Version Upgrade
  auto_minor_version_upgrade = true
  
  # CloudWatch logs (disabled for cost)
  log_delivery_configuration {
    destination      = var.enable_logs ? aws_cloudwatch_log_group.redis[0].name : ""
    destination_type = var.enable_logs ? "cloudwatch-logs" : ""
    log_format      = "json"
    log_type        = "slow-log"
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis-single"
    Type = "single-node"
  })
  
  # Apply immediately for dev, during maintenance window for prod
  apply_immediately = var.environment != "prod"
}

# Option 2: Redis Replication Group (HA - ~$25-50/month)
resource "aws_elasticache_replication_group" "redis" {
  count = var.deployment_mode == "cluster" ? 1 : 0
  
  replication_group_id       = "${var.name_prefix}-redis"
  replication_group_description = "Redis cluster for ${var.name_prefix}"
  
  engine               = "redis"
  engine_version       = var.redis_version
  node_type           = var.node_type
  number_cache_clusters = var.num_cache_nodes
  parameter_group_name = aws_elasticache_parameter_group.redis.name
  port                = var.redis_port
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.elasticache.id]
  
  # Enable Multi-AZ for production only
  automatic_failover_enabled = var.environment == "prod"
  multi_az_enabled          = var.environment == "prod"
  
  # Backup configuration
  snapshot_retention_limit = var.backup_retention_days
  snapshot_window         = var.backup_window
  
  # Maintenance
  maintenance_window = var.maintenance_window
  
  # Encryption (for sensitive data)
  at_rest_encryption_enabled = var.enable_encryption
  transit_encryption_enabled = var.enable_encryption
  auth_token                = var.enable_encryption ? random_password.auth_token[0].result : null
  
  # Auto Minor Version Upgrade
  auto_minor_version_upgrade = true
  
  # Notifications
  notification_topic_arn = var.sns_topic_arn
  
  # CloudWatch logs
  dynamic "log_delivery_configuration" {
    for_each = var.enable_logs ? ["slow-log"] : []
    content {
      destination      = aws_cloudwatch_log_group.redis[0].name
      destination_type = "cloudwatch-logs"
      log_format      = "json"
      log_type        = log_delivery_configuration.value
    }
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis-cluster"
    Type = "replication-group"
  })
  
  apply_immediately = var.environment != "prod"
}

# CloudWatch Log Group (optional)
resource "aws_cloudwatch_log_group" "redis" {
  count = var.enable_logs ? 1 : 0
  
  name              = "/aws/elasticache/${var.name_prefix}"
  retention_in_days = 7  # Minimal retention for cost
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis-logs"
  })
}

# Random password for auth token
resource "random_password" "auth_token" {
  count   = var.enable_encryption ? 1 : 0
  length  = 32
  special = true
}

# Store auth token in SSM
resource "aws_ssm_parameter" "redis_auth_token" {
  count = var.enable_encryption ? 1 : 0
  
  name  = "/${var.name_prefix}/redis/auth-token"
  type  = "SecureString"
  value = random_password.auth_token[0].result
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis-auth-token"
  })
}

# CloudWatch Alarms for monitoring
resource "aws_cloudwatch_metric_alarm" "cache_cpu" {
  count = var.enable_monitoring ? 1 : 0
  
  alarm_name          = "${var.name_prefix}-redis-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name        = "CPUUtilization"
  namespace          = "AWS/ElastiCache"
  period             = "300"
  statistic          = "Average"
  threshold          = "75"
  alarm_description  = "Redis CPU utilization"
  alarm_actions      = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
  
  dimensions = {
    CacheClusterId = var.deployment_mode == "single" ? 
      aws_elasticache_cluster.single_node[0].id : 
      aws_elasticache_replication_group.redis[0].id
  }
  
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "cache_memory" {
  count = var.enable_monitoring ? 1 : 0
  
  alarm_name          = "${var.name_prefix}-redis-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name        = "DatabaseMemoryUsagePercentage"
  namespace          = "AWS/ElastiCache"
  period             = "300"
  statistic          = "Average"
  threshold          = "80"
  alarm_description  = "Redis memory usage"
  alarm_actions      = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
  
  dimensions = {
    CacheClusterId = var.deployment_mode == "single" ? 
      aws_elasticache_cluster.single_node[0].id : 
      aws_elasticache_replication_group.redis[0].id
  }
  
  tags = var.tags
}