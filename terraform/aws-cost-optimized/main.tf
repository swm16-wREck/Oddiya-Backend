# Main orchestration module for cost-optimized AWS infrastructure

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  
  # Cost optimization flags based on environment
  is_production = var.environment == "prod"
  enable_spot   = !local.is_production && var.use_spot_instances
  enable_scheduling = !local.is_production && var.enable_auto_stop
  
  # Region configuration
  selected_region = var.aws_region  # Using ap-northeast-2 (Seoul)
  
  # Common tags for cost tracking
  common_tags = {
    Environment     = var.environment
    Project         = var.project_name
    CostCenter      = var.cost_center
    ManagedBy       = "Terraform"
    CostOptimized   = "true"
    LastUpdated     = timestamp()
  }
}

# Data sources for current pricing
data "aws_ec2_spot_price" "current" {
  count = local.enable_spot ? length(var.instance_types.medium) : 0
  
  instance_type     = var.instance_types.medium[count.index]
  availability_zone = data.aws_availability_zones.available.names[0]
  
  filter {
    name   = "product-description"
    values = ["Linux/UNIX"]
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# Cost-optimized VPC Module
module "vpc" {
  source = "./modules/networking"
  
  name_prefix             = local.name_prefix
  use_single_nat_gateway  = var.use_single_nat_gateway
  enable_vpc_endpoints    = var.enable_vpc_endpoints
  availability_zones      = data.aws_availability_zones.available.names
  
  tags = local.common_tags
}

# Cost-optimized Compute Module
module "compute" {
  source = "./modules/compute"
  
  name_prefix             = local.name_prefix
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  
  use_spot_instances      = local.enable_spot
  spot_max_price          = var.spot_max_price
  use_graviton            = var.use_graviton
  use_burstable_instances = var.use_burstable_instances
  instance_types          = var.instance_types
  
  auto_scaling            = var.auto_scaling
  
  tags = local.common_tags
}

# Cost-optimized Storage Module
module "storage" {
  source = "./modules/storage"
  
  name_prefix         = local.name_prefix
  ebs_optimization    = var.ebs_optimization
  s3_lifecycle_rules  = var.s3_lifecycle_rules
  
  tags = local.common_tags
}

# Cost-optimized Database Module
module "database" {
  source = "./modules/database"
  
  name_prefix         = local.name_prefix
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.database_subnet_ids
  
  rds_optimization    = var.rds_optimization
  is_production       = local.is_production
  
  tags = local.common_tags
}

# Scheduling Module for Non-Production
module "scheduling" {
  count  = local.enable_scheduling ? 1 : 0
  source = "./modules/scheduling"
  
  name_prefix = local.name_prefix
  schedule    = var.schedule
  
  target_resources = {
    ec2_instances = module.compute.instance_ids
    rds_instances = module.database.instance_identifiers
    asg_names     = [module.compute.autoscaling_group_name]
  }
  
  tags = local.common_tags
}

# Cost Monitoring and Alerting
module "cost_monitoring" {
  source = "./modules/monitoring"
  
  name_prefix   = local.name_prefix
  budget_alert  = var.budget_alert
  
  monitored_services = [
    "EC2",
    "RDS",
    "S3",
    "EBS",
    "CloudWatch",
    "Lambda"
  ]
  
  tags = local.common_tags
}

# Savings Plans and Reserved Instances Recommendations
resource "aws_ce_anomaly_monitor" "cost_anomaly" {
  name              = "${local.name_prefix}-anomaly-monitor"
  monitor_type      = "CUSTOM"
  monitor_frequency = "DAILY"
  
  monitor_specification = jsonencode({
    Dimensions = {
      Key    = "LINKED_ACCOUNT"
      Values = [data.aws_caller_identity.current.account_id]
    }
  })
}

resource "aws_ce_anomaly_subscription" "cost_alerts" {
  name      = "${local.name_prefix}-anomaly-alerts"
  frequency = "IMMEDIATE"
  
  monitor_arn_list = [
    aws_ce_anomaly_monitor.cost_anomaly.arn
  ]
  
  subscriber {
    type    = "EMAIL"
    address = var.budget_alert.alert_emails[0]
  }
  
  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      values        = ["100"]
      match_options = ["GREATER_THAN_OR_EQUAL"]
    }
  }
}

data "aws_caller_identity" "current" {}