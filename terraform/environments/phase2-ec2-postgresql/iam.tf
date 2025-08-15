# IAM Roles and Policies for EC2 PostgreSQL Infrastructure

# ==========================================
# Database Server IAM Role and Policies
# ==========================================

# IAM role for database EC2 instance
resource "aws_iam_role" "db_instance_role" {
  name = "${local.project_name}-db-instance-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-instance-role"
  })
}

# IAM policy for S3 backup access
resource "aws_iam_policy" "db_s3_backup_policy" {
  name        = "${local.project_name}-db-s3-backup-policy"
  description = "Policy for database S3 backup operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.db_backups.arn,
          "${aws_s3_bucket.db_backups.arn}/*"
        ]
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-s3-backup-policy"
  })
}

# IAM policy for CloudWatch monitoring
resource "aws_iam_policy" "db_cloudwatch_policy" {
  name        = "${local.project_name}-db-cloudwatch-policy"
  description = "Policy for database CloudWatch monitoring"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
          "ec2:DescribeVolumes",
          "ec2:DescribeTags",
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-cloudwatch-policy"
  })
}

# IAM policy for Systems Manager (SSM)
resource "aws_iam_policy" "db_ssm_policy" {
  name        = "${local.project_name}-db-ssm-policy"
  description = "Policy for Systems Manager access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:UpdateInstanceInformation",
          "ssmmessages:CreateControlChannel",
          "ssmmessages:CreateDataChannel",
          "ssmmessages:OpenControlChannel",
          "ssmmessages:OpenDataChannel",
          "ec2messages:AcknowledgeMessage",
          "ec2messages:DeleteMessage",
          "ec2messages:FailMessage",
          "ec2messages:GetEndpoint",
          "ec2messages:GetMessages",
          "ec2messages:SendReply"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-ssm-policy"
  })
}

# IAM policy for accessing Secrets Manager
resource "aws_iam_policy" "db_secrets_policy" {
  name        = "${local.project_name}-db-secrets-policy"
  description = "Policy for accessing database secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:UpdateSecret"
        ]
        Resource = [
          aws_secretsmanager_secret.db_credentials.arn
        ]
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-secrets-policy"
  })
}

# Attach policies to database instance role
resource "aws_iam_role_policy_attachment" "db_s3_backup" {
  role       = aws_iam_role.db_instance_role.name
  policy_arn = aws_iam_policy.db_s3_backup_policy.arn
}

resource "aws_iam_role_policy_attachment" "db_cloudwatch" {
  role       = aws_iam_role.db_instance_role.name
  policy_arn = aws_iam_policy.db_cloudwatch_policy.arn
}

resource "aws_iam_role_policy_attachment" "db_ssm" {
  role       = aws_iam_role.db_instance_role.name
  policy_arn = aws_iam_policy.db_ssm_policy.arn
}

resource "aws_iam_role_policy_attachment" "db_secrets" {
  role       = aws_iam_role.db_instance_role.name
  policy_arn = aws_iam_policy.db_secrets_policy.arn
}

# IAM instance profile for database EC2 instance
resource "aws_iam_instance_profile" "db_instance_profile" {
  name = "${local.project_name}-db-instance-profile"
  role = aws_iam_role.db_instance_role.name

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-instance-profile"
  })
}

# ==========================================
# Application Server IAM Role and Policies
# ==========================================

# IAM role for application servers (ECS tasks or EC2 instances)
resource "aws_iam_role" "app_instance_role" {
  name = "${local.project_name}-app-instance-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = ["ec2.amazonaws.com", "ecs-tasks.amazonaws.com"]
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-app-instance-role"
  })
}

# IAM policy for application to access database secrets
resource "aws_iam_policy" "app_secrets_policy" {
  name        = "${local.project_name}-app-secrets-policy"
  description = "Policy for application to access database secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.db_credentials.arn
        ]
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-app-secrets-policy"
  })
}

# IAM policy for application CloudWatch logging
resource "aws_iam_policy" "app_logging_policy" {
  name        = "${local.project_name}-app-logging-policy"
  description = "Policy for application CloudWatch logging"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/oddiya/*"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-app-logging-policy"
  })
}

# Attach policies to application role
resource "aws_iam_role_policy_attachment" "app_secrets" {
  role       = aws_iam_role.app_instance_role.name
  policy_arn = aws_iam_policy.app_secrets_policy.arn
}

resource "aws_iam_role_policy_attachment" "app_logging" {
  role       = aws_iam_role.app_instance_role.name
  policy_arn = aws_iam_policy.app_logging_policy.arn
}

# IAM instance profile for application servers
resource "aws_iam_instance_profile" "app_instance_profile" {
  name = "${local.project_name}-app-instance-profile"
  role = aws_iam_role.app_instance_role.name

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-app-instance-profile"
  })
}

# ==========================================
# Monitoring Server IAM Role and Policies
# ==========================================

# IAM role for monitoring server
resource "aws_iam_role" "monitoring_role" {
  count = var.enable_monitoring ? 1 : 0
  name  = "${local.project_name}-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-monitoring-role"
  })
}

# IAM policy for monitoring server
resource "aws_iam_policy" "monitoring_policy" {
  count       = var.enable_monitoring ? 1 : 0
  name        = "${local.project_name}-monitoring-policy"
  description = "Policy for monitoring server"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:ListMetrics",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:GetMetricData",
          "ec2:DescribeInstances",
          "ec2:DescribeInstanceStatus",
          "ec2:DescribeVolumes",
          "rds:DescribeDBInstances",
          "rds:DescribeDBClusters",
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-monitoring-policy"
  })
}

# Attach monitoring policy
resource "aws_iam_role_policy_attachment" "monitoring_policy_attach" {
  count      = var.enable_monitoring ? 1 : 0
  role       = aws_iam_role.monitoring_role[0].name
  policy_arn = aws_iam_policy.monitoring_policy[0].arn
}

# IAM instance profile for monitoring server
resource "aws_iam_instance_profile" "monitoring_instance_profile" {
  count = var.enable_monitoring ? 1 : 0
  name  = "${local.project_name}-monitoring-instance-profile"
  role  = aws_iam_role.monitoring_role[0].name

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-monitoring-instance-profile"
  })
}

# ==========================================
# Lambda Function IAM Role (for scheduled operations)
# ==========================================

# IAM role for Lambda functions
resource "aws_iam_role" "lambda_role" {
  count = var.enable_scheduled_scaling ? 1 : 0
  name  = "${local.project_name}-lambda-role"

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

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-lambda-role"
  })
}

# IAM policy for Lambda to manage EC2 instances
resource "aws_iam_policy" "lambda_ec2_policy" {
  count       = var.enable_scheduled_scaling ? 1 : 0
  name        = "${local.project_name}-lambda-ec2-policy"
  description = "Policy for Lambda to manage EC2 instances"

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
        Condition = {
          StringEquals = {
            "ec2:ResourceTag/Project" = "Oddiya"
          }
        }
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

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-lambda-ec2-policy"
  })
}

# Attach Lambda policies
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  count      = var.enable_scheduled_scaling ? 1 : 0
  role       = aws_iam_role.lambda_role[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "lambda_ec2" {
  count      = var.enable_scheduled_scaling ? 1 : 0
  role       = aws_iam_role.lambda_role[0].name
  policy_arn = aws_iam_policy.lambda_ec2_policy[0].arn
}