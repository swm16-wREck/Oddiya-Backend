output "configuration_endpoint" {
  value = aws_elasticache_replication_group.main.configuration_endpoint_address
}

output "primary_endpoint" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}
