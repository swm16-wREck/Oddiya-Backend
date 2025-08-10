variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "ebs_optimization" {
  description = "EBS optimization settings"
  type = object({
    use_gp3                 = bool
    enable_encryption       = bool
    snapshot_retention_days = number
  })
}

variable "s3_lifecycle_rules" {
  description = "S3 lifecycle rules for cost optimization"
  type = object({
    enable_intelligent_tiering = bool
    transition_to_ia_days      = number
    transition_to_glacier_days = number
    expire_days                = number
  })
}

variable "enable_efs" {
  description = "Enable EFS for shared storage"
  type        = bool
  default     = false
}

variable "efs_single_az" {
  description = "Use EFS One Zone storage (47% cheaper)"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}