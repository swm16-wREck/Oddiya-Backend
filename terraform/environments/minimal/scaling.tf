# Scaling Configuration for Oddiya Minimal Infrastructure
# Uncomment and apply these resources as your user base grows

# PHASE 1: Add RDS Aurora Serverless v2 (when you need a real database)
# Cost: ~$50-100/month minimum
/*
resource "aws_rds_cluster" "serverless" {
  cluster_identifier     = "oddiya-minimal-db"
  engine                = "aurora-postgresql"
  engine_mode           = "provisioned"
  engine_version        = "15.4"
  database_name         = "oddiya"
  master_username       = "oddiya_admin"
  master_password       = random_password.db.result
  
  serverlessv2_scaling_configuration {
    max_capacity = 1.0    # Start small
    min_capacity = 0.5    # Minimum ACU (Aurora Capacity Units)
  }
  
  skip_final_snapshot = true
  
  tags = {
    Name = "oddiya-minimal-db"
  }
}

resource "aws_rds_cluster_instance" "serverless" {
  cluster_identifier = aws_rds_cluster.serverless.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.serverless.engine
  engine_version     = aws_rds_cluster.serverless.engine_version
}

resource "random_password" "db" {
  length  = 32
  special = true
}
*/

# PHASE 2: Add Application Load Balancer (when you need HTTPS and multiple containers)
# Cost: ~$20/month
/*
resource "aws_lb" "minimal" {
  name               = "oddiya-minimal-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.minimal.id]
  subnets            = data.aws_subnets.default.ids
  
  enable_deletion_protection = false
  enable_http2              = true
  
  tags = {
    Name = "oddiya-minimal-alb"
  }
}

resource "aws_lb_target_group" "app" {
  name        = "oddiya-minimal-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.default.id
  target_type = "ip"
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = "/health"
    matcher             = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.minimal.arn
  port              = "80"
  protocol          = "HTTP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
*/

# PHASE 3: Add ElastiCache Redis (when you need caching)
# Cost: ~$15/month for t4g.micro
/*
resource "aws_elasticache_cluster" "minimal" {
  cluster_id           = "oddiya-minimal-redis"
  engine              = "redis"
  node_type           = "cache.t4g.micro"  # Cheapest option
  num_cache_nodes     = 1
  parameter_group_name = "default.redis7"
  port                = 6379
  
  tags = {
    Name = "oddiya-minimal-redis"
  }
}
*/

# PHASE 4: Add CloudFront CDN (when you have global users)
# Cost: Pay per GB transferred
/*
resource "aws_cloudfront_distribution" "minimal" {
  enabled             = true
  is_ipv6_enabled    = true
  default_root_object = "index.html"
  price_class        = "PriceClass_100"  # Use only North America and Europe to save costs
  
  origin {
    domain_name = aws_lb.minimal.dns_name
    origin_id   = "alb"
    
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }
  
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "alb"
    
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
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
    Name = "oddiya-minimal-cdn"
  }
}
*/

# PHASE 5: Auto Scaling for ECS (when you have consistent traffic)
/*
resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = 10
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.minimal.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_cpu" {
  name               = "oddiya-minimal-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace
  
  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value = 70.0
  }
}
*/