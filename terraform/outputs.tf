# Outputs for Oddiya AWS Infrastructure

# VPC Outputs
output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = module.vpc.vpc_cidr
}

# Subnet Outputs
output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.vpc.private_subnet_ids
}

output "data_subnet_ids" {
  description = "Data subnet IDs"
  value       = module.vpc.data_subnet_ids
}

# RDS Outputs
output "rds_cluster_endpoint" {
  description = "RDS cluster writer endpoint"
  value       = module.rds.cluster_endpoint
  sensitive   = true
}

output "rds_cluster_reader_endpoint" {
  description = "RDS cluster reader endpoint"
  value       = module.rds.cluster_reader_endpoint
  sensitive   = true
}

# ElastiCache Outputs
output "redis_endpoint" {
  description = "Redis cluster configuration endpoint"
  value       = module.elasticache.configuration_endpoint
  sensitive   = true
}

# S3 Outputs
output "s3_buckets" {
  description = "S3 bucket names"
  value = {
    static_assets = module.s3.static_bucket_name
    media_input   = module.s3.media_input_bucket_name
    media_output  = module.s3.media_output_bucket_name
    backups       = module.s3.backup_bucket_name
  }
}

# ECS Outputs
output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN"
  value       = module.ecs.cluster_arn
}

# ALB Outputs
output "alb_dns_name" {
  description = "ALB DNS name"
  value       = module.alb.alb_dns_name
}

output "alb_zone_id" {
  description = "ALB zone ID"
  value       = module.alb.alb_zone_id
}

# Lambda Outputs
output "lambda_function_names" {
  description = "Lambda function names"
  value = {
    video_processor = module.lambda.video_processor_name
  }
}

# DynamoDB Outputs
output "dynamodb_table_names" {
  description = "DynamoDB table names"
  value = {
    sessions = module.dynamodb.sessions_table_name
    queues   = module.dynamodb.queues_table_name
  }
}

# SQS Outputs
output "sqs_queue_urls" {
  description = "SQS queue URLs"
  value = {
    video_queue = module.sqs.video_queue_url
  }
}

# CloudFront Outputs
output "cloudfront_distribution_domain" {
  description = "CloudFront distribution domain name"
  value       = module.cloudfront.distribution_domain_name
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = module.cloudfront.distribution_id
}

# Security Group Outputs
output "security_group_ids" {
  description = "Security group IDs"
  value = {
    alb    = module.security_groups.alb_security_group_id
    ecs    = module.security_groups.ecs_security_group_id
    rds    = module.security_groups.rds_security_group_id
    redis  = module.security_groups.redis_security_group_id
    lambda = module.security_groups.lambda_security_group_id
  }
}

# Monitoring Outputs
output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard URL"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=oddiya-${var.environment}"
}