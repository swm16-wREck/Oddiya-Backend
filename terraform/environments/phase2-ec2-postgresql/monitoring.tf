# Monitoring Infrastructure for EC2 PostgreSQL

# ==========================================
# CloudWatch Log Groups
# ==========================================

resource "aws_cloudwatch_log_group" "postgresql" {
  name              = "/oddiya/postgresql"
  retention_in_days = 30

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-postgresql-logs"
  })
}

resource "aws_cloudwatch_log_group" "postgresql_backup" {
  name              = "/oddiya/postgresql-backup"
  retention_in_days = 7

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-postgresql-backup-logs"
  })
}

# ==========================================
# CloudWatch Alarms
# ==========================================

# Database CPU Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "db_cpu_high" {
  alarm_name          = "${local.project_name}-db-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This alarm monitors EC2 CPU utilization for PostgreSQL instance"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = aws_instance.postgresql.id
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-cpu-alarm"
  })
}

# Database Memory Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "db_memory_high" {
  alarm_name          = "${local.project_name}-db-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "mem_used_percent"
  namespace           = "Oddiya/PostgreSQL"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "This alarm monitors memory utilization for PostgreSQL instance"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = aws_instance.postgresql.id
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-memory-alarm"
  })
}

# Database Disk Space Alarm
resource "aws_cloudwatch_metric_alarm" "db_disk_space_high" {
  alarm_name          = "${local.project_name}-db-disk-space-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "used_percent"
  namespace           = "Oddiya/PostgreSQL"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This alarm monitors disk space utilization for PostgreSQL data volume"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = aws_instance.postgresql.id
    device     = "/dev/nvme1n1"
    fstype     = "ext4"
    path       = "/var/lib/pgsql/15/data"
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-disk-alarm"
  })
}

# Instance Status Check Alarm
resource "aws_cloudwatch_metric_alarm" "db_status_check" {
  alarm_name          = "${local.project_name}-db-status-check-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = "60"
  statistic           = "Maximum"
  threshold           = "0"
  alarm_description   = "This alarm monitors EC2 instance status checks"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = aws_instance.postgresql.id
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-status-check-alarm"
  })
}

# ==========================================
# SNS Topic and Subscriptions for Alerts
# ==========================================

resource "aws_sns_topic" "alerts" {
  name = "${local.project_name}-alerts"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-alerts"
  })
}

resource "aws_sns_topic_subscription" "email_alerts" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ==========================================
# Optional Monitoring Instance
# ==========================================

# Monitoring server instance
resource "aws_instance" "monitoring" {
  count                  = var.enable_monitoring ? 1 : 0
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.monitoring_instance_type
  key_name               = aws_key_pair.db_key.key_name
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.monitoring.id]

  iam_instance_profile = var.enable_monitoring ? aws_iam_instance_profile.monitoring_instance_profile[0].name : null

  user_data = base64encode(templatefile("${path.module}/monitoring_user_data.sh", {
    db_host     = aws_instance.postgresql.private_ip
    db_name     = var.db_name
    db_username = var.db_username
    aws_region  = var.aws_region
  }))

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-monitoring"
    Role = "Monitoring"
  })

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# ==========================================
# CloudWatch Dashboard
# ==========================================

resource "aws_cloudwatch_dashboard" "postgresql_dashboard" {
  dashboard_name = "${local.project_name}-postgresql-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.postgresql.id],
            ["Oddiya/PostgreSQL", "mem_used_percent", "InstanceId", aws_instance.postgresql.id],
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "CPU and Memory Utilization"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["Oddiya/PostgreSQL", "used_percent", "InstanceId", aws_instance.postgresql.id, "device", "/dev/nvme1n1", "fstype", "ext4", "path", "/var/lib/pgsql/15/data"],
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Disk Usage"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/EC2", "NetworkIn", "InstanceId", aws_instance.postgresql.id],
            [".", "NetworkOut", ".", "."],
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Network Traffic"
          period  = 300
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 18
        width  = 24
        height = 6

        properties = {
          query   = "SOURCE '/oddiya/postgresql' | fields @timestamp, @message | sort @timestamp desc | limit 50"
          region  = var.aws_region
          title   = "PostgreSQL Logs"
          view    = "table"
        }
      }
    ]
  })

  depends_on = [aws_cloudwatch_log_group.postgresql]
}

# ==========================================
# Custom CloudWatch Metrics (Optional)
# ==========================================

# Lambda function for custom PostgreSQL metrics
resource "aws_lambda_function" "postgresql_metrics" {
  count         = var.enable_custom_metrics ? 1 : 0
  filename      = "${path.module}/postgresql_metrics.zip"
  function_name = "${local.project_name}-postgresql-metrics"
  role          = aws_iam_role.lambda_metrics_role[0].arn
  handler       = "index.handler"
  runtime       = "python3.9"
  timeout       = 60

  environment {
    variables = {
      DB_HOST           = aws_instance.postgresql.private_ip
      DB_NAME           = var.db_name
      DB_SECRET_ARN     = aws_secretsmanager_secret.db_credentials.arn
      CLOUDWATCH_NAMESPACE = "Oddiya/PostgreSQL/Custom"
    }
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-postgresql-metrics"
  })
}

# EventBridge rule to trigger metrics collection
resource "aws_cloudwatch_event_rule" "metrics_schedule" {
  count               = var.enable_custom_metrics ? 1 : 0
  name                = "${local.project_name}-metrics-schedule"
  description         = "Trigger PostgreSQL metrics collection"
  schedule_expression = "rate(5 minutes)"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-metrics-schedule"
  })
}

resource "aws_cloudwatch_event_target" "lambda_target" {
  count     = var.enable_custom_metrics ? 1 : 0
  rule      = aws_cloudwatch_event_rule.metrics_schedule[0].name
  target_id = "PostgreSQLMetricsTarget"
  arn       = aws_lambda_function.postgresql_metrics[0].arn
}

resource "aws_lambda_permission" "allow_eventbridge" {
  count         = var.enable_custom_metrics ? 1 : 0
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.postgresql_metrics[0].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.metrics_schedule[0].arn
}

# IAM role for Lambda metrics function
resource "aws_iam_role" "lambda_metrics_role" {
  count = var.enable_custom_metrics ? 1 : 0
  name  = "${local.project_name}-lambda-metrics-role"

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
    Name = "${local.project_name}-lambda-metrics-role"
  })
}

# IAM policy for Lambda metrics function
resource "aws_iam_policy" "lambda_metrics_policy" {
  count       = var.enable_custom_metrics ? 1 : 0
  name        = "${local.project_name}-lambda-metrics-policy"
  description = "Policy for Lambda metrics function"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.db_credentials.arn
      },
      {
        Effect = "Allow"
        Action = [
          "ec2:DescribeNetworkInterfaces",
          "ec2:CreateNetworkInterface",
          "ec2:DeleteNetworkInterface"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-lambda-metrics-policy"
  })
}

resource "aws_iam_role_policy_attachment" "lambda_metrics_policy_attach" {
  count      = var.enable_custom_metrics ? 1 : 0
  role       = aws_iam_role.lambda_metrics_role[0].name
  policy_arn = aws_iam_policy.lambda_metrics_policy[0].arn
}