#!/bin/bash

# Script to create remaining Terraform modules

# ElastiCache Module
cat > terraform/modules/elasticache/main.tf << 'EOF'
resource "aws_elasticache_subnet_group" "main" {
  name       = "oddiya-${var.environment}-redis-subnet"
  subnet_ids = var.data_subnet_ids
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "oddiya-${var.environment}-redis"
  description                = "Redis cluster for Oddiya"
  node_type                  = var.node_type
  parameter_group_name       = "default.redis7"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [var.redis_security_group_id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  automatic_failover_enabled = true
  num_cache_clusters         = var.num_cache_nodes
  
  tags = {
    Name        = "oddiya-${var.environment}-redis"
    Environment = var.environment
  }
}
EOF

cat > terraform/modules/elasticache/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "redis_security_group_id" {
  type = string
}

variable "node_type" {
  type = string
}

variable "num_cache_nodes" {
  type = number
}
EOF

cat > terraform/modules/elasticache/outputs.tf << 'EOF'
output "configuration_endpoint" {
  value = aws_elasticache_replication_group.main.configuration_endpoint_address
}

output "primary_endpoint" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}
EOF

# S3 Module
cat > terraform/modules/s3/main.tf << 'EOF'
resource "aws_s3_bucket" "static" {
  bucket = "oddiya-${var.environment}-static-assets"
}

resource "aws_s3_bucket" "media_input" {
  bucket = "oddiya-${var.environment}-media-input"
}

resource "aws_s3_bucket" "media_output" {
  bucket = "oddiya-${var.environment}-media-output"
}

resource "aws_s3_bucket" "backups" {
  bucket = "oddiya-${var.environment}-backups"
}

