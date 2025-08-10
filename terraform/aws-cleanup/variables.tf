variable "aws_region" {
  description = "AWS region for cleanup operations"
  type        = string
  default     = "ap-northeast-2"
}

variable "prefix" {
  description = "Prefix for resource names"
  type        = string
  default     = "aws-cleanup"
}

variable "dry_run" {
  description = "If true, only identify resources without deleting them"
  type        = bool
  default     = true
}

variable "schedule_expression" {
  description = "CloudWatch Events schedule expression for cleanup runs"
  type        = string
  default     = "rate(1 day)"  # Daily scan
  
  validation {
    condition = can(regex("^(rate|cron)\\(.+\\)$", var.schedule_expression))
    error_message = "Schedule expression must be a valid rate() or cron() expression."
  }
}

variable "resource_age_threshold_days" {
  description = "Minimum age in days before a resource is considered for cleanup"
  type        = number
  default     = 30
  
  validation {
    condition     = var.resource_age_threshold_days >= 7
    error_message = "Age threshold must be at least 7 days."
  }
}

variable "notification_emails" {
  description = "Email addresses to receive cleanup notifications"
  type        = list(string)
  default     = []
}

variable "cleanup_config" {
  description = "Configuration for which resource types to clean up"
  type = object({
    ebs_volumes          = bool
    ebs_snapshots        = bool
    elastic_ips          = bool
    load_balancers       = bool
    nat_gateways         = bool
    rds_snapshots        = bool
    ami_images           = bool
    ecr_images           = bool
    cloudwatch_logs      = bool
    s3_buckets           = bool
    security_groups      = bool
    old_lambda_versions  = bool
  })
  default = {
    ebs_volumes          = true
    ebs_snapshots        = true
    elastic_ips          = true
    load_balancers       = true
    nat_gateways         = true
    rds_snapshots        = true
    ami_images           = true
    ecr_images           = true
    cloudwatch_logs      = true
    s3_buckets           = true
    security_groups      = true
    old_lambda_versions  = true
  }
}

variable "whitelist_tags" {
  description = "Tag keys that protect resources from deletion"
  type        = list(string)
  default     = ["Environment:prod", "Protected", "DoNotDelete", "Critical"]
}

variable "whitelist_name_prefixes" {
  description = "Name prefixes that protect resources from deletion"
  type        = list(string)
  default     = ["prod-", "production-", "critical-", "shared-"]
}