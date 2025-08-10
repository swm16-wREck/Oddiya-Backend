output "autoscaling_group_name" {
  description = "Name of the Auto Scaling Group"
  value       = aws_autoscaling_group.main.name
}

output "autoscaling_group_arn" {
  description = "ARN of the Auto Scaling Group"
  value       = aws_autoscaling_group.main.arn
}

output "launch_template_id" {
  description = "ID of the launch template"
  value       = aws_launch_template.main.id
}

output "launch_template_latest_version" {
  description = "Latest version of the launch template"
  value       = aws_launch_template.main.latest_version
}

output "security_group_id" {
  description = "ID of the compute security group"
  value       = aws_security_group.compute.id
}

output "instance_ids" {
  description = "IDs of instances in the ASG"
  value       = aws_autoscaling_group.main.id
}

output "spot_savings_estimate" {
  description = "Estimated monthly savings from spot instances"
  value       = var.use_spot_instances ? "70-90% off on-demand pricing" : "Using on-demand pricing"
}

output "graviton_savings_estimate" {
  description = "Estimated savings from Graviton instances"
  value       = var.use_graviton ? "20% off x86 pricing" : "Using x86 instances"
}