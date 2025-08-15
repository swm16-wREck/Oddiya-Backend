# Outputs for Phase 3 Redis Infrastructure
# Agent 1 - EC2 Infrastructure Specialist

# Network Load Balancer Endpoints
output "redis_master_endpoint" {
  description = "Redis master endpoint for applications"
  value       = "${aws_lb.redis_nlb.dns_name}:6379"
}

output "redis_slave_endpoint" {
  description = "Redis slave endpoint for read operations"
  value       = "${aws_lb.redis_nlb.dns_name}:6380"
}

output "redis_sentinel_endpoint" {
  description = "Redis Sentinel endpoint for high availability"
  value       = "${aws_lb.redis_nlb.dns_name}:26379"
}

# Network Load Balancer Details
output "redis_nlb_dns_name" {
  description = "DNS name of the Redis Network Load Balancer"
  value       = aws_lb.redis_nlb.dns_name
}

output "redis_nlb_zone_id" {
  description = "Zone ID of the Redis Network Load Balancer"
  value       = aws_lb.redis_nlb.zone_id
}

output "redis_nlb_arn" {
  description = "ARN of the Redis Network Load Balancer"
  value       = aws_lb.redis_nlb.arn
}

# VPC and Network Information
output "redis_vpc_id" {
  description = "ID of the Redis VPC"
  value       = aws_vpc.redis_vpc.id
}

output "redis_vpc_cidr_block" {
  description = "CIDR block of the Redis VPC"
  value       = aws_vpc.redis_vpc.cidr_block
}

output "redis_private_subnet_ids" {
  description = "IDs of the Redis private subnets"
  value       = aws_subnet.redis_private_subnets[*].id
}

output "redis_public_subnet_id" {
  description = "ID of the Redis public subnet"
  value       = aws_subnet.redis_public_subnet.id
}

# Security Group Information
output "redis_security_group_id" {
  description = "ID of the Redis security group"
  value       = aws_security_group.redis_sg.id
}

output "redis_nlb_security_group_id" {
  description = "ID of the Redis NLB security group"
  value       = aws_security_group.redis_nlb_sg.id
}

# Auto Scaling Group Information
output "redis_master_asg_name" {
  description = "Name of the Redis master Auto Scaling Group"
  value       = aws_autoscaling_group.redis_master_asg.name
}

output "redis_master_asg_arn" {
  description = "ARN of the Redis master Auto Scaling Group"
  value       = aws_autoscaling_group.redis_master_asg.arn
}

output "redis_slave_asg_names" {
  description = "Names of the Redis slave Auto Scaling Groups"
  value       = aws_autoscaling_group.redis_slave_asg[*].name
}

output "redis_slave_asg_arns" {
  description = "ARNs of the Redis slave Auto Scaling Groups"
  value       = aws_autoscaling_group.redis_slave_asg[*].arn
}

# Launch Template Information
output "redis_launch_template_id" {
  description = "ID of the Redis launch template"
  value       = aws_launch_template.redis_template.id
}

output "redis_launch_template_latest_version" {
  description = "Latest version of the Redis launch template"
  value       = aws_launch_template.redis_template.latest_version
}

# Target Group Information
output "redis_target_group_arn" {
  description = "ARN of the Redis master target group"
  value       = aws_lb_target_group.redis_tg.arn
}

output "redis_slave_target_group_arn" {
  description = "ARN of the Redis slave target group"
  value       = aws_lb_target_group.redis_slave_tg.arn
}

output "redis_sentinel_target_group_arn" {
  description = "ARN of the Redis Sentinel target group"
  value       = aws_lb_target_group.redis_sentinel_tg.arn
}

# IAM Information
output "redis_iam_role_arn" {
  description = "ARN of the Redis IAM role"
  value       = aws_iam_role.redis_role.arn
}

output "redis_iam_instance_profile_name" {
  description = "Name of the Redis IAM instance profile"
  value       = aws_iam_instance_profile.redis_profile.name
}

# S3 Backup Bucket
output "redis_backup_bucket_name" {
  description = "Name of the S3 bucket for Redis backups"
  value       = aws_s3_bucket.redis_backups.bucket
}

output "redis_backup_bucket_arn" {
  description = "ARN of the S3 bucket for Redis backups"
  value       = aws_s3_bucket.redis_backups.arn
}

output "redis_backup_bucket_domain_name" {
  description = "Domain name of the S3 bucket for Redis backups"
  value       = aws_s3_bucket.redis_backups.bucket_domain_name
}

# CloudWatch Log Group
output "redis_log_group_name" {
  description = "Name of the CloudWatch log group for Redis"
  value       = aws_cloudwatch_log_group.redis_logs.name
}

