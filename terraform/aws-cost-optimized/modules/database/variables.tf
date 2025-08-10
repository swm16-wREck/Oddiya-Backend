variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for resources"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for database"
  type        = list(string)
}

variable "rds_optimization" {
  description = "RDS optimization settings"
  type = object({
    use_aurora_serverless = bool
    use_graviton          = bool
    enable_auto_pause     = bool
    backup_retention_days = number
  })
}

variable "is_production" {
  description = "Whether this is a production environment"
  type        = bool
  default     = false
}

variable "database_engine" {
  description = "Database engine (mysql or postgres)"
  type        = string
  default     = "postgres"
  
  validation {
    condition     = contains(["mysql", "postgres"], var.database_engine)
    error_message = "Database engine must be mysql or postgres."
  }
}

variable "enable_read_replica" {
  description = "Enable read replica for production"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}