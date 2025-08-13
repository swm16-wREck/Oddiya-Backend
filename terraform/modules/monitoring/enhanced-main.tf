# Enhanced Monitoring and Observability for ECS
# Comprehensive CloudWatch dashboards, alarms, and log management

# Variables for dynamic configuration
variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "oddiya"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "ecs_cluster_name" {
  description = "ECS cluster name"
  type        = string
}

variable "ecs_service_name" {
  description = "ECS service name"
  type        = string
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix for metrics"
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Target Group ARN suffix for metrics"
  type        = string
}

variable "alert_email" {
  description = "Email address for alerts"
  type        = string
}

variable "slack_webhook_url" {
  description = "Slack webhook URL for notifications (optional)"
  type        = string
  default     = ""
  sensitive   = true
}

# SNS Topic for Alerts
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-${var.environment}-alerts"

  tags = {
    Name        = "${var.project_name}-${var.environment}-alerts"
    Environment = var.environment
    Purpose     = "monitoring-alerts"
  }
}

resource "aws_sns_topic_subscription" "email_alerts" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Lambda function for Slack notifications (if webhook provided)
resource "aws_lambda_function" "slack_notifier" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  filename      = "slack_notifier.zip"
  function_name = "${var.project_name}-${var.environment}-slack-notifier"
  role          = aws_iam_role.lambda_role[0].arn
  handler       = "index.handler"
  runtime       = "python3.9"
  timeout       = 30

  environment {
    variables = {
      SLACK_WEBHOOK_URL = var.slack_webhook_url
    }
  }

  depends_on = [data.archive_file.slack_notifier_zip[0]]

  tags = {
    Name        = "${var.project_name}-slack-notifier"
    Environment = var.environment
  }
}

# Create Lambda deployment package
data "archive_file" "slack_notifier_zip" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  type        = "zip"
  output_path = "slack_notifier.zip"
  source {
    content = <<EOF
import json
import urllib3
import os

def handler(event, context):
    webhook_url = os.environ.get('SLACK_WEBHOOK_URL')
    if not webhook_url:
        return {'statusCode': 200}
    
    # Parse SNS message
    message = json.loads(event['Records'][0]['Sns']['Message'])
    
    # Format Slack message
    slack_message = {
        'text': f"🚨 AWS Alert: {message.get('AlarmName', 'Unknown Alarm')}",
        'attachments': [{
            'color': 'danger' if message.get('NewStateValue') == 'ALARM' else 'good',
            'fields': [
                {'title': 'Alarm', 'value': message.get('AlarmName', 'N/A'), 'short': True},
                {'title': 'State', 'value': message.get('NewStateValue', 'N/A'), 'short': True},
                {'title': 'Reason', 'value': message.get('NewStateReason', 'N/A'), 'short': False},
                {'title': 'Region', 'value': message.get('Region', 'N/A'), 'short': True},
                {'title': 'Time', 'value': message.get('StateChangeTime', 'N/A'), 'short': True}
            ]
        }]
    }
    
    http = urllib3.PoolManager()
    response = http.request(
        'POST',
        webhook_url,
        body=json.dumps(slack_message).encode('utf-8'),
        headers={'Content-Type': 'application/json'}
    )
    
    return {'statusCode': 200}
EOF
    filename = "index.py"
  }
}

# IAM Role for Lambda
resource "aws_iam_role" "lambda_role" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  name = "${var.project_name}-${var.environment}-lambda-role"

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

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  role       = aws_iam_role.lambda_role[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# SNS subscription for Slack Lambda
resource "aws_sns_topic_subscription" "slack_alerts" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.slack_notifier[0].arn
}

# Lambda permission for SNS
resource "aws_lambda_permission" "allow_sns" {
  count = var.slack_webhook_url != "" ? 1 : 0
  
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.slack_notifier[0].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.alerts.arn
}

