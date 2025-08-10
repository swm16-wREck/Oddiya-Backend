output "lambda_function_name" {
  description = "Name of the scheduler Lambda function"
  value       = aws_lambda_function.scheduler.function_name
}

output "lambda_function_arn" {
  description = "ARN of the scheduler Lambda function"
  value       = aws_lambda_function.scheduler.arn
}

output "start_rule_arn" {
  description = "ARN of the start schedule rule"
  value       = aws_cloudwatch_event_rule.start.arn
}

output "stop_rule_arn" {
  description = "ARN of the stop schedule rule"
  value       = aws_cloudwatch_event_rule.stop.arn
}

output "schedule_details" {
  description = "Scheduling configuration details"
  value = {
    start_schedule = var.schedule.start_cron
    stop_schedule  = var.schedule.stop_cron
    timezone       = var.schedule.timezone
  }
}

output "estimated_savings" {
  description = "Estimated savings from scheduling"
  value       = "60-75% reduction in non-production resource costs"
}