output "static_bucket_name" {
  value = aws_s3_bucket.static.id
}

output "static_bucket_domain" {
  value = aws_s3_bucket.static.bucket_regional_domain_name
}

output "media_input_bucket_name" {
  value = aws_s3_bucket.media_input.id
}

output "media_output_bucket_name" {
  value = aws_s3_bucket.media_output.id
}

output "backup_bucket_name" {
  value = aws_s3_bucket.backups.id
}
