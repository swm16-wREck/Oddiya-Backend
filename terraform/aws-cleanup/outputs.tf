output "lambda_function_arn" {
  description = "ARN of the cleanup Lambda function"
  value       = aws_lambda_function.cleanup.arn
}

output "lambda_function_name" {
  description = "Name of the cleanup Lambda function"
  value       = aws_lambda_function.cleanup.function_name
}

output "sns_topic_arn" {
  description = "ARN of the SNS topic for notifications"
  value       = aws_sns_topic.cleanup_notifications.arn
}

output "schedule_rule_arn" {
  description = "ARN of the EventBridge schedule rule"
  value       = aws_cloudwatch_event_rule.cleanup_schedule.arn
}

output "dashboard_url" {
  description = "URL to the CloudWatch dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.cleanup_dashboard.dashboard_name}"
}

output "log_group_name" {
  description = "CloudWatch log group name for cleanup logs"
  value       = aws_cloudwatch_log_group.cleanup_logs.name
}

output "configuration_summary" {
  description = "Summary of cleanup configuration"
  value = {
    dry_run_mode         = var.dry_run
    schedule             = var.schedule_expression
    age_threshold_days   = var.resource_age_threshold_days
    cleanup_targets      = var.cleanup_config
    whitelist_tags       = var.whitelist_tags
    whitelist_prefixes   = var.whitelist_name_prefixes
  }
}

output "manual_invocation_command" {
  description = "AWS CLI command to manually trigger cleanup"
  value       = "aws lambda invoke --function-name ${aws_lambda_function.cleanup.function_name} --region ${var.aws_region} /tmp/cleanup-output.json"
}