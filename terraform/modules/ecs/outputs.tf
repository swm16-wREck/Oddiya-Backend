output "cluster_id" {
  description = "ECS cluster ID"
  value = aws_ecs_cluster.main.id
}

output "cluster_name" {
  description = "ECS cluster name"
  value = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  description = "ECS cluster ARN"
  value = aws_ecs_cluster.main.arn
}

output "service_name" {
  description = "ECS service name"
  value = aws_ecs_service.app.name
}

output "service_arn" {
  description = "ECS service ARN"
  value = aws_ecs_service.app.id
}

output "task_definition_arn" {
  description = "ECS task definition ARN"
  value = aws_ecs_task_definition.app.arn
}

output "log_group_name" {
  description = "CloudWatch log group name"
  value = aws_cloudwatch_log_group.app.name
}

output "service_discovery_namespace_id" {
  description = "Service discovery namespace ID"
  value = aws_service_discovery_private_dns_namespace.main.id
}

output "auto_scaling_target_arn" {
  description = "Auto scaling target ARN"
  value = aws_appautoscaling_target.ecs_target.arn
}
