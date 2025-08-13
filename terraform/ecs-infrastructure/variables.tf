# Variables for ECS Infrastructure

variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "oddiya"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2b", "ap-northeast-2c"]
}

variable "task_cpu" {
  description = "CPU units for the task (256, 512, 1024, etc.)"
  type        = string
  default     = "1024"  # Increased from 256 for Spring Boot performance
}

variable "task_memory" {
  description = "Memory for the task in MiB (512, 1024, 2048, etc.)"
  type        = string
  default     = "2048"  # Increased from 512 for Spring Boot + JPA/Hibernate
}

variable "desired_count" {
  description = "Desired number of tasks"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Minimum number of tasks for auto scaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks for auto scaling"
  type        = number
  default     = 3
}

variable "enable_logging" {
  description = "Enable CloudWatch logging"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

# Health Check Configuration Variables
variable "health_check_timeout" {
  description = "Health check timeout in seconds"
  type        = number
  default     = 15
}

variable "health_check_interval" {
  description = "Health check interval in seconds"
  type        = number
  default     = 60
}

variable "health_check_unhealthy_threshold" {
  description = "Number of consecutive health check failures before marking unhealthy"
  type        = number
  default     = 5
}

variable "container_health_check_start_period" {
  description = "Grace period for container startup in seconds"
  type        = number
  default     = 180
}

variable "container_health_check_retries" {
  description = "Number of retries for container health check"
  type        = number
  default     = 5
}