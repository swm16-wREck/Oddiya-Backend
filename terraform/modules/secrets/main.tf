resource "aws_secretsmanager_secret" "app_secrets" {
  name = "oddiya-${var.environment}-app-secrets"
  
  tags = {
    Name        = "oddiya-${var.environment}-app-secrets"
    Environment = var.environment
  }
}
