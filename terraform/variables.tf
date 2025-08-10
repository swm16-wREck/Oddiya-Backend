# Variables for Oddiya AWS Infrastructure

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

variable "data_subnet_cidrs" {
  description = "CIDR blocks for data subnets"
  type        = list(string)
  default     = ["10.0.21.0/24", "10.0.22.0/24"]
}

# RDS Configuration
variable "db_name" {
  description = "Database name"
  type        = string
  default     = "oddiya"
}

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "oddiya_admin"
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = object({
    writer = string
    reader = string
  })
  default = {
    writer = "db.r6g.large"
    reader = "db.t4g.medium"
  }
}

variable "backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 7
}

# ElastiCache Configuration
variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.r6g.large"
}

variable "redis_num_nodes" {
  description = "Number of cache nodes"
  type        = number
  default     = 2
}

# ALB Configuration
variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS"
  type        = string
  default     = ""
}

# ECS Configuration
variable "ecs_services" {
  description = "ECS service configurations"
  type = map(object({
    cpu         = number
    memory      = number
    desired_count = number
    min_count   = number
    max_count   = number
  }))
  default = {
    travel-service = {
      cpu         = 1024
      memory      = 2048
      desired_count = 2
      min_count   = 2
      max_count   = 10
    }
    user-service = {
      cpu         = 512
      memory      = 1024
      desired_count = 2
      min_count   = 1
      max_count   = 5
    }
    notification-service = {
      cpu         = 512
      memory      = 1024
      desired_count = 1
      min_count   = 1
      max_count   = 3
    }
  }
}

# Lambda Configuration
variable "lambda_memory_size" {
  description = "Lambda function memory size"
  type        = number
  default     = 3008
}

variable "lambda_timeout" {
  description = "Lambda function timeout in seconds"
  type        = number
  default     = 900
}

# Tags
variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default = {
    Project     = "Oddiya"
    Team        = "DevOps"
    CostCenter  = "Engineering"
  }
}

# Supabase OAuth Configuration
variable "domain_name" {
  description = "Domain name for the application"
  type        = string
  default     = "oddiya.com"
}

variable "supabase_url" {
  description = "Supabase project URL"
  type        = string
  sensitive   = true
}

variable "supabase_anon_key" {
  description = "Supabase anonymous key"
  type        = string
  sensitive   = true
}

variable "supabase_service_key" {
  description = "Supabase service role key"
  type        = string
  sensitive   = true
}

variable "supabase_jwt_secret" {
  description = "JWT secret for verifying Supabase tokens"
  type        = string
  sensitive   = true
}

variable "allowed_oauth_providers" {
  description = "List of allowed OAuth providers"
  type        = list(string)
  default     = ["google", "apple"]
}

variable "enable_oauth_session_management" {
  description = "Enable DynamoDB table for OAuth session management"
  type        = bool
  default     = true
}