output "sessions_table_name" {
  value = aws_dynamodb_table.sessions.name
}

output "queues_table_name" {
  value = aws_dynamodb_table.queues.name
}
