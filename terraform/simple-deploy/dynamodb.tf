# DynamoDB Tables for Oddiya Application

# Users table
resource "aws_dynamodb_table" "oddiya_users" {
  name           = "oddiya_users"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "email"
    type = "S"
  }
  
  attribute {
    name = "username"
    type = "S"
  }
  
  global_secondary_index {
    name            = "email-index"
    hash_key        = "email"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "username-index"
    hash_key        = "username"
    projection_type = "ALL"
  }
  
  tags = {
    Name        = "oddiya-users"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Activities table
resource "aws_dynamodb_table" "oddiya_activities" {
  name           = "oddiya_activities"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  range_key      = "created_at"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "created_at"
    type = "S"
  }
  
  attribute {
    name = "user_id"
    type = "S"
  }
  
  attribute {
    name = "category"
    type = "S"
  }
  
  global_secondary_index {
    name            = "user-activities-index"
    hash_key        = "user_id"
    range_key       = "created_at"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "category-index"
    hash_key        = "category"
    range_key       = "created_at"
    projection_type = "ALL"
  }
  
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
  
  tags = {
    Name        = "oddiya-activities"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Reviews table
resource "aws_dynamodb_table" "oddiya_reviews" {
  name           = "oddiya_reviews"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  range_key      = "created_at"
  
  attribute {
    name = "id"
    type = "S"
  }
  
  attribute {
    name = "created_at"
    type = "S"
  }
  
  attribute {
    name = "activity_id"
    type = "S"
  }
  
  attribute {
    name = "user_id"
    type = "S"
  }
  
  global_secondary_index {
    name            = "activity-reviews-index"
    hash_key        = "activity_id"
    range_key       = "created_at"
    projection_type = "ALL"
  }
  
  global_secondary_index {
    name            = "user-reviews-index"
    hash_key        = "user_id"
    range_key       = "created_at"
    projection_type = "ALL"
  }
  
  tags = {
    Name        = "oddiya-reviews"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Session/Cache table (if not using Redis)
resource "aws_dynamodb_table" "oddiya_sessions" {
  name           = "oddiya_sessions"
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
    Name        = "oddiya-sessions"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Bookmarks table
resource "aws_dynamodb_table" "oddiya_bookmarks" {
  name           = "oddiya_bookmarks"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "user_id"
  range_key      = "activity_id"
  
  attribute {
    name = "user_id"
    type = "S"
  }
  
  attribute {
    name = "activity_id"
    type = "S"
  }
  
  attribute {
    name = "created_at"
    type = "S"
  }
  
  global_secondary_index {
    name            = "user-bookmarks-by-date-index"
    hash_key        = "user_id"
    range_key       = "created_at"
    projection_type = "ALL"
  }
  
  tags = {
    Name        = "oddiya-bookmarks"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Output table names
output "dynamodb_tables" {
  value = {
    users     = aws_dynamodb_table.oddiya_users.name
    activities = aws_dynamodb_table.oddiya_activities.name
    reviews   = aws_dynamodb_table.oddiya_reviews.name
    sessions  = aws_dynamodb_table.oddiya_sessions.name
    bookmarks = aws_dynamodb_table.oddiya_bookmarks.name
  }
  description = "Names of the created DynamoDB tables"
}