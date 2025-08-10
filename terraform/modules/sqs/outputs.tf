output "video_queue_url" {
  value = aws_sqs_queue.video_queue.url
}

output "video_queue_arn" {
  value = aws_sqs_queue.video_queue.arn
}
