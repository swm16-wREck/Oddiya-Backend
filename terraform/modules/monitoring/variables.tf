variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name"
  type        = string
}

variable "ecs_service_name" {
  description = "ECS service name"
  type        = string
}

variable "rds_cluster_id" {
  description = "RDS cluster identifier"
  type        = string
}

variable "alb_name" {
  description = "Application Load Balancer name"
  type        = string
}

variable "alert_emails" {
  description = "List of email addresses for CloudWatch alerts"
  type        = list(string)
  default     = []
}

variable "min_healthy_tasks" {
  description = "Minimum number of healthy ECS tasks"
  type        = number
  default     = 1
}