# Enhanced CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-${var.environment}-monitoring"

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
            ["AWS/ECS", "CPUUtilization", "ServiceName", var.ecs_service_name, "ClusterName", var.ecs_cluster_name],
            [".", "MemoryUtilization", ".", ".", ".", "."],
          ]
          view    = "timeSeries"
          stacked = false
          region  = data.aws_region.current.name
          title   = "ECS Service Resources"
          period  = 300
          stat    = "Average"
          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", var.target_group_arn_suffix],
            [".", "RequestCount", ".", "."],
            [".", "HTTPCode_Target_2XX_Count", ".", "."],
            [".", "HTTPCode_Target_4XX_Count", ".", "."],
            [".", "HTTPCode_Target_5XX_Count", ".", "."],
          ]
          view   = "timeSeries"
          region = data.aws_region.current.name
          title  = "Application Load Balancer Metrics"
          period = 300
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 8
        height = 6

        properties = {
          metrics = [
            ["AWS/ECS", "ServiceCount", "ServiceName", var.ecs_service_name, "ClusterName", var.ecs_cluster_name],
            [".", "RunningTaskCount", ".", ".", ".", "."],
            [".", "PendingTaskCount", ".", ".", ".", "."],
          ]
          view   = "timeSeries"
          region = data.aws_region.current.name
          title  = "ECS Service Tasks"
          period = 300
          stat   = "Average"
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 6
        width  = 8
        height = 6

        properties = {
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", var.target_group_arn_suffix],
            [".", "UnHealthyHostCount", ".", "."],
          ]
          view   = "timeSeries"
          region = data.aws_region.current.name
          title  = "Target Health"
          period = 300
          stat   = "Average"
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 6
        width  = 8
        height = 6

        properties = {
          metrics = [
            ["AWS/ApplicationELB", "ActiveConnectionCount", "LoadBalancer", var.alb_arn_suffix],
            [".", "NewConnectionCount", ".", "."],
          ]
          view   = "timeSeries"
          region = data.aws_region.current.name
          title  = "ALB Connections"
          period = 300
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 12
        width  = 24
        height = 6

        properties = {
          query   = "SOURCE '/ecs/${var.project_name}-${var.environment}' | fields @timestamp, @message | sort @timestamp desc | limit 100"
          region  = data.aws_region.current.name
          title   = "Recent Application Logs"
        }
      }
    ]
  })
}

# Critical Alarms

# High CPU Utilization
resource "aws_cloudwatch_metric_alarm" "high_cpu" {
  alarm_name          = "${var.project_name}-${var.environment}-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors ECS service CPU utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    ServiceName = var.ecs_service_name
    ClusterName = var.ecs_cluster_name
  }

  tags = {
    Name        = "${var.project_name}-high-cpu-alarm"
    Environment = var.environment
    Severity    = "High"
  }
}

# High Memory Utilization
resource "aws_cloudwatch_metric_alarm" "high_memory" {
  alarm_name          = "${var.project_name}-${var.environment}-high-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "This metric monitors ECS service memory utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    ServiceName = var.ecs_service_name
    ClusterName = var.ecs_cluster_name
  }

  tags = {
    Name        = "${var.project_name}-high-memory-alarm"
    Environment = var.environment
    Severity    = "High"
  }
}

# High Response Time
resource "aws_cloudwatch_metric_alarm" "high_response_time" {
  alarm_name          = "${var.project_name}-${var.environment}-high-response-time"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Average"
  threshold           = "5"
  alarm_description   = "This metric monitors application response time"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    TargetGroup = var.target_group_arn_suffix
  }

  tags = {
    Name        = "${var.project_name}-high-response-time-alarm"
    Environment = var.environment
    Severity    = "Medium"
  }
}

# 5XX Error Rate
resource "aws_cloudwatch_metric_alarm" "high_5xx_errors" {
  alarm_name          = "${var.project_name}-${var.environment}-high-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "This metric monitors 5XX error rate"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    TargetGroup = var.target_group_arn_suffix
  }

  tags = {
    Name        = "${var.project_name}-high-5xx-errors-alarm"
    Environment = var.environment
    Severity    = "Critical"
  }
}

# Unhealthy Hosts
resource "aws_cloudwatch_metric_alarm" "unhealthy_hosts" {
  alarm_name          = "${var.project_name}-${var.environment}-unhealthy-hosts"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Average"
  threshold           = "0"
  alarm_description   = "This metric monitors unhealthy target hosts"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    TargetGroup = var.target_group_arn_suffix
  }

  tags = {
    Name        = "${var.project_name}-unhealthy-hosts-alarm"
    Environment = var.environment
    Severity    = "Critical"
  }
}

