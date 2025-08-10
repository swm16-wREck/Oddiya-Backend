output "main_bucket_id" {
  description = "ID of the main S3 bucket"
  value       = aws_s3_bucket.main.id
}

output "main_bucket_arn" {
  description = "ARN of the main S3 bucket"
  value       = aws_s3_bucket.main.arn
}

output "logs_bucket_id" {
  description = "ID of the logs S3 bucket"
  value       = aws_s3_bucket.logs.id
}

output "efs_id" {
  description = "ID of the EFS file system"
  value       = var.enable_efs ? aws_efs_file_system.shared[0].id : null
}

output "dlm_policy_id" {
  description = "ID of the DLM lifecycle policy"
  value       = aws_dlm_lifecycle_policy.ebs_snapshots.id
}

output "storage_cost_optimizations" {
  description = "Applied storage cost optimizations"
  value = {
    s3_intelligent_tiering = var.s3_lifecycle_rules.enable_intelligent_tiering
    s3_lifecycle_enabled   = true
    ebs_gp3_volumes        = var.ebs_optimization.use_gp3
    efs_one_zone           = var.efs_single_az
    snapshot_lifecycle     = true
  }
}

output "estimated_monthly_savings" {
  description = "Estimated monthly storage savings"
  value = {
    s3_lifecycle     = "60-90% on aged data"
    intelligent_tier = "30-40% on frequently accessed data"
    gp3_vs_gp2       = "20% on EBS volumes"
    efs_one_zone     = "47% on shared storage"
  }
}