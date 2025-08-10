variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "schedule" {
  description = "Start/stop schedule configuration"
  type = object({
    start_cron = string
    stop_cron  = string
    timezone   = string
  })
}

variable "target_resources" {
  description = "Resources to be scheduled"
  type = object({
    ec2_instances = list(string)
    rds_instances = list(string)
    asg_names     = list(string)
  })
  default = {
    ec2_instances = []
    rds_instances = []
    asg_names     = []
  }
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}