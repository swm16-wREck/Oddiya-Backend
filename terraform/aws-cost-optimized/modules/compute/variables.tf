variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for resources"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for instances"
  type        = list(string)
}

variable "use_spot_instances" {
  description = "Use spot instances"
  type        = bool
  default     = true
}

variable "spot_max_price" {
  description = "Maximum spot price"
  type        = string
  default     = ""
}

variable "use_graviton" {
  description = "Use ARM-based Graviton instances"
  type        = bool
  default     = true
}

variable "use_burstable_instances" {
  description = "Use burstable instance types"
  type        = bool
  default     = true
}

variable "instance_types" {
  description = "Instance type options"
  type        = map(list(string))
}

variable "auto_scaling" {
  description = "Auto-scaling configuration"
  type = object({
    min_size                 = number
    max_size                 = number
    desired_capacity         = number
    target_cpu_utilization   = number
    scale_in_cooldown        = number
    scale_out_cooldown       = number
    predictive_scaling       = bool
  })
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}