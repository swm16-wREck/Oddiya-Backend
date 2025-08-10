output "redis_endpoint" {
  description = "Redis primary endpoint"
  value = var.deployment_mode == "single" ? 
    (length(aws_elasticache_cluster.single_node) > 0 ? aws_elasticache_cluster.single_node[0].cache_nodes[0].address : "") :
    (length(aws_elasticache_replication_group.redis) > 0 ? aws_elasticache_replication_group.redis[0].primary_endpoint_address : "")
}

output "redis_port" {
  description = "Redis port"
  value       = var.redis_port
}

output "redis_configuration_endpoint" {
  description = "Redis configuration endpoint (cluster mode only)"
  value = var.deployment_mode == "cluster" && length(aws_elasticache_replication_group.redis) > 0 ? 
    aws_elasticache_replication_group.redis[0].configuration_endpoint_address : 
    null
}

output "redis_reader_endpoint" {
  description = "Redis reader endpoint (cluster mode only)"
  value = var.deployment_mode == "cluster" && length(aws_elasticache_replication_group.redis) > 0 ? 
    aws_elasticache_replication_group.redis[0].reader_endpoint_address : 
    null
}

output "security_group_id" {
  description = "Security group ID for ElastiCache"
  value       = aws_security_group.elasticache.id
}

output "auth_token_ssm_parameter" {
  description = "SSM parameter name for auth token (if encryption enabled)"
  value       = var.enable_encryption && length(aws_ssm_parameter.redis_auth_token) > 0 ? aws_ssm_parameter.redis_auth_token[0].name : null
}

output "connection_string" {
  description = "Redis connection string"
  value = format("redis%s://%s:%d",
    var.enable_encryption ? "s" : "",
    var.deployment_mode == "single" ? 
      (length(aws_elasticache_cluster.single_node) > 0 ? aws_elasticache_cluster.single_node[0].cache_nodes[0].address : "") :
      (length(aws_elasticache_replication_group.redis) > 0 ? aws_elasticache_replication_group.redis[0].primary_endpoint_address : ""),
    var.redis_port
  )
}

output "estimated_monthly_cost" {
  description = "Estimated monthly cost"
  value = {
    single_node = var.deployment_mode == "single" ? 
      "~$12-25/month (t4g.micro)" : 
      "N/A"
    cluster = var.deployment_mode == "cluster" ? 
      format("~$%d-$%d/month (%d nodes)", var.num_cache_nodes * 12, var.num_cache_nodes * 25, var.num_cache_nodes) : 
      "N/A"
  }
}