output "cluster_endpoint" {
  description = "Aurora cluster endpoint"
  value       = var.rds_optimization.use_aurora_serverless ? aws_rds_cluster.serverless[0].endpoint : null
}

output "cluster_reader_endpoint" {
  description = "Aurora cluster reader endpoint"
  value       = var.rds_optimization.use_aurora_serverless ? aws_rds_cluster.serverless[0].reader_endpoint : null
}

output "instance_endpoint" {
  description = "RDS instance endpoint"
  value       = !var.rds_optimization.use_aurora_serverless ? aws_db_instance.standard[0].endpoint : null
}

output "instance_identifiers" {
  description = "Database instance identifiers"
  value = var.rds_optimization.use_aurora_serverless ? 
    [aws_rds_cluster_instance.serverless[0].identifier] : 
    [aws_db_instance.standard[0].identifier]
}

output "database_name" {
  description = "Name of the database"
  value       = replace(var.name_prefix, "-", "_")
}

output "database_port" {
  description = "Database port"
  value       = var.database_engine == "mysql" ? 3306 : 5432
}

output "password_ssm_parameter" {
  description = "SSM parameter containing database password"
  value       = aws_ssm_parameter.db_password.name
}

output "security_group_id" {
  description = "Database security group ID"
  value       = aws_security_group.database.id
}

output "cost_savings" {
  description = "Database cost optimization features enabled"
  value = {
    aurora_serverless = var.rds_optimization.use_aurora_serverless
    graviton_instances = var.rds_optimization.use_graviton
    auto_pause_enabled = var.rds_optimization.enable_auto_pause && !var.is_production
    minimal_backups = var.rds_optimization.backup_retention_days <= 7
  }
}

output "estimated_monthly_cost" {
  description = "Estimated monthly database cost"
  value = var.rds_optimization.use_aurora_serverless ? 
    "~$43-200/month (scales with usage)" : 
    "~$25-50/month (t4g.medium)"
}