# ECS Service Running Task Count
resource "aws_cloudwatch_metric_alarm" "ecs_running_tasks_low" {
  alarm_name          = "${var.project_name}-${var.environment}-low-running-tasks"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "RunningTaskCount"
  namespace           = "AWS/ECS"
  period              = "300"
  statistic           = "Average"
  threshold           = "1"
  alarm_description   = "This metric monitors ECS service running task count"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    ServiceName = var.ecs_service_name
    ClusterName = var.ecs_cluster_name
  }

  tags = {
    Name        = "${var.project_name}-low-running-tasks-alarm"
    Environment = var.environment
    Severity    = "Critical"
  }
}

# Deployment Timeout Detection (Pending Tasks)
resource "aws_cloudwatch_metric_alarm" "deployment_timeout" {
  alarm_name          = "${var.project_name}-${var.environment}-deployment-timeout"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "6"  # 30 minutes
  metric_name         = "PendingTaskCount"
  namespace           = "AWS/ECS"
  period              = "300"
  statistic           = "Average"
  threshold           = "0"
  alarm_description   = "This alarm detects deployment timeouts (tasks pending for >30min)"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ServiceName = var.ecs_service_name
    ClusterName = var.ecs_cluster_name
  }

  tags = {
    Name        = "${var.project_name}-deployment-timeout-alarm"
    Environment = var.environment
    Severity    = "Critical"
    Purpose     = "deployment-monitoring"
  }
}

# Log-based Error Detection
resource "aws_cloudwatch_log_metric_filter" "application_errors" {
  name           = "${var.project_name}-${var.environment}-application-errors"
  log_group_name = "/ecs/${var.project_name}-${var.environment}"
  pattern        = "[timestamp, requestId, level=ERROR, ...]"

  metric_transformation {
    name      = "ApplicationErrors"
    namespace = "Custom/${var.project_name}"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "application_error_rate" {
  alarm_name          = "${var.project_name}-${var.environment}-application-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ApplicationErrors"
  namespace           = "Custom/${var.project_name}"
  period              = "300"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "This alarm detects high application error rates in logs"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"

  tags = {
    Name        = "${var.project_name}-application-error-rate-alarm"
    Environment = var.environment
    Severity    = "Medium"
  }
}

# JVM Memory Pressure Detection
resource "aws_cloudwatch_log_metric_filter" "jvm_memory_pressure" {
  name           = "${var.project_name}-${var.environment}-jvm-memory-pressure"
  log_group_name = "/ecs/${var.project_name}-${var.environment}"
  pattern        = "[..., message=\"*OutOfMemoryError*\" || message=\"*GC overhead*\" || message=\"*heap space*\"]"

  metric_transformation {
    name      = "JVMMemoryPressure"
    namespace = "Custom/${var.project_name}"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "jvm_memory_pressure" {
  alarm_name          = "${var.project_name}-${var.environment}-jvm-memory-pressure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "JVMMemoryPressure"
  namespace           = "Custom/${var.project_name}"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "This alarm detects JVM memory issues"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"

  tags = {
    Name        = "${var.project_name}-jvm-memory-pressure-alarm"
    Environment = var.environment
    Severity    = "High"
  }
}

# Data sources
data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# Outputs
output "dashboard_url" {
  value = "https://${data.aws_region.current.name}.console.aws.amazon.com/cloudwatch/home?region=${data.aws_region.current.name}#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}

output "sns_topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "monitoring_resources" {
  value = {
    dashboard_name = aws_cloudwatch_dashboard.main.dashboard_name
    sns_topic_arn  = aws_sns_topic.alerts.arn
    alarms_created = [
      aws_cloudwatch_metric_alarm.high_cpu.alarm_name,
      aws_cloudwatch_metric_alarm.high_memory.alarm_name,
      aws_cloudwatch_metric_alarm.high_response_time.alarm_name,
      aws_cloudwatch_metric_alarm.high_5xx_errors.alarm_name,
      aws_cloudwatch_metric_alarm.unhealthy_hosts.alarm_name,
      aws_cloudwatch_metric_alarm.ecs_running_tasks_low.alarm_name,
      aws_cloudwatch_metric_alarm.deployment_timeout.alarm_name,
      aws_cloudwatch_metric_alarm.application_error_rate.alarm_name,
      aws_cloudwatch_metric_alarm.jvm_memory_pressure.alarm_name
    ]
  }
}