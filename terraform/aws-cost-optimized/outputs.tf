output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "cost_savings_summary" {
  description = "Summary of all cost optimization features enabled"
  value = {
    spot_instances_enabled        = var.use_spot_instances
    graviton_instances_enabled    = var.use_graviton
    single_nat_gateway            = var.use_single_nat_gateway
    aurora_serverless_enabled     = var.rds_optimization.use_aurora_serverless
    s3_intelligent_tiering        = var.s3_lifecycle_rules.enable_intelligent_tiering
    auto_stop_scheduling          = var.enable_auto_stop && var.environment != "prod"
    
    estimated_monthly_savings = {
      spot_instances     = var.use_spot_instances ? "70-90% on compute costs" : "0%"
      graviton           = var.use_graviton ? "20% on instance costs" : "0%"
      single_nat         = var.use_single_nat_gateway ? "$90+ per avoided gateway" : "0"
      aurora_serverless  = var.rds_optimization.use_aurora_serverless ? "Pay-per-request pricing" : "Fixed instance costs"
      s3_lifecycle       = "60-90% on aged data"
      scheduling         = var.enable_auto_stop && var.environment != "prod" ? "60-75% on non-prod resources" : "0%"
    }
  }
}

output "monitoring_dashboard_url" {
  description = "CloudWatch dashboard URL for cost monitoring"
  value       = module.cost_monitoring.dashboard_url
}

output "monthly_budget_limit" {
  description = "Monthly budget limit in USD"
  value       = var.budget_alert.monthly_limit_usd
}

output "autoscaling_group_name" {
  description = "Name of the Auto Scaling Group"
  value       = module.compute.autoscaling_group_name
}

output "database_endpoint" {
  description = "Database endpoint"
  value = var.rds_optimization.use_aurora_serverless ? 
    module.database.cluster_endpoint : 
    module.database.instance_endpoint
}

output "s3_bucket_names" {
  description = "S3 bucket names"
  value = {
    main = module.storage.main_bucket_id
    logs = module.storage.logs_bucket_id
  }
}

output "region" {
  description = "AWS region for deployment"
  value       = var.aws_region
}

output "environment" {
  description = "Environment name"
  value       = var.environment
}