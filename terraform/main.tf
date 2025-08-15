# Oddiya AWS Infrastructure - Main Configuration
# Region: ap-northeast-2 (Seoul)

terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }

  # Configure backend for state management
  backend "s3" {
    bucket         = "oddiya-terraform-state"
    key            = "production/terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "oddiya-terraform-locks"
  }
}

# Configure AWS Provider
provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "Oddiya"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# Data sources for availability zones
data "aws_availability_zones" "available" {
  state = "available"
}

# Current AWS account ID
data "aws_caller_identity" "current" {}

# VPC Module
module "vpc" {
  source = "./modules/vpc"
  
  environment         = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = data.aws_availability_zones.available.names
  
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  data_subnet_cidrs    = var.data_subnet_cidrs
}

# Security Groups Module
module "security_groups" {
  source = "./modules/security"
  
  vpc_id      = module.vpc.vpc_id
  environment = var.environment
}

# RDS Aurora PostgreSQL Module
module "rds" {
  source = "./modules/rds"
  
  environment            = var.environment
  vpc_id                = module.vpc.vpc_id
  data_subnet_ids       = module.vpc.data_subnet_ids
  database_security_group_id = module.security_groups.rds_security_group_id
  
  db_name               = var.db_name
  db_username           = var.db_username
  db_instance_class     = var.db_instance_class
  backup_retention_period = var.backup_retention_period
}

# ElastiCache Redis Module
module "elasticache" {
  source = "./modules/elasticache"
  
  environment           = var.environment
  vpc_id               = module.vpc.vpc_id
  data_subnet_ids      = module.vpc.data_subnet_ids
  redis_security_group_id = module.security_groups.redis_security_group_id
  
  node_type            = var.redis_node_type
  num_cache_nodes      = var.redis_num_nodes
}

# S3 Buckets Module
module "s3" {
  source = "./modules/s3"
  
  environment = var.environment
  account_id  = data.aws_caller_identity.current.account_id
}

# ECS Cluster Module
module "ecs" {
  source = "./modules/ecs"
  
  environment          = var.environment
  vpc_id              = module.vpc.vpc_id
  private_subnet_ids  = module.vpc.private_subnet_ids
  ecs_security_group_id = module.security_groups.ecs_security_group_id
  
  # Auth service configuration
  supabase_url              = var.supabase_url
  oauth_redirect_url        = "https://${var.domain_name}/auth/callback"
  allowed_oauth_providers   = var.allowed_oauth_providers
  supabase_secret_arn       = module.supabase.supabase_config_secret_arn
  oauth_sessions_table_name = module.supabase.oauth_sessions_table != null ? module.supabase.oauth_sessions_table.name : ""
  oauth_target_group_arn    = module.alb.oauth_target_group_arn
}

# Application Load Balancer Module
module "alb" {
  source = "./modules/alb"
  
  environment         = var.environment
  vpc_id             = module.vpc.vpc_id
  public_subnet_ids  = module.vpc.public_subnet_ids
  alb_security_group_id = module.security_groups.alb_security_group_id
  certificate_arn    = var.certificate_arn
}

# Lambda Functions Module
module "lambda" {
  source = "./modules/lambda"
  
  environment            = var.environment
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  lambda_security_group_id = module.security_groups.lambda_security_group_id
  
  s3_input_bucket  = module.s3.media_input_bucket_name
  s3_output_bucket = module.s3.media_output_bucket_name
}

# DynamoDB Tables Module
module "dynamodb" {
  source = "./modules/dynamodb"
  
  environment = var.environment
}

# SQS Queues Module
module "sqs" {
  source = "./modules/sqs"
  
  environment = var.environment
  lambda_arn  = module.lambda.video_processor_arn
}

# Secrets Manager Module
module "secrets" {
  source = "./modules/secrets"
  
  environment = var.environment
}

# CloudWatch Monitoring Module
module "monitoring" {
  source = "./modules/monitoring"
  
  environment     = var.environment
  ecs_cluster_name = module.ecs.cluster_name
  rds_cluster_id  = module.rds.cluster_id
}

# WAF Module
module "waf" {
  source = "./modules/waf"
  
  environment = var.environment
  alb_arn    = module.alb.alb_arn
}

# CloudFront CDN Module
module "cloudfront" {
  source = "./modules/cloudfront"
  
  environment      = var.environment
  alb_dns_name    = module.alb.alb_dns_name
  s3_bucket_domain = module.s3.static_bucket_domain
  waf_acl_id      = module.waf.web_acl_id
}

# Supabase OAuth Module
module "supabase" {
  source = "./modules/supabase"
  
  project_name          = "oddiya"
  environment           = var.environment
  tags                  = var.common_tags
  
  # Supabase Configuration
  supabase_url          = var.supabase_url
  supabase_anon_key     = var.supabase_anon_key
  supabase_service_key  = var.supabase_service_key
  supabase_jwt_secret   = var.supabase_jwt_secret
  
  # OAuth Configuration
  oauth_redirect_url    = "https://${var.domain_name}/auth/callback"
  allowed_oauth_providers = var.allowed_oauth_providers
  
  # Security
  app_security_group_id = module.security_groups.ecs_security_group_id
  
  # Session Management
  enable_session_management = var.enable_oauth_session_management
}