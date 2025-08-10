output "budget_id" {
  description = "ID of the AWS Budget"
  value       = aws_budgets_budget.monthly.id
}

output "budget_name" {
  description = "Name of the AWS Budget"
  value       = aws_budgets_budget.monthly.name
}

output "sns_topic_arn" {
  description = "ARN of the SNS topic for alerts"
  value       = aws_sns_topic.alerts.arn
}

output "dashboard_url" {
  description = "URL of the CloudWatch dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards:name=${aws_cloudwatch_dashboard.cost_optimization.dashboard_name}"
}

output "cost_optimizer_function_name" {
  description = "Name of the cost optimizer Lambda function"
  value       = aws_lambda_function.cost_optimizer.function_name
}

output "monthly_budget_limit" {
  description = "Monthly budget limit in USD"
  value       = var.budget_alert.monthly_limit_usd
}

output "alert_thresholds" {
  description = "Budget alert thresholds (percentages)"
  value       = var.budget_alert.alert_thresholds
}