resource "aws_s3_bucket_versioning" "all" {
  for_each = {
    static = aws_s3_bucket.static.id
    input  = aws_s3_bucket.media_input.id
    output = aws_s3_bucket.media_output.id
    backup = aws_s3_bucket.backups.id
  }
  
  bucket = each.value
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "all" {
  for_each = {
    static = aws_s3_bucket.static.id
    input  = aws_s3_bucket.media_input.id
    output = aws_s3_bucket.media_output.id
    backup = aws_s3_bucket.backups.id
  }
  
  bucket = each.value
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
EOF

cat > terraform/modules/s3/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "account_id" {
  type = string
}
EOF

cat > terraform/modules/s3/outputs.tf << 'EOF'
output "static_bucket_name" {
  value = aws_s3_bucket.static.id
}

output "static_bucket_domain" {
  value = aws_s3_bucket.static.bucket_regional_domain_name
}

output "media_input_bucket_name" {
  value = aws_s3_bucket.media_input.id
}

output "media_output_bucket_name" {
  value = aws_s3_bucket.media_output.id
}

output "backup_bucket_name" {
  value = aws_s3_bucket.backups.id
}
EOF

# ECS Module
cat > terraform/modules/ecs/main.tf << 'EOF'
resource "aws_ecs_cluster" "main" {
  name = "oddiya-${var.environment}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-cluster"
    Environment = var.environment
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name
  
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
  
  default_capacity_provider_strategy {
    base              = 1
    weight            = 100
    capacity_provider = "FARGATE"
  }
}
EOF

cat > terraform/modules/ecs/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "ecs_security_group_id" {
  type = string
}
EOF

cat > terraform/modules/ecs/outputs.tf << 'EOF'
output "cluster_id" {
  value = aws_ecs_cluster.main.id
}

output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  value = aws_ecs_cluster.main.arn
}
EOF

# ALB Module
cat > terraform/modules/alb/main.tf << 'EOF'
resource "aws_lb" "main" {
  name               = "oddiya-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.public_subnet_ids
  
  enable_deletion_protection = false
  enable_http2              = true
  
  tags = {
    Name        = "oddiya-${var.environment}-alb"
    Environment = var.environment
  }
}

resource "aws_lb_target_group" "main" {
  name        = "oddiya-${var.environment}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 30
    interval            = 60
    path                = "/health"
    matcher             = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"
  
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}
EOF

cat > terraform/modules/alb/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "certificate_arn" {
  type    = string
  default = ""
}
EOF

cat > terraform/modules/alb/outputs.tf << 'EOF'
output "alb_arn" {
  value = aws_lb.main.arn
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "alb_zone_id" {
  value = aws_lb.main.zone_id
}

output "target_group_arn" {
  value = aws_lb_target_group.main.arn
}
EOF

# Lambda Module
cat > terraform/modules/lambda/main.tf << 'EOF'
resource "aws_iam_role" "lambda" {
  name = "oddiya-${var.environment}-lambda-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_lambda_function" "video_processor" {
  function_name = "oddiya-${var.environment}-video-processor"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 900
  memory_size   = 3008
  
  filename         = "${path.module}/lambda_placeholder.zip"
  source_code_hash = filebase64sha256("${path.module}/lambda_placeholder.zip")
  
  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }
  
  environment {
    variables = {
      S3_INPUT_BUCKET  = var.s3_input_bucket
      S3_OUTPUT_BUCKET = var.s3_output_bucket
    }
  }
}

# Create placeholder Lambda deployment package
resource "local_file" "lambda_code" {
  filename = "${path.module}/lambda_placeholder.py"
  content  = "def handler(event, context): return {'statusCode': 200}"
}

resource "null_resource" "lambda_zip" {
  provisioner "local-exec" {
    command = "cd ${path.module} && zip lambda_placeholder.zip lambda_placeholder.py"
  }
  depends_on = [local_file.lambda_code]
}
EOF

cat > terraform/modules/lambda/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "lambda_security_group_id" {
  type = string
}

variable "s3_input_bucket" {
  type = string
}

variable "s3_output_bucket" {
  type = string
}
EOF

cat > terraform/modules/lambda/outputs.tf << 'EOF'
output "video_processor_arn" {
  value = aws_lambda_function.video_processor.arn
}

output "video_processor_name" {
  value = aws_lambda_function.video_processor.function_name
}
EOF

# DynamoDB Module
cat > terraform/modules/dynamodb/main.tf << 'EOF'
resource "aws_dynamodb_table" "sessions" {
  name           = "oddiya-${var.environment}-sessions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "session_id"
  
  attribute {
    name = "session_id"
    type = "S"
  }
  
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-sessions"
    Environment = var.environment
  }
}

resource "aws_dynamodb_table" "queues" {
  name           = "oddiya-${var.environment}-queues"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "queue_id"
  
  attribute {
    name = "queue_id"
    type = "S"
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-queues"
    Environment = var.environment
  }
}
EOF

cat > terraform/modules/dynamodb/variables.tf << 'EOF'
variable "environment" {
  type = string
}
EOF

cat > terraform/modules/dynamodb/outputs.tf << 'EOF'
output "sessions_table_name" {
  value = aws_dynamodb_table.sessions.name
}

output "queues_table_name" {
  value = aws_dynamodb_table.queues.name
}
EOF

# SQS Module
cat > terraform/modules/sqs/main.tf << 'EOF'
resource "aws_sqs_queue" "video_queue" {
  name                       = "oddiya-${var.environment}-video-queue"
  visibility_timeout_seconds = 960
  message_retention_seconds  = 86400
  
  tags = {
    Name        = "oddiya-${var.environment}-video-queue"
    Environment = var.environment
  }
}

resource "aws_lambda_event_source_mapping" "sqs_lambda" {
  event_source_arn = aws_sqs_queue.video_queue.arn
  function_name    = var.lambda_arn
  batch_size       = 1
}
EOF

cat > terraform/modules/sqs/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "lambda_arn" {
  type = string
}
EOF

cat > terraform/modules/sqs/outputs.tf << 'EOF'
output "video_queue_url" {
  value = aws_sqs_queue.video_queue.url
}

output "video_queue_arn" {
  value = aws_sqs_queue.video_queue.arn
}
EOF

# Secrets Manager Module
cat > terraform/modules/secrets/main.tf << 'EOF'
resource "aws_secretsmanager_secret" "app_secrets" {
  name = "oddiya-${var.environment}-app-secrets"
  
  tags = {
    Name        = "oddiya-${var.environment}-app-secrets"
    Environment = var.environment
  }
}
EOF

cat > terraform/modules/secrets/variables.tf << 'EOF'
variable "environment" {
  type = string
}
EOF

cat > terraform/modules/secrets/outputs.tf << 'EOF'
output "app_secrets_arn" {
  value = aws_secretsmanager_secret.app_secrets.arn
}
EOF

# Monitoring Module
cat > terraform/modules/monitoring/main.tf << 'EOF'
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "oddiya-${var.environment}"
  
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", { stat = "Average" }],
            [".", "MemoryUtilization", { stat = "Average" }]
          ]
          period = 300
          stat   = "Average"
          region = "ap-northeast-2"
          title  = "ECS Metrics"
        }
      }
    ]
  })
}
EOF

