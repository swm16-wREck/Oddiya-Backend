output "dashboard_url" {
  value = "https://console.aws.amazon.com/cloudwatch/home?region=ap-northeast-2#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}
