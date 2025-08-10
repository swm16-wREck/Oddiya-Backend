# AWS Resource Cleanup Module - Identify and remove unused resources

terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Purpose     = "ResourceCleanup"
      ManagedBy   = "Terraform"
      LastScanned = timestamp()
    }
  }
}

# Data source for current account
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# Lambda function for resource cleanup
resource "aws_lambda_function" "cleanup" {
  filename         = data.archive_file.cleanup_lambda.output_path
  function_name    = "${var.prefix}-resource-cleanup"
  role            = aws_iam_role.cleanup_lambda.arn
  handler         = "index.handler"
  runtime         = "python3.11"
  timeout         = 900  # 15 minutes
  memory_size     = 512
  
  environment {
    variables = {
      DRY_RUN            = var.dry_run ? "true" : "false"
      SNS_TOPIC_ARN      = aws_sns_topic.cleanup_notifications.arn
      REGION             = var.aws_region
      AGE_THRESHOLD_DAYS = var.resource_age_threshold_days
      
      # Feature flags for what to clean
      CLEAN_EBS_VOLUMES       = var.cleanup_config.ebs_volumes ? "true" : "false"
      CLEAN_EBS_SNAPSHOTS     = var.cleanup_config.ebs_snapshots ? "true" : "false"
      CLEAN_ELASTIC_IPS       = var.cleanup_config.elastic_ips ? "true" : "false"
      CLEAN_LOAD_BALANCERS    = var.cleanup_config.load_balancers ? "true" : "false"
      CLEAN_NAT_GATEWAYS      = var.cleanup_config.nat_gateways ? "true" : "false"
      CLEAN_RDS_SNAPSHOTS     = var.cleanup_config.rds_snapshots ? "true" : "false"
      CLEAN_AMI_IMAGES        = var.cleanup_config.ami_images ? "true" : "false"
      CLEAN_ECR_IMAGES        = var.cleanup_config.ecr_images ? "true" : "false"
      CLEAN_CLOUDWATCH_LOGS   = var.cleanup_config.cloudwatch_logs ? "true" : "false"
      CLEAN_S3_BUCKETS        = var.cleanup_config.s3_buckets ? "true" : "false"
      CLEAN_SECURITY_GROUPS   = var.cleanup_config.security_groups ? "true" : "false"
      CLEAN_LAMBDA_FUNCTIONS  = var.cleanup_config.old_lambda_versions ? "true" : "false"
      
      # Whitelist patterns (comma-separated)
      WHITELIST_TAGS     = join(",", var.whitelist_tags)
      WHITELIST_PREFIXES = join(",", var.whitelist_name_prefixes)
    }
  }
  
  tags = {
    Name = "${var.prefix}-cleanup-lambda"
  }
}

# Lambda deployment package
data "archive_file" "cleanup_lambda" {
  type        = "zip"
  output_path = "${path.module}/cleanup_lambda.zip"
  
  source {
    content  = file("${path.module}/cleanup.py")
    filename = "index.py"
  }
}

# IAM role for Lambda
resource "aws_iam_role" "cleanup_lambda" {
  name = "${var.prefix}-cleanup-lambda-role"
  
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
}

# IAM policy for Lambda - comprehensive permissions for cleanup
resource "aws_iam_role_policy" "cleanup_lambda" {
  name = "${var.prefix}-cleanup-policy"
  role = aws_iam_role.cleanup_lambda.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Sid    = "SNSPublish"
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = aws_sns_topic.cleanup_notifications.arn
      },
      {
        Sid    = "EC2Cleanup"
        Effect = "Allow"
        Action = [
          "ec2:DescribeVolumes",
          "ec2:DeleteVolume",
          "ec2:DescribeSnapshots",
          "ec2:DeleteSnapshot",
          "ec2:DescribeAddresses",
          "ec2:ReleaseAddress",
          "ec2:DescribeInstances",
          "ec2:DescribeImages",
          "ec2:DeregisterImage",
          "ec2:DescribeSecurityGroups",
          "ec2:DeleteSecurityGroup",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DescribeNatGateways",
          "ec2:DeleteNatGateway",
          "ec2:DescribeTags"
        ]
        Resource = "*"
      },
      {
        Sid    = "ELBCleanup"
        Effect = "Allow"
        Action = [
          "elasticloadbalancing:DescribeLoadBalancers",
          "elasticloadbalancing:DeleteLoadBalancer",
          "elasticloadbalancing:DescribeTargetGroups",
          "elasticloadbalancing:DeleteTargetGroup",
          "elasticloadbalancing:DescribeTags"
        ]
        Resource = "*"
      },
      {
        Sid    = "RDSCleanup"
        Effect = "Allow"
        Action = [
          "rds:DescribeDBSnapshots",
          "rds:DeleteDBSnapshot",
          "rds:DescribeDBClusterSnapshots",
          "rds:DeleteDBClusterSnapshot",
          "rds:ListTagsForResource"
        ]
        Resource = "*"
      },
      {
        Sid    = "S3Cleanup"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketLocation",
          "s3:GetBucketTagging",
          "s3:GetBucketVersioning",
          "s3:GetLifecycleConfiguration",
          "s3:PutLifecycleConfiguration",
          "s3:DeleteBucket",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion",
          "s3:ListBucketVersions"
        ]
        Resource = "*"
      },
      {
        Sid    = "ECRCleanup"
        Effect = "Allow"
        Action = [
          "ecr:DescribeRepositories",
          "ecr:ListImages",
          "ecr:BatchDeleteImage",
          "ecr:GetRepositoryPolicy",
          "ecr:ListTagsForResource"
        ]
        Resource = "*"
      },
      {
        Sid    = "LambdaCleanup"
        Effect = "Allow"
        Action = [
          "lambda:ListFunctions",
          "lambda:ListVersionsByFunction",
          "lambda:DeleteFunction",
          "lambda:GetFunction",
          "lambda:ListTags"
        ]
        Resource = "*"
      },
      {
        Sid    = "CloudWatchCleanup"
        Effect = "Allow"
        Action = [
          "logs:DescribeLogGroups",
          "logs:DeleteLogGroup",
          "logs:ListTagsLogGroup"
        ]
        Resource = "*"
      }
    ]
  })
}

