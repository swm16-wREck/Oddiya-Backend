# Outputs for EC2 PostgreSQL Infrastructure

# ==========================================
# Network Information
# ==========================================

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "vpc_cidr_block" {
  description = "VPC CIDR block"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = aws_subnet.private[*].id
}

output "internet_gateway_id" {
  description = "Internet Gateway ID"
  value       = aws_internet_gateway.main.id
}

output "nat_gateway_id" {
  description = "NAT Gateway ID (if enabled)"
  value       = var.enable_nat_gateway ? aws_nat_gateway.main[0].id : null
}

# ==========================================
# Security Group Information
# ==========================================

output "app_security_group_id" {
  description = "Application security group ID"
  value       = aws_security_group.app.id
}

output "database_security_group_id" {
  description = "Database security group ID"
  value       = aws_security_group.database.id
}

output "monitoring_security_group_id" {
  description = "Monitoring security group ID"
  value       = aws_security_group.monitoring.id
}

output "bastion_security_group_id" {
  description = "Bastion security group ID (if enabled)"
  value       = var.enable_bastion ? aws_security_group.bastion[0].id : null
}

# ==========================================
# Database Instance Information
# ==========================================

output "database_instance_id" {
  description = "PostgreSQL EC2 instance ID"
  value       = aws_instance.postgresql.id
}

output "database_private_ip" {
  description = "PostgreSQL EC2 instance private IP"
  value       = aws_instance.postgresql.private_ip
}

output "database_public_ip" {
  description = "PostgreSQL EC2 instance public IP (if EIP enabled)"
  value       = var.enable_db_eip ? aws_eip.db_eip[0].public_ip : null
}

output "database_availability_zone" {
  description = "PostgreSQL instance availability zone"
  value       = aws_instance.postgresql.availability_zone
}

# ==========================================
# Database Connection Information
# ==========================================

output "database_endpoint" {
  description = "PostgreSQL database endpoint"
  value       = aws_instance.postgresql.private_ip
}

output "database_port" {
  description = "PostgreSQL database port"
  value       = "5432"
}

output "database_name" {
  description = "PostgreSQL database name"
  value       = var.db_name
}

output "database_username" {
  description = "PostgreSQL database username"
  value       = var.db_username
  sensitive   = true
}

# ==========================================
# AWS Secrets Manager
# ==========================================

output "db_credentials_secret_arn" {
  description = "ARN of the database credentials secret"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "db_credentials_secret_name" {
  description = "Name of the database credentials secret"
  value       = aws_secretsmanager_secret.db_credentials.name
}

# ==========================================
# S3 Backup Information
# ==========================================

output "backup_s3_bucket_name" {
  description = "S3 bucket name for database backups"
  value       = aws_s3_bucket.db_backups.bucket
}

output "backup_s3_bucket_arn" {
  description = "S3 bucket ARN for database backups"
  value       = aws_s3_bucket.db_backups.arn
}

# ==========================================
# IAM Information
# ==========================================

output "db_instance_profile_name" {
  description = "IAM instance profile name for database server"
  value       = aws_iam_instance_profile.db_instance_profile.name
}

output "db_instance_role_name" {
  description = "IAM role name for database server"
  value       = aws_iam_role.db_instance_role.name
}

# ==========================================
# Monitoring Information
# ==========================================

output "monitoring_instance_id" {
  description = "Monitoring server instance ID (if enabled)"
  value       = var.enable_monitoring ? aws_instance.monitoring[0].id : null
}

output "monitoring_private_ip" {
  description = "Monitoring server private IP (if enabled)"
  value       = var.enable_monitoring ? aws_instance.monitoring[0].private_ip : null
}

# ==========================================
# Connection Strings and URLs
# ==========================================

output "postgresql_connection_string" {
  description = "PostgreSQL connection string (without password)"
  value       = "postgresql://${var.db_username}@${aws_instance.postgresql.private_ip}:5432/${var.db_name}?sslmode=require"
  sensitive   = true
}

output "jdbc_url" {
  description = "JDBC URL for PostgreSQL connection"
  value       = "jdbc:postgresql://${aws_instance.postgresql.private_ip}:5432/${var.db_name}?sslmode=require"
}

# ==========================================
# Application Configuration
# ==========================================

output "application_config" {
  description = "Application configuration values"
  value = {
    spring_datasource_url      = "jdbc:postgresql://${aws_instance.postgresql.private_ip}:5432/${var.db_name}?sslmode=require"
    spring_datasource_username = var.db_username
    database_host              = aws_instance.postgresql.private_ip
    database_port              = "5432"
    database_name              = var.db_name
    backup_bucket              = aws_s3_bucket.db_backups.bucket
    aws_region                 = var.aws_region
  }
}

# ==========================================
# Cost and Resource Summary
# ==========================================

output "resource_summary" {
  description = "Summary of created resources for cost tracking"
  value = {
    vpc_enabled             = true
    ec2_instances          = 1 + (var.enable_monitoring ? 1 : 0) + (var.enable_bastion ? 1 : 0)
    ebs_volumes            = 2  # Root + Data volume per DB instance
    nat_gateway            = var.enable_nat_gateway
    elastic_ips            = (var.enable_nat_gateway ? 1 : 0) + (var.enable_db_eip ? 1 : 0)
    s3_buckets             = 1
    secrets_manager_secrets = 1
    estimated_monthly_cost = "~$50-150 (depending on instance types and data transfer)"
  }
}

# ==========================================
# Next Steps and Documentation
# ==========================================

output "setup_instructions" {
  description = "Next steps for database setup"
  value = {
    1 = "SSH to the database instance using the private key"
    2 = "Run the setup script: /opt/postgresql/setup-ec2-postgresql.sh"
    3 = "Verify PostgreSQL is running: sudo systemctl status postgresql"
    4 = "Test connection: psql -h localhost -U ${var.db_username} -d ${var.db_name}"
    5 = "Configure your application with the JDBC URL from outputs"
    6 = "Set up monitoring dashboards if monitoring is enabled"
    7 = "Configure backup verification and restore procedures"
  }
}