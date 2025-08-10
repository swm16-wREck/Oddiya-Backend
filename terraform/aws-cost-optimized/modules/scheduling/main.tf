# Scheduling module for automatic start/stop of resources

locals {
  # Parse schedule timezone
  schedule_timezone = var.schedule.timezone
}

# Lambda function for start/stop operations
resource "aws_lambda_function" "scheduler" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "${var.name_prefix}-resource-scheduler"
  role            = aws_iam_role.lambda.arn
  handler         = "index.handler"
  runtime         = "python3.11"
  timeout         = 60
  memory_size     = 128  # Minimal memory for cost savings
  
  environment {
    variables = {
      EC2_INSTANCES = jsonencode(var.target_resources.ec2_instances)
      RDS_INSTANCES = jsonencode(var.target_resources.rds_instances)
      ASG_NAMES     = jsonencode(var.target_resources.asg_names)
      ACTION        = "variable"  # Will be set by each rule
    }
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-scheduler"
  })
}

# Create Lambda deployment package
data "archive_file" "lambda_zip" {
  type        = "zip"
  output_path = "${path.module}/lambda_scheduler.zip"
  
  source {
    content  = file("${path.module}/scheduler.py")
    filename = "index.py"
  }
}

# Lambda IAM role
resource "aws_iam_role" "lambda" {
  name_prefix = "${var.name_prefix}-scheduler-"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
  
  tags = var.tags
}

# Lambda IAM policy
resource "aws_iam_role_policy" "lambda" {
  name_prefix = "${var.name_prefix}-scheduler-"
  role        = aws_iam_role.lambda.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:StartInstances",
          "ec2:StopInstances",
          "ec2:DescribeInstances",
          "ec2:DescribeInstanceStatus"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "rds:StartDBInstance",
          "rds:StopDBInstance",
          "rds:StartDBCluster",
          "rds:StopDBCluster",
          "rds:DescribeDBInstances",
          "rds:DescribeDBClusters"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "autoscaling:UpdateAutoScalingGroup",
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:SetDesiredCapacity"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# CloudWatch Event Rules for scheduling
resource "aws_cloudwatch_event_rule" "start" {
  name                = "${var.name_prefix}-start-resources"
  description         = "Start resources on schedule"
  schedule_expression = "cron(${var.schedule.start_cron})"
  
  tags = var.tags
}

resource "aws_cloudwatch_event_rule" "stop" {
  name                = "${var.name_prefix}-stop-resources"
  description         = "Stop resources on schedule"
  schedule_expression = "cron(${var.schedule.stop_cron})"
  
  tags = var.tags
}

# Event targets for start
resource "aws_cloudwatch_event_target" "start" {
  rule      = aws_cloudwatch_event_rule.start.name
  target_id = "StartLambda"
  arn       = aws_lambda_function.scheduler.arn
  
  input = jsonencode({
    action = "start"
  })
}

# Event targets for stop
resource "aws_cloudwatch_event_target" "stop" {
  rule      = aws_cloudwatch_event_rule.stop.name
  target_id = "StopLambda"
  arn       = aws_lambda_function.scheduler.arn
  
  input = jsonencode({
    action = "stop"
  })
}

# Lambda permissions for EventBridge
resource "aws_lambda_permission" "start" {
  statement_id  = "AllowExecutionFromEventBridgeStart"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scheduler.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.start.arn
}

resource "aws_lambda_permission" "stop" {
  statement_id  = "AllowExecutionFromEventBridgeStop"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scheduler.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.stop.arn
}

# CloudWatch Log Group for Lambda
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.scheduler.function_name}"
  retention_in_days = 7  # Minimal retention for cost savings
  
  tags = var.tags
}