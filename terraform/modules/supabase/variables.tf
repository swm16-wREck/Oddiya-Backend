# Variables for Supabase OAuth Module

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {}
}

# Supabase Configuration
variable "supabase_url" {
  description = "Supabase project URL"
  type        = string
  sensitive   = true
}

variable "supabase_anon_key" {
  description = "Supabase anonymous key for public access"
  type        = string
  sensitive   = true
}

variable "supabase_service_key" {
  description = "Supabase service role key for admin access"
  type        = string
  sensitive   = true
}

variable "supabase_jwt_secret" {
  description = "JWT secret for verifying Supabase tokens"
  type        = string
  sensitive   = true
}

# OAuth Configuration
variable "oauth_redirect_url" {
  description = "OAuth redirect URL after authentication"
  type        = string
}

variable "allowed_oauth_providers" {
  description = "List of allowed OAuth providers (google, apple, etc.)"
  type        = list(string)
  default     = ["google", "apple"]
}

variable "session_duration" {
  description = "Session duration in seconds"
  type        = number
  default     = 3600 # 1 hour
}

variable "refresh_token_ttl" {
  description = "Refresh token TTL in seconds"
  type        = number
  default     = 604800 # 7 days
}

variable "enable_mfa" {
  description = "Enable multi-factor authentication"
  type        = bool
  default     = false
}

# Security Group
variable "app_security_group_id" {
  description = "Security group ID for the application"
  type        = string
}

# Logging and Monitoring
variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

variable "oauth_failure_threshold" {
  description = "Threshold for OAuth failure alarm"
  type        = number
  default     = 10
}

# Secrets Management
variable "secret_recovery_days" {
  description = "Number of days to retain a secret after deletion"
  type        = number
  default     = 7
}

# Session Management
variable "enable_session_management" {
  description = "Enable DynamoDB table for session management"
  type        = bool
  default     = true
}