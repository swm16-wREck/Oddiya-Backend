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

# Target group for OAuth service
resource "aws_lb_target_group" "oauth" {
  name        = "oddiya-${var.environment}-oauth-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 10
    interval            = 30
    path                = "/auth/health"
    matcher             = "200"
  }
  
  stickiness {
    enabled         = true
    type            = "app_cookie"
    cookie_name     = "OAUTH_SESSION"
    cookie_duration = 86400  # 24 hours
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

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"
  certificate_arn   = var.certificate_arn
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.main.arn
  }
}

# Listener rule for OAuth endpoints
resource "aws_lb_listener_rule" "oauth" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.oauth.arn
  }
  
  condition {
    path_pattern {
      values = [
        "/auth/*",
        "/oauth/*",
        "/api/auth/*",
        "/api/oauth/*",
        "/.well-known/jwks.json"
      ]
    }
  }
}

# Listener rule for OAuth callback
resource "aws_lb_listener_rule" "oauth_callback" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 99
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.oauth.arn
  }
  
  condition {
    path_pattern {
      values = ["/auth/callback", "/auth/callback/*"]
    }
  }
  
  condition {
    query_string {
      key   = "code"
      value = "*"
    }
  }
}