output "redis_log_group_arn" {
  description = "ARN of the CloudWatch log group for Redis"
  value       = aws_cloudwatch_log_group.redis_logs.arn
}

# Key Pair Information
output "redis_key_pair_name" {
  description = "Name of the Redis key pair"
  value       = aws_key_pair.redis_key.key_name
}

output "redis_key_pair_fingerprint" {
  description = "Fingerprint of the Redis key pair"
  value       = aws_key_pair.redis_key.fingerprint
}

# Configuration Values (for application configuration)
output "redis_configuration" {
  description = "Redis configuration values for application setup"
  value = {
    master_endpoint   = "${aws_lb.redis_nlb.dns_name}:6379"
    slave_endpoint    = "${aws_lb.redis_nlb.dns_name}:6380"
    sentinel_endpoint = "${aws_lb.redis_nlb.dns_name}:26379"
    password_required = var.redis_password != ""
    tls_enabled      = var.redis_tls_enabled
    max_connections  = 1000  # Estimated based on instance type
    timeout_seconds  = 30
    pool_size       = 10
  }
  sensitive = false
}

# Spring Boot Configuration Template
output "spring_boot_redis_config" {
  description = "Spring Boot Redis configuration template"
  value = {
    spring = {
      redis = {
        host     = aws_lb.redis_nlb.dns_name
        port     = 6379
        password = var.redis_password != "" ? "***" : null
        timeout  = "2000ms"
        lettuce = {
          pool = {
            max_active = 8
            max_idle   = 8
            min_idle   = 0
          }
        }
        sentinel = var.sentinel_enabled ? {
          master    = "redis-master"
          nodes     = ["${aws_lb.redis_nlb.dns_name}:26379"]
          password  = var.redis_password != "" ? "***" : null
        } : null
      }
    }
  }
  sensitive = true
}

# Monitoring Endpoints
output "monitoring_endpoints" {
  description = "Endpoints for monitoring Redis infrastructure"
  value = {
    cloudwatch_logs = "/aws/ec2/redis/${var.environment}"
    metrics_namespace = "Oddiya/Redis/${title(var.environment)}"
    s3_backup_bucket = aws_s3_bucket.redis_backups.bucket
  }
}

# Cost Information
output "estimated_monthly_cost" {
  description = "Estimated monthly cost breakdown (USD)"
  value = {
    ec2_instances = {
      on_demand_t3_medium = "~$30/instance/month"
      spot_t3_medium     = "~$10-15/instance/month"
      estimated_total    = "~$45-90/month (3 nodes)"
    }
    storage = {
      ebs_gp3_50gb = "~$6/volume/month"
      total_storage = "~$18/month (3 volumes)"
    }
    network = {
      nlb = "~$16/month"
      data_transfer = "~$5-20/month (estimated)"
    }
    other = {
      s3_backups = "~$1-5/month"
      cloudwatch = "~$2-10/month"
    }
    total_estimated = "~$87-149/month"
    comparison_elasticache = "~$150-200/month (40-50% savings)"
  }
}

# Health Check URLs
output "health_check_commands" {
  description = "Commands to verify Redis cluster health"
  value = {
    redis_ping = "redis-cli -h ${aws_lb.redis_nlb.dns_name} -p 6379 ping"
    sentinel_masters = "redis-cli -h ${aws_lb.redis_nlb.dns_name} -p 26379 sentinel masters"
    cluster_info = "redis-cli -h ${aws_lb.redis_nlb.dns_name} -p 6379 info replication"
    backup_test = "aws s3 ls s3://${aws_s3_bucket.redis_backups.bucket}/"
  }
  sensitive = false
}

# VPC Peering Information (for connecting to application VPC)
output "vpc_peering_info" {
  description = "Information needed for VPC peering with application VPC"
  value = {
    redis_vpc_id = aws_vpc.redis_vpc.id
    redis_vpc_cidr = aws_vpc.redis_vpc.cidr_block
    redis_route_table_ids = [aws_route_table.redis_private_rt.id]
    required_security_group_rules = {
      allow_from_app_vpc = "Allow TCP 6379,6380,26379 from application VPC CIDR"
      allow_ssh_access = "Allow TCP 22 from management network"
    }
  }
}

# Disaster Recovery Information
output "disaster_recovery_info" {
  description = "Disaster recovery and backup information"
  value = {
    backup_bucket = aws_s3_bucket.redis_backups.bucket
    backup_schedule = var.backup_schedule
    retention_days = var.backup_retention_days
    recovery_procedure = "Use scripts/redis/redis-restore.sh with backup from S3"
    rpo_target = "24 hours"
    rto_target = "30 minutes"
  }
}