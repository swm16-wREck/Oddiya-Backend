# Core Variables
variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "ap-northeast-2"  # Seoul region
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "cost_center" {
  description = "Cost center for billing tags"
  type        = string
  default     = "engineering"
}

# Cost Optimization Controls
variable "use_spot_instances" {
  description = "Use EC2 Spot instances for compute (70-90% savings)"
  type        = bool
  default     = true
}

variable "spot_max_price" {
  description = "Maximum price for spot instances (empty = on-demand price)"
  type        = string
  default     = ""
}

variable "use_graviton" {
  description = "Use ARM-based Graviton instances (20% cheaper)"
  type        = bool
  default     = true
}

variable "use_burstable_instances" {
  description = "Use t4g/t3 burstable instances for variable workloads"
  type        = bool
  default     = true
}

variable "enable_auto_stop" {
  description = "Enable automatic stop/start scheduling for non-prod"
  type        = bool
  default     = true
}

variable "auto_stop_enabled" {
  description = "Tag value for auto-stop functionality"
  type        = string
  default     = "true"
}

# Compute Configuration
variable "instance_types" {
  description = "Preferred instance types (cheapest first)"
  type        = map(list(string))
  default = {
    micro = ["t4g.micro", "t3.micro", "t3a.micro"]
    small = ["t4g.small", "t3.small", "t3a.small"]
    medium = ["t4g.medium", "t3.medium", "t3a.medium", "m6a.medium"]
    large = ["t4g.large", "t3.large", "m6a.large", "m5a.large"]
    xlarge = ["t4g.xlarge", "t3.xlarge", "m6a.xlarge", "m5a.xlarge"]
  }
}

# Storage Configuration
variable "ebs_optimization" {
  description = "EBS volume optimization settings"
  type = object({
    use_gp3           = bool
    enable_encryption = bool
    snapshot_retention_days = number
  })
  default = {
    use_gp3           = true  # 20% cheaper than gp2
    enable_encryption = true
    snapshot_retention_days = 7
  }
}

variable "s3_lifecycle_rules" {
  description = "S3 lifecycle rules for cost optimization"
  type = object({
    enable_intelligent_tiering = bool
    transition_to_ia_days      = number
    transition_to_glacier_days = number
    expire_days                = number
  })
  default = {
    enable_intelligent_tiering = true
    transition_to_ia_days      = 30
    transition_to_glacier_days = 90
    expire_days                = 365
  }
}

# Database Configuration
variable "rds_optimization" {
  description = "RDS optimization settings"
  type = object({
    use_aurora_serverless = bool
    use_graviton          = bool
    enable_auto_pause     = bool
    backup_retention_days = number
  })
  default = {
    use_aurora_serverless = true  # Pay per request
    use_graviton          = true  # 20% cheaper
    enable_auto_pause     = true
    backup_retention_days = 7
  }
}

# Networking Configuration
variable "use_single_nat_gateway" {
  description = "Use single NAT gateway for all AZs (saves ~$90/month per gateway)"
  type        = bool
  default     = true
}

variable "enable_vpc_endpoints" {
  description = "Enable VPC endpoints to reduce data transfer costs"
  type        = bool
  default     = true
}

# Auto-scaling Configuration
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
  default = {
    min_size                 = 1
    max_size                 = 10
    desired_capacity         = 2
    target_cpu_utilization   = 70
    scale_in_cooldown        = 300
    scale_out_cooldown       = 60
    predictive_scaling       = true
  }
}

# Scheduling Configuration
variable "schedule" {
  description = "Start/stop schedule for non-production resources"
  type = object({
    start_cron = string
    stop_cron  = string
    timezone   = string
  })
  default = {
    start_cron = "0 8 * * MON-FRI"  # 8 AM weekdays
    stop_cron  = "0 20 * * *"       # 8 PM daily
    timezone   = "Asia/Seoul"       # Seoul timezone
  }
}

# Cost Alerting
variable "budget_alert" {
  description = "Budget alert configuration"
  type = object({
    monthly_limit_usd = number
    alert_thresholds  = list(number)
    alert_emails      = list(string)
  })
  default = {
    monthly_limit_usd = 1000
    alert_thresholds  = [50, 80, 100, 120]
    alert_emails      = []
  }
}