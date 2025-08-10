resource "aws_iam_role" "lambda" {
  name = "oddiya-${var.environment}-lambda-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_lambda_function" "video_processor" {
  function_name = "oddiya-${var.environment}-video-processor"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 900
  memory_size   = 3008
  
  filename         = "${path.module}/lambda_placeholder.zip"
  source_code_hash = filebase64sha256("${path.module}/lambda_placeholder.zip")
  
  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }
  
  environment {
    variables = {
      S3_INPUT_BUCKET  = var.s3_input_bucket
      S3_OUTPUT_BUCKET = var.s3_output_bucket
    }
  }
}

# Create placeholder Lambda deployment package
resource "local_file" "lambda_code" {
  filename = "${path.module}/lambda_placeholder.py"
  content  = "def handler(event, context): return {'statusCode': 200}"
}

resource "null_resource" "lambda_zip" {
  provisioner "local-exec" {
    command = "cd ${path.module} && zip lambda_placeholder.zip lambda_placeholder.py"
  }
  depends_on = [local_file.lambda_code]
}
