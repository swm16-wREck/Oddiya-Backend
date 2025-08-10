resource "aws_s3_bucket" "static" {
  bucket = "oddiya-${var.environment}-static-assets"
}

resource "aws_s3_bucket" "media_input" {
  bucket = "oddiya-${var.environment}-media-input"
}

resource "aws_s3_bucket" "media_output" {
  bucket = "oddiya-${var.environment}-media-output"
}

resource "aws_s3_bucket" "backups" {
  bucket = "oddiya-${var.environment}-backups"
}

resource "aws_s3_bucket_versioning" "all" {
  for_each = {
    static = aws_s3_bucket.static.id
    input  = aws_s3_bucket.media_input.id
    output = aws_s3_bucket.media_output.id
    backup = aws_s3_bucket.backups.id
  }
  
  bucket = each.value
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "all" {
  for_each = {
    static = aws_s3_bucket.static.id
    input  = aws_s3_bucket.media_input.id
    output = aws_s3_bucket.media_output.id
    backup = aws_s3_bucket.backups.id
  }
  
  bucket = each.value
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
