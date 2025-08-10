output "video_processor_arn" {
  value = aws_lambda_function.video_processor.arn
}

output "video_processor_name" {
  value = aws_lambda_function.video_processor.function_name
}
