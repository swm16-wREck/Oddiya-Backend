# ECS Task Definition for Auth Service with Supabase OAuth

resource "aws_ecs_task_definition" "auth_service" {
  family                   = "oddiya-${var.environment}-auth"
  requires_compatibilities = ["FARGATE"]
  network_mode            = "awsvpc"
  cpu                     = var.auth_service_cpu
  memory                  = var.auth_service_memory
  execution_role_arn      = aws_iam_role.ecs_execution_role.arn
  task_role_arn           = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name  = "auth-service"
      image = "${var.ecr_repository_url}/auth-service:${var.auth_service_version}"
      
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = var.environment
        },
        {
          name  = "SERVER_PORT"
          value = "8080"
        },
        {
          name  = "SUPABASE_URL"
          value = var.supabase_url
        },
        {
          name  = "OAUTH_REDIRECT_URL"
          value = var.oauth_redirect_url
        },
        {
          name  = "ALLOWED_OAUTH_PROVIDERS"
          value = join(",", var.allowed_oauth_providers)
        },
        {
          name  = "JWT_ISSUER"
          value = var.supabase_url
        },
        {
          name  = "SESSION_DURATION"
          value = tostring(var.session_duration)
        },
        {
          name  = "REFRESH_TOKEN_TTL"
          value = tostring(var.refresh_token_ttl)
        },
        {
          name  = "DYNAMODB_SESSION_TABLE"
          value = var.oauth_sessions_table_name
        },
        {
          name  = "AWS_REGION"
          value = var.aws_region
        }
      ]
      
      secrets = [
        {
          name      = "SUPABASE_ANON_KEY"
          valueFrom = "${var.supabase_secret_arn}:supabase_anon_key::"
        },
        {
          name      = "SUPABASE_SERVICE_KEY"
          valueFrom = "${var.supabase_secret_arn}:supabase_service_key::"
        },
        {
          name      = "SUPABASE_JWT_SECRET"
          valueFrom = "${var.supabase_secret_arn}:supabase_jwt_secret::"
        },
        {
          name      = "DATABASE_PASSWORD"
          valueFrom = "${var.database_secret_arn}:password::"
        }
      ]
      
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.auth_service.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
      
      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/auth/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name        = "oddiya-${var.environment}-auth-task"
    Environment = var.environment
    Service     = "auth"
  }
}

# ECS Service for Auth
resource "aws_ecs_service" "auth_service" {
  name            = "oddiya-${var.environment}-auth-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.auth_service.arn
  desired_count   = var.auth_service_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.oauth_target_group_arn
    container_name   = "auth-service"
    container_port   = 8080
  }

  service_registries {
    registry_arn = aws_service_discovery_service.auth_service.arn
  }

  deployment_configuration {
    maximum_percent         = 200
    minimum_healthy_percent = 100
  }

  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  tags = {
    Name        = "oddiya-${var.environment}-auth-service"
    Environment = var.environment
    Service     = "auth"
  }

  depends_on = [var.alb_listener_arn]
}

# Service Discovery for Auth Service
resource "aws_service_discovery_service" "auth_service" {
  name = "auth"

  dns_config {
    namespace_id = var.service_discovery_namespace_id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

# CloudWatch Log Group for Auth Service
resource "aws_cloudwatch_log_group" "auth_service" {
  name              = "/ecs/oddiya-${var.environment}/auth-service"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "oddiya-${var.environment}-auth-logs"
    Environment = var.environment
    Service     = "auth"
  }
}

# Auto Scaling for Auth Service
resource "aws_appautoscaling_target" "auth_service" {
  max_capacity       = var.auth_service_max_count
  min_capacity       = var.auth_service_min_count
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.auth_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "auth_service_cpu" {
  name               = "oddiya-${var.environment}-auth-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.auth_service.resource_id
  scalable_dimension = aws_appautoscaling_target.auth_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.auth_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value = 70.0
  }
}

resource "aws_appautoscaling_policy" "auth_service_memory" {
  name               = "oddiya-${var.environment}-auth-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.auth_service.resource_id
  scalable_dimension = aws_appautoscaling_target.auth_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.auth_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value = 80.0
  }
}