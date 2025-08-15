# Oddiya Phase 1: DynamoDB-Only Infrastructure
# This configuration uses only DynamoDB for all data storage
# Estimated cost: ~$30-50/month with pay-per-request billing

terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "oddiya-terraform-state"
    key            = "phase1-dynamodb/terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

provider "aws" {
  region = "ap-northeast-2"
  
  default_tags {
    tags = {
      Project     = "Oddiya"
      Environment = "phase1-dynamodb"
      Phase       = "1"
      ManagedBy   = "Terraform"
    }
  }
}

# Use default VPC to minimize costs
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Security Group for ECS Tasks
resource "aws_security_group" "app" {
  name        = "oddiya-phase1-app-sg"
  description = "Security group for Oddiya Phase 1 application"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "oddiya-phase1-app-sg"
  }
}

# DynamoDB Tables for Phase 1
resource "aws_dynamodb_table" "users" {
  name           = "oddiya_users"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "email"
    type = "S"
  }
  
  attribute {
    name = "provider"
    type = "S"
  }
  
  attribute {
    name = "providerId"
    type = "S"
  }
  
  global_secondary_index {
    name            = "email-index"
    hash_key        = "email"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "provider-index"
    hash_key        = "provider"
    range_key       = "providerId"
    projection_type = "ALL"
  }
  
  tags = {
    Name = "oddiya_users"
    Phase = "1"
  }
}

resource "aws_dynamodb_table" "places" {
  name           = "oddiya_places"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "category"
    type = "S"
  }
  
  attribute {
    name = "geohash"
    type = "S"
  }
  
  attribute {
    name = "naverPlaceId"
    type = "S"
  }
  
  global_secondary_index {
    name            = "category-index"
    hash_key        = "category"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "geohash-index"
    hash_key        = "geohash"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "naverPlaceId-index"
    hash_key        = "naverPlaceId"
    projection_type = "ALL"
  }
  
  tags = {
    Name = "oddiya_places"
    Phase = "1"
  }
}

resource "aws_dynamodb_table" "travel_plans" {
  name           = "oddiya_travel_plans"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "userId"
    type = "S"
  }
  
  attribute {
    name = "status"
    type = "S"
  }
  
  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    range_key       = "status"
    projection_type = "ALL"
  }
  
  tags = {
    Name = "oddiya_travel_plans"
    Phase = "1"
  }
}

resource "aws_dynamodb_table" "itinerary_items" {
  name           = "oddiya_itinerary_items"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "travelPlanId"
    type = "S"
  }
  
  attribute {
    name = "dayNumber"
    type = "N"
  }
  
  global_secondary_index {
    name            = "travelPlanId-index"
    hash_key        = "travelPlanId"
    range_key       = "dayNumber"
    projection_type = "ALL"
  }
  
  tags = {
    Name = "oddiya_itinerary_items"
    Phase = "1"
  }
}

resource "aws_dynamodb_table" "saved_plans" {
  name           = "oddiya_saved_plans"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "userId"
    type = "S"
  }
  
  attribute {
    name = "travelPlanId"
    type = "S"
  }
  
  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "travelPlanId-index"
    hash_key        = "travelPlanId"
    projection_type = "ALL"
  }
  
  tags = {
    Name = "oddiya_saved_plans"
    Phase = "1"
  }
}

resource "aws_dynamodb_table" "sessions" {
  name           = "oddiya_sessions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "sessionId"
  
  attribute {
    name = "sessionId"
    type = "S"
  }
  
  attribute {
    name = "userId"
    type = "S"
  }
  
  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
  
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
  
  tags = {
    Name = "oddiya_sessions"
    Phase = "1"
  }
}

