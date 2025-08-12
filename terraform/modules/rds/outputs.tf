output "cluster_id" {
  value = aws_rds_cluster.main.id
}

output "cluster_endpoint" {
  value = aws_rds_cluster.main.endpoint
}

output "cluster_reader_endpoint" {
  value = aws_rds_cluster.main.reader_endpoint
}

output "database_name" {
  value = aws_rds_cluster.main.database_name
}

output "master_username" {
  value = aws_rds_cluster.main.master_username
}

output "password_secret_arn" {
  value = aws_secretsmanager_secret.db_password.arn
}

output "connection_secret_arn" {
  description = "ARN of the secret containing complete database connection information including PostGIS setup"
  value = aws_secretsmanager_secret.db_connection_string.arn
}

output "cluster_port" {
  value = aws_rds_cluster.main.port
}