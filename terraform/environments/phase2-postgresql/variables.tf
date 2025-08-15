# Variables for Phase 2 PostgreSQL Infrastructure

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# Database Variables
variable "db_name" {
  description = "Name of the database"
  type        = string
  default     = "oddiya"
}

variable "db_username" {
  description = "Master username for the database"
  type        = string
  default     = "oddiya_admin"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "db_instance_count" {
  description = "Number of RDS instances in the cluster"
  type        = number
  default     = 2
}

variable "backup_retention_period" {
  description = "Number of days to retain automated backups"
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Enable deletion protection for RDS cluster"
  type        = bool
  default     = true
}

# Application Variables
variable "app_port" {
  description = "Port on which the application runs"
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Health check path for the application"
  type        = string
  default     = "/actuator/health"
}