output "app_secrets_arn" {
  value = aws_secretsmanager_secret.app_secrets.arn
}
