# Cost monitoring and alerting module

locals {
  # Budget configuration
  budget_name = "${var.name_prefix}-monthly-budget"
}

# AWS Budget for cost control
resource "aws_budgets_budget" "monthly" {
  name              = local.budget_name
  budget_type       = "COST"
  limit_amount      = tostring(var.budget_alert.monthly_limit_usd)
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = formatdate("YYYY-MM-01_00:00", timestamp())
  
  cost_filter {
    name   = "Service"
    values = var.monitored_services
  }
  
  # Cost types to include
  cost_types {
    include_credit             = false
    include_discount           = true
    include_other_subscription = true
    include_recurring          = true
    include_refund             = false
    include_subscription       = true
    include_support            = true
    include_tax                = true
    include_upfront            = true
    use_amortized              = false
    use_blended                = false
  }
  
  # Alert notifications
  dynamic "notification" {
    for_each = var.budget_alert.alert_thresholds
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = var.budget_alert.alert_emails
    }
  }
}

# Cost Explorer queries for optimization insights
resource "aws_ce_cost_category" "main" {
  name = "${var.name_prefix}-cost-categories"
  
  rule {
    value = "compute"
    rule {
      dimension {
        key           = "SERVICE"
        values        = ["Amazon Elastic Compute Cloud - Compute"]
        match_options = ["EQUALS"]
      }
    }
  }
  
  rule {
    value = "storage"
    rule {
      dimension {
        key           = "SERVICE"
        values        = ["Amazon Simple Storage Service", "EC2 - Other"]
        match_options = ["EQUALS"]
      }
    }
  }
  
  rule {
    value = "database"
    rule {
      dimension {
        key           = "SERVICE"
        values        = ["Amazon Relational Database Service", "Amazon DynamoDB"]
        match_options = ["EQUALS"]
      }
    }
  }
  
  rule {
    value = "network"
    rule {
      dimension {
        key           = "USAGE_TYPE_GROUP"
        values        = ["EC2: Data Transfer", "EC2: NAT Gateway"]
        match_options = ["CONTAINS"]
      }
    }
  }
  
  rule_version = "CostCategoryExpression.v1"
  
  default_value = "other"
}

# CloudWatch Dashboard for cost visibility
resource "aws_cloudwatch_dashboard" "cost_optimization" {
  dashboard_name = "${var.name_prefix}-cost-optimization"
  
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/Billing", "EstimatedCharges", { stat = "Maximum", label = "Total Estimated Charges" }]
          ]
          period = 86400
          stat   = "Maximum"
          region = "us-east-1"  # Billing metrics are only in us-east-1
          title  = "Daily Estimated Charges"
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/EC2", "CPUUtilization", { stat = "Average" }],
            [".", ".", { stat = "Maximum" }]
          ]
          period = 300
          stat   = "Average"
          region = var.region
          title  = "EC2 CPU Utilization"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/RDS", "DatabaseConnections", { stat = "Average" }],
            [".", "CPUUtilization", { stat = "Average" }]
          ]
          period = 300
          stat   = "Average"
          region = var.region
          title  = "RDS Metrics"
        }
      },
      {
        type = "text"
        properties = {
          markdown = <<-EOT
            # Cost Optimization Status
            
            ## Active Optimizations:
            - **Spot Instances**: 70-90% savings on compute
            - **Graviton**: 20% savings on compatible workloads
            - **Single NAT Gateway**: $90/month saved per avoided gateway
            - **S3 Intelligent Tiering**: 30-40% savings on storage
            - **Aurora Serverless**: Pay-per-request database
            - **Scheduled Stop/Start**: 60-75% savings on non-production
            
            ## Monthly Budget: $${var.budget_alert.monthly_limit_usd}
            
            ## Alert Thresholds: ${join(", ", formatlist("%d%%", var.budget_alert.alert_thresholds))}
          EOT
        }
      }
    ]
  })
}

# CloudWatch Alarms for resource optimization
resource "aws_cloudwatch_metric_alarm" "high_cpu" {
  alarm_name          = "${var.name_prefix}-high-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors EC2 cpu utilization for scaling decisions"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  dimensions = {
    AutoScalingGroupName = var.asg_name
  }
  
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "low_cpu" {
  alarm_name          = "${var.name_prefix}-low-cpu-utilization"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "10"
  alarm_description   = "This metric monitors EC2 cpu utilization for scale-in decisions"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  dimensions = {
    AutoScalingGroupName = var.asg_name
  }
  
  tags = var.tags
}

# SNS Topic for alerts
resource "aws_sns_topic" "alerts" {
  name_prefix = "${var.name_prefix}-cost-alerts-"
  
  tags = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = length(var.budget_alert.alert_emails)
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.budget_alert.alert_emails[count.index]
}

# Lambda for cost optimization recommendations
resource "aws_lambda_function" "cost_optimizer" {
  filename         = data.archive_file.optimizer_zip.output_path
  function_name    = "${var.name_prefix}-cost-optimizer"
  role            = aws_iam_role.optimizer.arn
  handler         = "index.handler"
  runtime         = "python3.11"
  timeout         = 300
  memory_size     = 256
  
  environment {
    variables = {
      SNS_TOPIC_ARN = aws_sns_topic.alerts.arn
      REGION        = var.region
    }
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-cost-optimizer"
  })
}

# Create Lambda deployment package for optimizer
data "archive_file" "optimizer_zip" {
  type        = "zip"
  output_path = "${path.module}/optimizer.zip"
  
  source {
    content  = file("${path.module}/optimizer.py")
    filename = "index.py"
  }
}

# IAM role for cost optimizer Lambda
resource "aws_iam_role" "optimizer" {
  name_prefix = "${var.name_prefix}-optimizer-"
  
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

resource "aws_iam_role_policy" "optimizer" {
  name_prefix = "${var.name_prefix}-optimizer-"
  role        = aws_iam_role.optimizer.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ce:GetCostAndUsage",
          "ce:GetReservationUtilization",
          "ce:GetSavingsPlansUtilization",
          "ce:GetRightsizingRecommendation",
          "ce:GetReservationPurchaseRecommendation",
          "ce:GetSavingsPlansPurchaseRecommendation",
          "compute-optimizer:GetEC2InstanceRecommendations",
          "compute-optimizer:GetAutoScalingGroupRecommendations",
          "compute-optimizer:GetEBSVolumeRecommendations",
          "compute-optimizer:GetLambdaFunctionRecommendations",
          "trustedadvisor:Describe*"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = aws_sns_topic.alerts.arn
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

# Schedule cost optimizer to run weekly
resource "aws_cloudwatch_event_rule" "optimizer_schedule" {
  name                = "${var.name_prefix}-optimizer-schedule"
  description         = "Run cost optimizer weekly"
  schedule_expression = "rate(7 days)"
  
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "optimizer" {
  rule      = aws_cloudwatch_event_rule.optimizer_schedule.name
  target_id = "CostOptimizerLambda"
  arn       = aws_lambda_function.cost_optimizer.arn
}

resource "aws_lambda_permission" "optimizer_schedule" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cost_optimizer.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.optimizer_schedule.arn
}