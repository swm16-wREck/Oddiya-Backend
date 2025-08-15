# Variables for Phase 3 Redis Infrastructure
# Agent 1 - EC2 Infrastructure Specialist

variable "aws_region" {
  description = "AWS region for Redis deployment"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "oddiya"
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for Redis VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "management_cidr" {
  description = "CIDR block for management access (SSH)"
  type        = string
  default     = "10.0.0.0/8"  # Adjust based on your management network
}

variable "app_cidr" {
  description = "CIDR block for application access to Redis"
  type        = string
  default     = "172.31.0.0/16"  # Default VPC CIDR - adjust for your app VPC
}

# Redis Configuration
variable "redis_node_count" {
  description = "Number of Redis nodes (master + slaves)"
  type        = number
  default     = 3
  
  validation {
    condition     = var.redis_node_count >= 3 && var.redis_node_count <= 7
    error_message = "Redis node count must be between 3 and 7 for proper HA setup."
  }
}

variable "redis_instance_type" {
  description = "EC2 instance type for Redis nodes"
  type        = string
  default     = "t3.medium"
}

variable "redis_instance_type_spot" {
  description = "Alternative EC2 instance type for spot instances"
  type        = string
  default     = "t3.large"
}

variable "redis_disk_size" {
  description = "EBS volume size in GB for Redis persistence"
  type        = number
  default     = 50
}

variable "redis_disk_iops" {
  description = "EBS IOPS for Redis volume (gp3)"
  type        = number
  default     = 3000
}

variable "redis_disk_throughput" {
  description = "EBS throughput in MB/s for Redis volume (gp3)"
  type        = number
  default     = 250
}

# Cost Optimization
variable "redis_on_demand_base" {
  description = "Number of on-demand instances to maintain"
  type        = number
  default     = 1
}

variable "redis_on_demand_percentage" {
  description = "Percentage of on-demand instances above base capacity"
  type        = number
  default     = 50
}

# Redis Software Configuration
variable "redis_version" {
  description = "Redis version to install"
  type        = string
  default     = "7.2"
}

variable "redis_password" {
  description = "Redis authentication password"
  type        = string
  sensitive   = true
  default     = "change-me-in-production"
}

variable "redis_public_key" {
  description = "Public key for EC2 instance access"
  type        = string
  default     = ""  # Must be provided
}

# Monitoring and Backup
variable "backup_retention_days" {
  description = "Number of days to retain Redis backups in S3"
  type        = number
  default     = 30
}

variable "log_retention_days" {
  description = "Number of days to retain CloudWatch logs"
  type        = number
  default     = 14
}

# Security
variable "enable_deletion_protection" {
  description = "Enable deletion protection for load balancers"
  type        = bool
  default     = false
}

# Memory Configuration
variable "redis_max_memory" {
  description = "Maximum memory for Redis in MB (auto-calculated if 0)"
  type        = number
  default     = 0  # Will be calculated as 75% of available instance memory
}

variable "redis_max_memory_policy" {
  description = "Redis memory eviction policy"
  type        = string
  default     = "allkeys-lru"
  
  validation {
    condition = contains([
      "noeviction", "allkeys-lru", "volatile-lru", 
      "allkeys-random", "volatile-random", "volatile-ttl"
    ], var.redis_max_memory_policy)
    error_message = "Invalid memory policy. Must be one of: noeviction, allkeys-lru, volatile-lru, allkeys-random, volatile-random, volatile-ttl."
  }
}

# Persistence Configuration
variable "redis_save_enabled" {
  description = "Enable Redis RDB persistence"
  type        = bool
  default     = true
}

variable "redis_aof_enabled" {
  description = "Enable Redis AOF persistence"
  type        = bool
  default     = true
}

# Sentinel Configuration
variable "sentinel_enabled" {
  description = "Enable Redis Sentinel for high availability"
  type        = bool
  default     = true
}

variable "sentinel_quorum" {
  description = "Number of sentinels needed to agree on master failure"
  type        = number
  default     = 2
  
  validation {
    condition     = var.sentinel_quorum >= 1 && var.sentinel_quorum <= 7
    error_message = "Sentinel quorum must be between 1 and 7."
  }
}

# Network Configuration
variable "redis_port" {
  description = "Redis server port"
  type        = number
  default     = 6379
}

variable "sentinel_port" {
  description = "Redis Sentinel port"
  type        = number
  default     = 26379
}

# Performance Tuning
variable "redis_tcp_keepalive" {
  description = "TCP keepalive value for Redis connections"
  type        = number
  default     = 300
}

variable "redis_timeout" {
  description = "Client idle timeout in seconds (0 = disabled)"
  type        = number
  default     = 0
}

variable "redis_tcp_backlog" {
  description = "TCP backlog size for Redis"
  type        = number
  default     = 511
}

# Monitoring Configuration
variable "enable_cloudwatch_monitoring" {
  description = "Enable detailed CloudWatch monitoring"
  type        = bool
  default     = true
}

variable "monitoring_interval" {
  description = "Monitoring metrics collection interval in seconds"
  type        = number
  default     = 60
}

# Backup Configuration
variable "backup_schedule" {
  description = "Cron schedule for automated backups (UTC)"
  type        = string
  default     = "0 2 * * *"  # Daily at 2 AM UTC
}

variable "backup_compression" {
  description = "Enable backup compression"
  type        = bool
  default     = true
}

# SSL/TLS Configuration
variable "redis_tls_enabled" {
  description = "Enable TLS for Redis connections"
  type        = bool
  default     = false  # Can be enabled later for enhanced security
}

variable "redis_tls_port" {
  description = "Redis TLS port"
  type        = number
  default     = 6380
}

# Cluster Configuration (for future scaling)
variable "redis_cluster_enabled" {
  description = "Enable Redis Cluster mode (experimental)"
  type        = bool
  default     = false
}

variable "cluster_slots_per_node" {
  description = "Number of hash slots per cluster node"
  type        = number
  default     = 5461  # 16384 / 3 nodes
}

# Resource Tags
variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}