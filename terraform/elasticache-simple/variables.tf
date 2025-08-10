variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "dev"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "vpc_id" {
  description = "VPC ID for ElastiCache"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for ElastiCache (minimum 2 for Multi-AZ)"
  type        = list(string)
}

variable "deployment_mode" {
  description = "Deployment mode: 'single' for single node or 'cluster' for replication group"
  type        = string
  default     = "single"
  
  validation {
    condition     = contains(["single", "cluster"], var.deployment_mode)
    error_message = "Deployment mode must be 'single' or 'cluster'."
  }
}

variable "node_type" {
  description = "ElastiCache node type (t4g.micro = ~$12/month)"
  type        = string
  default     = "cache.t4g.micro"  # Cheapest option with Graviton
}

variable "num_cache_nodes" {
  description = "Number of cache nodes (only for cluster mode)"
  type        = number
  default     = 2
  
  validation {
    condition     = var.num_cache_nodes >= 1 && var.num_cache_nodes <= 6
    error_message = "Number of cache nodes must be between 1 and 6."
  }
}

variable "redis_version" {
  description = "Redis version"
  type        = string
  default     = "7.0"
}

variable "redis_family" {
  description = "Redis parameter group family"
  type        = string
  default     = "redis7"
}

variable "redis_port" {
  description = "Redis port"
  type        = number
  default     = 6379
}

variable "eviction_policy" {
  description = "Redis eviction policy when memory is full"
  type        = string
  default     = "allkeys-lru"
  
  validation {
    condition = contains([
      "volatile-lru", "allkeys-lru", "volatile-lfu", "allkeys-lfu",
      "volatile-random", "allkeys-random", "volatile-ttl", "noeviction"
    ], var.eviction_policy)
    error_message = "Invalid eviction policy."
  }
}

variable "backup_retention_days" {
  description = "Number of days to retain backups (0 to disable)"
  type        = number
  default     = 1  # Minimal backup for cost savings
}

variable "backup_window" {
  description = "Daily backup window (UTC)"
  type        = string
  default     = "03:00-04:00"
}

variable "maintenance_window" {
  description = "Weekly maintenance window"
  type        = string
  default     = "sun:04:00-sun:05:00"
}

variable "enable_encryption" {
  description = "Enable encryption at rest and in transit"
  type        = bool
  default     = false  # Disabled for cost savings
}

variable "enable_logs" {
  description = "Enable CloudWatch logs (adds cost)"
  type        = bool
  default     = false
}

variable "enable_monitoring" {
  description = "Enable CloudWatch alarms"
  type        = bool
  default     = true
}

variable "allowed_security_groups" {
  description = "Security groups allowed to access Redis"
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access Redis"
  type        = list(string)
  default     = []
}

variable "sns_topic_arn" {
  description = "SNS topic ARN for notifications"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}