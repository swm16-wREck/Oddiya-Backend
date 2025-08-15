# Variables for EC2 PostgreSQL Infrastructure

# ==========================================
# General Configuration
# ==========================================

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

# ==========================================
# Network Configuration
# ==========================================

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway for private subnets"
  type        = bool
  default     = false
}

variable "ssh_allowed_cidrs" {
  description = "CIDR blocks allowed for SSH access"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "db_admin_cidrs" {
  description = "CIDR blocks allowed for database admin access"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "monitoring_allowed_cidrs" {
  description = "CIDR blocks allowed for monitoring access"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

# ==========================================
# EC2 Configuration
# ==========================================

variable "db_instance_type" {
  description = "EC2 instance type for PostgreSQL database"
  type        = string
  default     = "t3.medium"
  
  validation {
    condition = contains([
      "t3.small", "t3.medium", "t3.large", "t3.xlarge",
      "m5.large", "m5.xlarge", "m5.2xlarge",
      "r5.large", "r5.xlarge", "r5.2xlarge"
    ], var.db_instance_type)
    error_message = "Instance type must be a valid EC2 instance type suitable for database workloads."
  }
}

variable "ec2_public_key" {
  description = "Public key for EC2 key pair (SSH access)"
  type        = string
  sensitive   = true
}

variable "db_root_volume_size" {
  description = "Size of root EBS volume in GB"
  type        = number
  default     = 20
}

variable "db_data_volume_size" {
  description = "Size of PostgreSQL data EBS volume in GB"
  type        = number
  default     = 50
}

variable "enable_db_eip" {
  description = "Enable Elastic IP for database instance"
  type        = bool
  default     = false
}

# ==========================================
# Database Configuration
# ==========================================

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "oddiya"
  
  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]*$", var.db_name))
    error_message = "Database name must start with a letter and contain only letters, numbers, and underscores."
  }
}

variable "db_username" {
  description = "Master username for PostgreSQL database"
  type        = string
  default     = "postgres"
  
  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]*$", var.db_username))
    error_message = "Database username must start with a letter and contain only letters, numbers, and underscores."
  }
}

variable "postgresql_version" {
  description = "PostgreSQL version to install"
  type        = string
  default     = "15"
  
  validation {
    condition     = contains(["13", "14", "15", "16"], var.postgresql_version)
    error_message = "PostgreSQL version must be 13, 14, 15, or 16."
  }
}

variable "enable_postgis" {
  description = "Enable PostGIS extension"
  type        = bool
  default     = true
}

# ==========================================
# Backup Configuration
# ==========================================

variable "backup_retention_days" {
  description = "Number of days to retain database backups"
  type        = number
  default     = 30
  
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 365
    error_message = "Backup retention must be between 1 and 365 days."
  }
}

variable "backup_schedule" {
  description = "Cron expression for backup schedule"
  type        = string
  default     = "0 2 * * *"  # Daily at 2 AM
}

# ==========================================
# Monitoring Configuration
# ==========================================

variable "enable_monitoring" {
  description = "Enable monitoring and alerting"
  type        = bool
  default     = true
}

variable "monitoring_instance_type" {
  description = "EC2 instance type for monitoring server"
  type        = string
  default     = "t3.small"
}

# ==========================================
# Bastion Host Configuration
# ==========================================

variable "enable_bastion" {
  description = "Enable bastion host for secure access"
  type        = bool
  default     = false
}

variable "bastion_allowed_cidrs" {
  description = "CIDR blocks allowed to access bastion host"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "bastion_instance_type" {
  description = "EC2 instance type for bastion host"
  type        = string
  default     = "t3.nano"
}

# ==========================================
# Cost Optimization
# ==========================================

variable "enable_spot_instances" {
  description = "Use spot instances for non-critical components"
  type        = bool
  default     = false
}

variable "enable_scheduled_scaling" {
  description = "Enable scheduled start/stop for development environments"
  type        = bool
  default     = false
}

variable "schedule_start_cron" {
  description = "Cron expression for starting instances"
  type        = string
  default     = "0 8 * * 1-5"  # 8 AM weekdays
}

variable "schedule_stop_cron" {
  description = "Cron expression for stopping instances"
  type        = string
  default     = "0 18 * * 1-5"  # 6 PM weekdays
}

# ==========================================
# Performance Configuration
# ==========================================

variable "enable_performance_insights" {
  description = "Enable performance monitoring and insights"
  type        = bool
  default     = true
}

variable "max_connections" {
  description = "Maximum number of PostgreSQL connections"
  type        = number
  default     = 100
  
  validation {
    condition     = var.max_connections >= 10 && var.max_connections <= 1000
    error_message = "Max connections must be between 10 and 1000."
  }
}

variable "shared_buffers" {
  description = "PostgreSQL shared_buffers setting (as percentage of RAM)"
  type        = string
  default     = "25%"
}

variable "effective_cache_size" {
  description = "PostgreSQL effective_cache_size setting (as percentage of RAM)"
  type        = string
  default     = "75%"
}

# ==========================================
# High Availability Configuration
# ==========================================

variable "enable_read_replica" {
  description = "Enable read replica for high availability"
  type        = bool
  default     = false
}

variable "replica_instance_type" {
  description = "EC2 instance type for read replica"
  type        = string
  default     = "t3.medium"
}

variable "enable_failover" {
  description = "Enable automatic failover configuration"
  type        = bool
  default     = false
}

# ==========================================
# Alerting Configuration
# ==========================================

variable "alert_email" {
  description = "Email address for alerts (leave empty to disable email alerts)"
  type        = string
  default     = ""
}

variable "enable_custom_metrics" {
  description = "Enable custom PostgreSQL metrics collection via Lambda"
  type        = bool
  default     = false
}