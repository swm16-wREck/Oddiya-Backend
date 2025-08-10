resource "aws_sqs_queue" "video_queue" {
  name                       = "oddiya-${var.environment}-video-queue"
  visibility_timeout_seconds = 960
  message_retention_seconds  = 86400
  
  tags = {
    Name        = "oddiya-${var.environment}-video-queue"
    Environment = var.environment
  }
}

resource "aws_lambda_event_source_mapping" "sqs_lambda" {
  event_source_arn = aws_sqs_queue.video_queue.arn
  function_name    = var.lambda_arn
  batch_size       = 1
}
