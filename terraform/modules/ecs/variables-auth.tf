# Additional variables for Auth Service with Supabase OAuth

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "ecr_repository_url" {
  description = "ECR repository URL"
  type        = string
  default     = ""
}

# Auth Service Configuration
variable "auth_service_cpu" {
  description = "CPU units for auth service"
  type        = number
  default     = 512
}

variable "auth_service_memory" {
  description = "Memory for auth service in MB"
  type        = number
  default     = 1024
}

variable "auth_service_version" {
  description = "Auth service Docker image version"
  type        = string
  default     = "latest"
}

variable "auth_service_desired_count" {
  description = "Desired count for auth service"
  type        = number
  default     = 2
}

variable "auth_service_min_count" {
  description = "Minimum count for auth service auto scaling"
  type        = number
  default     = 1
}

variable "auth_service_max_count" {
  description = "Maximum count for auth service auto scaling"
  type        = number
  default     = 4
}

# Supabase OAuth Configuration
variable "supabase_url" {
  description = "Supabase project URL"
  type        = string
  default     = ""
}

variable "oauth_redirect_url" {
  description = "OAuth redirect URL"
  type        = string
  default     = ""
}

variable "allowed_oauth_providers" {
  description = "Allowed OAuth providers"
  type        = list(string)
  default     = ["google", "apple"]
}

variable "session_duration" {
  description = "Session duration in seconds"
  type        = number
  default     = 3600
}

variable "refresh_token_ttl" {
  description = "Refresh token TTL in seconds"
  type        = number
  default     = 604800
}

variable "supabase_secret_arn" {
  description = "ARN of the Supabase secrets in Secrets Manager"
  type        = string
  default     = ""
}

variable "database_secret_arn" {
  description = "ARN of the database secrets in Secrets Manager"
  type        = string
  default     = ""
}

variable "oauth_sessions_table_name" {
  description = "DynamoDB table name for OAuth sessions"
  type        = string
  default     = ""
}

variable "oauth_target_group_arn" {
  description = "Target group ARN for OAuth service"
  type        = string
  default     = ""
}

variable "alb_listener_arn" {
  description = "ALB listener ARN"
  type        = string
  default     = ""
}

variable "service_discovery_namespace_id" {
  description = "Service discovery namespace ID"
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}