cat > terraform/modules/monitoring/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "rds_cluster_id" {
  type = string
}
EOF

cat > terraform/modules/monitoring/outputs.tf << 'EOF'
output "dashboard_url" {
  value = "https://console.aws.amazon.com/cloudwatch/home?region=ap-northeast-2#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}
EOF

# WAF Module
cat > terraform/modules/waf/main.tf << 'EOF'
resource "aws_wafv2_web_acl" "main" {
  name  = "oddiya-${var.environment}-waf"
  scope = "REGIONAL"
  
  default_action {
    allow {}
  }
  
  rule {
    name     = "RateLimitRule"
    priority = 1
    
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    
    action {
      block {}
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitRule"
      sampled_requests_enabled   = true
    }
  }
  
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "oddiya-${var.environment}-waf"
    sampled_requests_enabled   = true
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-waf"
    Environment = var.environment
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  resource_arn = var.alb_arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn
}
EOF

cat > terraform/modules/waf/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "alb_arn" {
  type = string
}
EOF

cat > terraform/modules/waf/outputs.tf << 'EOF'
output "web_acl_id" {
  value = aws_wafv2_web_acl.main.id
}

output "web_acl_arn" {
  value = aws_wafv2_web_acl.main.arn
}
EOF

# CloudFront Module
cat > terraform/modules/cloudfront/main.tf << 'EOF'
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "Oddiya ${var.environment} CDN"
  default_root_object = "index.html"
  
  origin {
    domain_name = var.alb_dns_name
    origin_id   = "alb"
    
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }
  
  origin {
    domain_name = var.s3_bucket_domain
    origin_id   = "s3"
    
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.main.cloudfront_access_identity_path
    }
  }
  
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "alb"
    
    forwarded_values {
      query_string = true
      headers      = ["*"]
      
      cookies {
        forward = "all"
      }
    }
    
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
  }
  
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  
  viewer_certificate {
    cloudfront_default_certificate = true
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-cdn"
    Environment = var.environment
  }
}

resource "aws_cloudfront_origin_access_identity" "main" {
  comment = "Oddiya ${var.environment} S3 OAI"
}
EOF

cat > terraform/modules/cloudfront/variables.tf << 'EOF'
variable "environment" {
  type = string
}

variable "alb_dns_name" {
  type = string
}

variable "s3_bucket_domain" {
  type = string
}

variable "waf_acl_id" {
  type = string
}
EOF

cat > terraform/modules/cloudfront/outputs.tf << 'EOF'
output "distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "distribution_domain_name" {
  value = aws_cloudfront_distribution.main.domain_name
}
EOF

echo "All Terraform modules created successfully!"