# SNS Topic for notifications
resource "aws_sns_topic" "cleanup_notifications" {
  name = "${var.prefix}-cleanup-notifications"
  
  tags = {
    Name = "${var.prefix}-cleanup-notifications"
  }
}

# SNS Topic subscription
resource "aws_sns_topic_subscription" "cleanup_email" {
  count     = length(var.notification_emails)
  topic_arn = aws_sns_topic.cleanup_notifications.arn
  protocol  = "email"
  endpoint  = var.notification_emails[count.index]
}

# EventBridge rule for scheduled cleanup
resource "aws_cloudwatch_event_rule" "cleanup_schedule" {
  name                = "${var.prefix}-cleanup-schedule"
  description         = "Trigger resource cleanup scan"
  schedule_expression = var.schedule_expression
  
  tags = {
    Name = "${var.prefix}-cleanup-schedule"
  }
}

# EventBridge target
resource "aws_cloudwatch_event_target" "cleanup_lambda" {
  rule      = aws_cloudwatch_event_rule.cleanup_schedule.name
  target_id = "CleanupLambda"
  arn       = aws_lambda_function.cleanup.arn
}

# Lambda permission for EventBridge
resource "aws_lambda_permission" "allow_eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cleanup.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.cleanup_schedule.arn
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "cleanup_logs" {
  name              = "/aws/lambda/${aws_lambda_function.cleanup.function_name}"
  retention_in_days = 30
  
  tags = {
    Name = "${var.prefix}-cleanup-logs"
  }
}

# CloudWatch Dashboard for cleanup monitoring
resource "aws_cloudwatch_dashboard" "cleanup_dashboard" {
  dashboard_name = "${var.prefix}-cleanup-dashboard"
  
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "text"
        properties = {
          markdown = <<-EOT
            # AWS Resource Cleanup Dashboard
            
            ## Configuration
            - **Dry Run Mode**: ${var.dry_run ? "ENABLED" : "DISABLED"}
            - **Schedule**: ${var.schedule_expression}
            - **Age Threshold**: ${var.resource_age_threshold_days} days
            - **Region**: ${var.aws_region}
            
            ## Cleanup Targets
            ${var.cleanup_config.ebs_volumes ? "✅" : "❌"} EBS Volumes
            ${var.cleanup_config.ebs_snapshots ? "✅" : "❌"} EBS Snapshots
            ${var.cleanup_config.elastic_ips ? "✅" : "❌"} Elastic IPs
            ${var.cleanup_config.load_balancers ? "✅" : "❌"} Load Balancers
            ${var.cleanup_config.nat_gateways ? "✅" : "❌"} NAT Gateways
            ${var.cleanup_config.rds_snapshots ? "✅" : "❌"} RDS Snapshots
            ${var.cleanup_config.ami_images ? "✅" : "❌"} AMI Images
            ${var.cleanup_config.ecr_images ? "✅" : "❌"} ECR Images
            ${var.cleanup_config.cloudwatch_logs ? "✅" : "❌"} CloudWatch Logs
            ${var.cleanup_config.s3_buckets ? "✅" : "❌"} Empty S3 Buckets
            ${var.cleanup_config.security_groups ? "✅" : "❌"} Unused Security Groups
            ${var.cleanup_config.old_lambda_versions ? "✅" : "❌"} Old Lambda Versions
          EOT
        }
      },
      {
        type = "log"
        properties = {
          query   = "SOURCE '${aws_cloudwatch_log_group.cleanup_logs.name}' | fields @timestamp, @message | filter @message like /CLEANUP_SUMMARY/ | sort @timestamp desc | limit 20"
          region  = var.aws_region
          title   = "Recent Cleanup Activities"
        }
      }
    ]
  })
}

# Output summary of what will be cleaned
output "cleanup_configuration" {
  value = {
    dry_run_mode = var.dry_run
    schedule     = var.schedule_expression
    lambda_arn   = aws_lambda_function.cleanup.arn
    sns_topic    = aws_sns_topic.cleanup_notifications.arn
    dashboard    = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.cleanup_dashboard.dashboard_name}"
    
    cleanup_targets = var.cleanup_config
    
    estimated_monthly_savings = {
      ebs_volumes      = "~$10-50 per unattached volume"
      elastic_ips      = "~$3.60 per unused EIP"
      nat_gateways     = "~$45 per unused NAT gateway"
      load_balancers   = "~$18-25 per unused ALB/NLB"
      rds_snapshots    = "~$0.095 per GB"
      s3_storage       = "~$0.023 per GB"
      cloudwatch_logs  = "~$0.50 per GB"
    }
  }
}