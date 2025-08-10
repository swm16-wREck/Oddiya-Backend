# Oddiya Minimal Cost-Optimized Infrastructure
# This configuration provides the absolute minimum required to run the application
# Estimated cost: ~$50-100/month (can scale to zero when not in use)

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
    key            = "minimal/terraform.tfstate"
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
      Environment = "minimal"
      ManagedBy   = "Terraform"
      CostOptimized = "true"
    }
  }
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

# VPC - Using default VPC to save costs (no NAT Gateway needed)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Security Group for all services
resource "aws_security_group" "minimal" {
  name        = "oddiya-minimal-sg"
  description = "Minimal security group for Oddiya"
  vpc_id      = data.aws_vpc.default.id

  # Allow HTTP
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow HTTPS
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow application port
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow all outbound
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "oddiya-minimal-sg"
  }
}

# S3 Buckets (virtually free until you store data)
resource "aws_s3_bucket" "media" {
  bucket = "oddiya-minimal-media"
  
  tags = {
    Name = "oddiya-minimal-media"
  }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

# DynamoDB Tables (On-Demand pricing - pay only for what you use)
resource "aws_dynamodb_table" "sessions" {
  name           = "oddiya-minimal-sessions"
  billing_mode   = "PAY_PER_REQUEST"  # No minimum cost
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
    Name = "oddiya-minimal-sessions"
  }
}

# ECS Cluster (no cost for the cluster itself)
resource "aws_ecs_cluster" "minimal" {
  name = "oddiya-minimal-cluster"
  
  setting {
    name  = "containerInsights"
    value = "disabled"  # Save CloudWatch costs
  }
  
  tags = {
    Name = "oddiya-minimal-cluster"
  }
}

# ECS Task Definition for Fargate Spot (70% cheaper than regular Fargate)
resource "aws_ecs_task_definition" "app" {
  family                   = "oddiya-minimal-app"
  network_mode            = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                     = "256"   # Minimum CPU (0.25 vCPU)
  memory                  = "512"   # Minimum memory
  
  container_definitions = jsonencode([
    {
      name  = "app"
      image = "nginx:alpine"  # Placeholder - replace with your app
      
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "NODE_ENV"
          value = "production"
        }
      ]
      
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-create-group"  = "true"
          "awslogs-group"         = "/ecs/oddiya-minimal"
          "awslogs-region"        = "ap-northeast-2"
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
  
  execution_role_arn = aws_iam_role.ecs_execution.arn
  task_role_arn      = aws_iam_role.ecs_task.arn
}

# IAM Roles for ECS
resource "aws_iam_role" "ecs_execution" {
  name = "oddiya-minimal-ecs-execution"
  
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

resource "aws_iam_role" "ecs_task" {
  name = "oddiya-minimal-ecs-task"
  
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

# ECS Service with Fargate Spot (can scale to 0)
resource "aws_ecs_service" "app" {
  name            = "oddiya-minimal-app"
  cluster         = aws_ecs_cluster.minimal.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 0  # START WITH 0 TO SAVE COSTS - scale up when needed
  
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"  # 70% cheaper than regular Fargate
    weight            = 100
    base              = 0
  }
  
  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.minimal.id]
    assign_public_ip = true
  }
  
  tags = {
    Name = "oddiya-minimal-app"
  }
}

# Outputs
output "cluster_name" {
  value = aws_ecs_cluster.minimal.name
}

output "service_name" {
  value = aws_ecs_service.app.name
}

output "s3_bucket" {
  value = aws_s3_bucket.media.id
}

output "dynamodb_table" {
  value = aws_dynamodb_table.sessions.name
}

output "security_group_id" {
  value = aws_security_group.minimal.id
}

output "scale_up_command" {
  value = "aws ecs update-service --cluster ${aws_ecs_cluster.minimal.name} --service ${aws_ecs_service.app.name} --desired-count 1"
}

output "scale_down_command" {
  value = "aws ecs update-service --cluster ${aws_ecs_cluster.minimal.name} --service ${aws_ecs_service.app.name} --desired-count 0"
}