# S3 Bucket for media storage
resource "aws_s3_bucket" "media" {
  bucket = "oddiya-phase1-media"
  
  tags = {
    Name = "oddiya-phase1-media"
    Phase = "1"
  }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST", "DELETE", "HEAD"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# ECR Repository for Docker images
resource "aws_ecr_repository" "app" {
  name                 = "oddiya-phase1"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
  
  tags = {
    Name = "oddiya-phase1"
    Phase = "1"
  }
}

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "oddiya-phase1-cluster"
  
  setting {
    name  = "containerInsights"
    value = "disabled"  # Save costs
  }
  
  tags = {
    Name = "oddiya-phase1-cluster"
    Phase = "1"
  }
}

# IAM Role for ECS Task Execution
resource "aws_iam_role" "ecs_execution" {
  name = "oddiya-phase1-ecs-execution"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM Role for ECS Tasks
resource "aws_iam_role" "ecs_task" {
  name = "oddiya-phase1-ecs-task"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

# IAM Policy for DynamoDB access
resource "aws_iam_policy" "dynamodb_access" {
  name        = "oddiya-phase1-dynamodb-access"
  description = "Allow ECS tasks to access DynamoDB tables"
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:DeleteItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
          "dynamodb:DescribeTable"
        ]
        Resource = [
          aws_dynamodb_table.users.arn,
          "${aws_dynamodb_table.users.arn}/index/*",
          aws_dynamodb_table.places.arn,
          "${aws_dynamodb_table.places.arn}/index/*",
          aws_dynamodb_table.travel_plans.arn,
          "${aws_dynamodb_table.travel_plans.arn}/index/*",
          aws_dynamodb_table.itinerary_items.arn,
          "${aws_dynamodb_table.itinerary_items.arn}/index/*",
          aws_dynamodb_table.saved_plans.arn,
          "${aws_dynamodb_table.saved_plans.arn}/index/*",
          aws_dynamodb_table.sessions.arn,
          "${aws_dynamodb_table.sessions.arn}/index/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_dynamodb" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.dynamodb_access.arn
}

# IAM Policy for S3 access
resource "aws_iam_policy" "s3_access" {
  name        = "oddiya-phase1-s3-access"
  description = "Allow ECS tasks to access S3 bucket"
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.media.arn,
          "${aws_s3_bucket.media.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_s3" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.s3_access.arn
}

# ECS Task Definition
resource "aws_ecs_task_definition" "app" {
  family                   = "oddiya-phase1-app"
  network_mode            = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                     = "512"
  memory                  = "1024"
  execution_role_arn      = aws_iam_role.ecs_execution.arn
  task_role_arn           = aws_iam_role.ecs_task.arn
  
  container_definitions = jsonencode([
    {
      name  = "app"
      image = "${aws_ecr_repository.app.repository_url}:latest"
      
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "dynamodb"
        },
        {
          name  = "AWS_REGION"
          value = "ap-northeast-2"
        },
        {
          name  = "S3_BUCKET"
          value = aws_s3_bucket.media.id
        }
      ]
      
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-create-group"  = "true"
          "awslogs-group"         = "/ecs/oddiya-phase1"
          "awslogs-region"        = "ap-northeast-2"
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# ECS Service with Fargate Spot
resource "aws_ecs_service" "app" {
  name            = "oddiya-phase1-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 100
    base              = 0
  }
  
  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }
  
  tags = {
    Name = "oddiya-phase1-app"
    Phase = "1"
  }
}

# Outputs
output "dynamodb_tables" {
  value = {
    users           = aws_dynamodb_table.users.name
    places          = aws_dynamodb_table.places.name
    travel_plans    = aws_dynamodb_table.travel_plans.name
    itinerary_items = aws_dynamodb_table.itinerary_items.name
    saved_plans     = aws_dynamodb_table.saved_plans.name
    sessions        = aws_dynamodb_table.sessions.name
  }
}

output "s3_bucket" {
  value = aws_s3_bucket.media.id
}

output "ecr_repository" {
  value = aws_ecr_repository.app.repository_url
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.app.name
}