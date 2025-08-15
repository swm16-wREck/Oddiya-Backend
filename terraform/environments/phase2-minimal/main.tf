# Phase 2 MINIMAL: PostgreSQL Infrastructure for Zero Users
# Optimized for lowest possible cost - scale up later
# Estimated cost: ~$30-40/month

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  # Backend configuration for state management
  backend "s3" {
    bucket         = "oddiya-terraform-state"
    key            = "phase2-minimal/terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

provider "aws" {
  region = "ap-northeast-2"
  
  default_tags {
    tags = {
      Environment = "dev"
      Project     = "Oddiya"
      Phase       = "2-minimal"
      CostOptimized = "true"
    }
  }
}

# Use default VPC to save costs (no NAT Gateway needed)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ==========================================
# Security Groups
# ==========================================

resource "aws_security_group" "app" {
  name        = "oddiya-minimal-app-sg"
  description = "Security group for Oddiya application"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  ingress {
    from_port   = 80
    to_port     = 80
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
    Name = "oddiya-minimal-app-sg"
  }
}

resource "aws_security_group" "database" {
  name        = "oddiya-minimal-db-sg"
  description = "Security group for PostgreSQL database"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "oddiya-minimal-db-sg"
  }
}

# ==========================================
# RDS PostgreSQL (NOT Aurora - cheaper for low usage)
# ==========================================

resource "aws_db_subnet_group" "main" {
  name       = "oddiya-minimal-db-subnet"
  subnet_ids = data.aws_subnets.default.ids

  tags = {
    Name = "oddiya-minimal-db-subnet"
  }
}

# Use regular RDS PostgreSQL instead of Aurora for cost savings
resource "aws_db_instance" "postgres" {
  identifier     = "oddiya-minimal-db"
  engine         = "postgres"
  engine_version = "15.7"
  
  # MINIMAL SIZE - scale up later
  instance_class        = "db.t4g.micro"  # Smallest Graviton instance
  allocated_storage     = 20              # Minimum storage
  storage_type          = "gp3"           # Better than gp2
  storage_encrypted     = true
  
  db_name  = "oddiya"
  username = "oddiya_admin"
  password = random_password.db_password.result
  
  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  
  # Backup - minimal for dev
  backup_retention_period = 1
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
  
  # Cost optimization
  skip_final_snapshot       = true  # For dev environment
  deletion_protection       = false # For dev environment
  auto_minor_version_upgrade = true
  
  # No Multi-AZ for cost savings
  multi_az = false
  
  # Basic monitoring only
  enabled_cloudwatch_logs_exports = []
  performance_insights_enabled    = false # Save costs
  monitoring_interval            = 0      # Disable enhanced monitoring
  
  tags = {
    Name = "oddiya-minimal-db"
  }
}

# Random password for DB
resource "random_password" "db_password" {
  length  = 16
  special = true
}

# Store password in Secrets Manager
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "oddiya-minimal-db-password"
  recovery_window_in_days = 0  # Immediate deletion for dev

  tags = {
    Name = "oddiya-minimal-db-password"
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id = aws_secretsmanager_secret.db_password.id
  secret_string = jsonencode({
    username = "oddiya_admin"
    password = random_password.db_password.result
    host     = aws_db_instance.postgres.endpoint
    port     = 5432
    database = "oddiya"
  })
}

# ==========================================
# ECR Repository for Docker Images
# ==========================================

resource "aws_ecr_repository" "app" {
  name                 = "oddiya-minimal"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false  # Disable for cost savings
  }
  
  tags = {
    Name = "oddiya-minimal"
  }
}

# ECR Lifecycle Policy to keep only recent images
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 3 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 3
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ==========================================
# ECS Cluster and Service
# ==========================================

resource "aws_ecs_cluster" "main" {
  name = "oddiya-minimal-cluster"
  
  setting {
    name  = "containerInsights"
    value = "disabled"  # Save CloudWatch costs
  }
  
  tags = {
    Name = "oddiya-minimal-cluster"
  }
}

# IAM Role for ECS Task Execution
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

# IAM Role for ECS Tasks
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

# Policy for accessing Secrets Manager
resource "aws_iam_role_policy" "ecs_secrets" {
  name = "oddiya-minimal-ecs-secrets"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.db_password.arn
      }
    ]
  })
}

# ECS Task Definition
resource "aws_ecs_task_definition" "app" {
  family                   = "oddiya-minimal-app"
  network_mode            = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                     = "256"   # 0.25 vCPU - minimum
  memory                  = "512"   # 0.5 GB - minimum
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
          value = "postgresql"
        },
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/oddiya"
        },
        {
          name  = "SERVER_PORT"
          value = "8080"
        }
      ]
      
      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${aws_secretsmanager_secret.db_password.arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.db_password.arn}:password::"
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
      
      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])
}

# ECS Service with Fargate Spot for 70% cost savings
resource "aws_ecs_service" "app" {
  name            = "oddiya-minimal-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  
  # START WITH 0 to save costs - scale up when needed
  desired_count = 0
  
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"  # 70% cheaper
    weight            = 100
    base              = 0
  }
  
  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true  # Required for Fargate in public subnet
  }
  
  # No load balancer for now - direct access
  
  tags = {
    Name = "oddiya-minimal-app"
  }
}

# ==========================================
# S3 Bucket for Application Files
# ==========================================

resource "aws_s3_bucket" "app_storage" {
  bucket = "oddiya-minimal-storage-${random_id.bucket_suffix.hex}"
  
  tags = {
    Name = "oddiya-minimal-storage"
  }
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket_public_access_block" "app_storage" {
  bucket = aws_s3_bucket.app_storage.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ==========================================
# Outputs
# ==========================================

output "database_endpoint" {
  value       = aws_db_instance.postgres.endpoint
  description = "PostgreSQL database endpoint"
}

output "database_name" {
  value       = aws_db_instance.postgres.db_name
  description = "Database name"
}

output "ecr_repository_url" {
  value       = aws_ecr_repository.app.repository_url
  description = "ECR repository URL for Docker images"
}

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "ECS cluster name"
}

output "ecs_service_name" {
  value       = aws_ecs_service.app.name
  description = "ECS service name"
}

output "estimated_monthly_cost" {
  value = "~$30-40/month (RDS: ~$13, ECS: ~$5-10, Storage: ~$5)"
  description = "Estimated monthly AWS costs"
}

output "scale_up_commands" {
  value = {
    start_app = "aws ecs update-service --cluster ${aws_ecs_cluster.main.name} --service ${aws_ecs_service.app.name} --desired-count 1"
    stop_app  = "aws ecs update-service --cluster ${aws_ecs_cluster.main.name} --service ${aws_ecs_service.app.name} --desired-count 0"
  }
  description = "Commands to start/stop the application"
}