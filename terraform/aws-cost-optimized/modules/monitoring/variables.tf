variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "budget_alert" {
  description = "Budget alert configuration"
  type = object({
    monthly_limit_usd = number
    alert_thresholds  = list(number)
    alert_emails      = list(string)
  })
}

variable "monitored_services" {
  description = "AWS services to monitor for costs"
  type        = list(string)
  default = [
    "Amazon Elastic Compute Cloud - Compute",
    "Amazon Simple Storage Service",
    "Amazon Relational Database Service",
    "Amazon DynamoDB",
    "AWS Lambda",
    "Amazon CloudWatch"
  ]
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "asg_name" {
  description = "Auto Scaling Group name for monitoring"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}