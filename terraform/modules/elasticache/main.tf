resource "aws_elasticache_subnet_group" "main" {
  name       = "oddiya-${var.environment}-redis-subnet"
  subnet_ids = var.data_subnet_ids
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "oddiya-${var.environment}-redis"
  description                = "Redis cluster for Oddiya"
  node_type                  = var.node_type
  parameter_group_name       = "default.redis7"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [var.redis_security_group_id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  automatic_failover_enabled = true
  num_cache_clusters         = var.num_cache_nodes
  
  tags = {
    Name        = "oddiya-${var.environment}-redis"
    Environment = var.environment
  }
}
