resource "aws_dynamodb_table" "sessions" {
  name           = "oddiya-${var.environment}-sessions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "session_id"
  
  attribute {
    name = "session_id"
    type = "S"
  }
  
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-sessions"
    Environment = var.environment
  }
}

resource "aws_dynamodb_table" "queues" {
  name           = "oddiya-${var.environment}-queues"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "queue_id"
  
  attribute {
    name = "queue_id"
    type = "S"
  }
  
  tags = {
    Name        = "oddiya-${var.environment}-queues"
    Environment = var.environment
  }
}
