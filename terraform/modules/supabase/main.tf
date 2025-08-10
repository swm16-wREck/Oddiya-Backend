# Supabase OAuth Configuration Module
# This module configures the necessary resources for Supabase OAuth integration

# Supabase configuration stored in Secrets Manager
resource "aws_secretsmanager_secret" "supabase_config" {
  name                    = "${var.project_name}-${var.environment}-supabase-config"
  description            = "Supabase OAuth configuration for ${var.project_name}"
  recovery_window_in_days = var.secret_recovery_days

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-${var.environment}-supabase-config"
      Environment = var.environment
      Service     = "supabase-auth"
    }
  )
}

resource "aws_secretsmanager_secret_version" "supabase_config" {
  secret_id = aws_secretsmanager_secret.supabase_config.id
  secret_string = jsonencode({
    supabase_url           = var.supabase_url
    supabase_anon_key      = var.supabase_anon_key
    supabase_service_key   = var.supabase_service_key
    supabase_jwt_secret    = var.supabase_jwt_secret
    oauth_redirect_url     = var.oauth_redirect_url
    allowed_oauth_providers = var.allowed_oauth_providers
  })
}

# IAM policy for accessing Supabase secrets
resource "aws_iam_policy" "supabase_secrets_access" {
  name        = "${var.project_name}-${var.environment}-supabase-secrets-policy"
  description = "Policy for accessing Supabase OAuth secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = aws_secretsmanager_secret.supabase_config.arn
      }
    ]
  })

  tags = var.tags
}

# Security group rules for Supabase OAuth communication
resource "aws_security_group_rule" "supabase_egress_https" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = var.app_security_group_id
  description       = "Allow HTTPS traffic to Supabase"
}

# CloudWatch log group for OAuth events
resource "aws_cloudwatch_log_group" "oauth_logs" {
  name              = "/aws/application/${var.project_name}/${var.environment}/oauth"
  retention_in_days = var.log_retention_days

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-${var.environment}-oauth-logs"
      Environment = var.environment
      Service     = "oauth"
    }
  )
}

# DynamoDB table for OAuth session management (optional)
resource "aws_dynamodb_table" "oauth_sessions" {
  count = var.enable_session_management ? 1 : 0

  name           = "${var.project_name}-${var.environment}-oauth-sessions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "session_id"
  range_key      = "user_id"

  attribute {
    name = "session_id"
    type = "S"
  }

  attribute {
    name = "user_id"
    type = "S"
  }

  attribute {
    name = "expires_at"
    type = "N"
  }

  ttl {
    enabled        = true
    attribute_name = "expires_at"
  }

  global_secondary_index {
    name            = "user_sessions_index"
    hash_key        = "user_id"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "expiry_index"
    hash_key        = "expires_at"
    projection_type = "KEYS_ONLY"
  }

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-${var.environment}-oauth-sessions"
      Environment = var.environment
      Service     = "oauth"
    }
  )
}

# SNS topic for OAuth events (login, logout, etc.)
resource "aws_sns_topic" "oauth_events" {
  name = "${var.project_name}-${var.environment}-oauth-events"

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-${var.environment}-oauth-events"
      Environment = var.environment
      Service     = "oauth"
    }
  )
}

# CloudWatch metric for OAuth success/failure rates
resource "aws_cloudwatch_log_metric_filter" "oauth_success_rate" {
  name           = "${var.project_name}-${var.environment}-oauth-success"
  log_group_name = aws_cloudwatch_log_group.oauth_logs.name
  pattern        = "[timestamp, request_id, level = \"INFO\", message = \"OAuth login successful\", ...]"

  metric_transformation {
    name      = "OAuthSuccessCount"
    namespace = "${var.project_name}/OAuth"
    value     = "1"
  }
}

resource "aws_cloudwatch_log_metric_filter" "oauth_failure_rate" {
  name           = "${var.project_name}-${var.environment}-oauth-failure"
  log_group_name = aws_cloudwatch_log_group.oauth_logs.name
  pattern        = "[timestamp, request_id, level = \"ERROR\", message = \"OAuth login failed\", ...]"

  metric_transformation {
    name      = "OAuthFailureCount"
    namespace = "${var.project_name}/OAuth"
    value     = "1"
  }
}

# CloudWatch alarm for high OAuth failure rate
resource "aws_cloudwatch_metric_alarm" "oauth_failure_alarm" {
  alarm_name          = "${var.project_name}-${var.environment}-oauth-high-failure-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "OAuthFailureCount"
  namespace           = "${var.project_name}/OAuth"
  period              = "300"
  statistic           = "Sum"
  threshold           = var.oauth_failure_threshold
  alarm_description   = "This metric monitors OAuth failure rate"
  alarm_actions       = [aws_sns_topic.oauth_events.arn]

  tags = var.tags
}

# SSM parameters for OAuth configuration (alternative to Secrets Manager for non-sensitive config)
resource "aws_ssm_parameter" "oauth_config" {
  name  = "/${var.project_name}/${var.environment}/oauth/config"
  type  = "String"
  value = jsonencode({
    redirect_urls       = var.oauth_redirect_url
    allowed_providers   = var.allowed_oauth_providers
    session_duration    = var.session_duration
    refresh_token_ttl   = var.refresh_token_ttl
    enable_mfa         = var.enable_mfa
  })

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-${var.environment}-oauth-config"
      Environment = var.environment
      Service     = "oauth"
    }
  )
}

# Outputs for use in other modules
output "supabase_secret_arn" {
  description = "ARN of the Supabase configuration secret"
  value       = aws_secretsmanager_secret.supabase_config.arn
}

output "oauth_sessions_table_name" {
  description = "Name of the OAuth sessions DynamoDB table"
  value       = var.enable_session_management ? aws_dynamodb_table.oauth_sessions[0].name : null
}

output "oauth_events_topic_arn" {
  description = "ARN of the OAuth events SNS topic"
  value       = aws_sns_topic.oauth_events.arn
}

output "oauth_logs_group_name" {
  description = "Name of the OAuth CloudWatch log group"
  value       = aws_cloudwatch_log_group.oauth_logs.name
}

output "supabase_secrets_policy_arn" {
  description = "ARN of the IAM policy for accessing Supabase secrets"
  value       = aws_iam_policy.supabase_secrets_access.arn
}