# Outputs for Supabase OAuth Module

output "supabase_config_secret_arn" {
  description = "ARN of the Supabase configuration secret in Secrets Manager"
  value       = aws_secretsmanager_secret.supabase_config.arn
}

output "supabase_config_secret_name" {
  description = "Name of the Supabase configuration secret"
  value       = aws_secretsmanager_secret.supabase_config.name
}

output "supabase_secrets_policy_arn" {
  description = "ARN of the IAM policy for accessing Supabase secrets"
  value       = aws_iam_policy.supabase_secrets_access.arn
}

output "oauth_sessions_table" {
  description = "OAuth sessions DynamoDB table details"
  value = var.enable_session_management ? {
    name = aws_dynamodb_table.oauth_sessions[0].name
    arn  = aws_dynamodb_table.oauth_sessions[0].arn
  } : null
}

output "oauth_events_topic" {
  description = "OAuth events SNS topic details"
  value = {
    arn  = aws_sns_topic.oauth_events.arn
    name = aws_sns_topic.oauth_events.name
  }
}

output "oauth_logs" {
  description = "OAuth CloudWatch log group details"
  value = {
    name = aws_cloudwatch_log_group.oauth_logs.name
    arn  = aws_cloudwatch_log_group.oauth_logs.arn
  }
}

output "oauth_config_parameter" {
  description = "SSM parameter for OAuth configuration"
  value = {
    name = aws_ssm_parameter.oauth_config.name
    arn  = aws_ssm_parameter.oauth_config.arn
  }
}

output "oauth_metrics" {
  description = "OAuth CloudWatch metrics"
  value = {
    success_metric = aws_cloudwatch_log_metric_filter.oauth_success_rate.name
    failure_metric = aws_cloudwatch_log_metric_filter.oauth_failure_rate.name
    failure_alarm  = aws_cloudwatch_metric_alarm.oauth_failure_alarm.alarm_name
  }
}