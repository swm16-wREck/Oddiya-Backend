variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "data_subnet_ids" {
  description = "Data subnet IDs for RDS"
  type        = list(string)
}

variable "database_security_group_id" {
  description = "Security group ID for database"
  type        = string
}

variable "db_name" {
  description = "Database name"
  type        = string
}

variable "db_username" {
  description = "Database username"
  type        = string
}

variable "db_instance_class" {
  description = "Instance class for RDS"
  type = object({
    writer = string
    reader = string
  })
}

variable "backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 7
}