#!/bin/bash

# Application Performance Monitoring (APM) Setup Script
# Configures comprehensive monitoring for ECS applications

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Icons
SETUP_ICON="🚀"
SUCCESS_ICON="✅"
ERROR_ICON="❌"
WARNING_ICON="⚠️"
INFO_ICON="ℹ️"
MONITOR_ICON="📊"

echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${BLUE}${BOLD}${SETUP_ICON} APM SETUP FOR ECS APPLICATION${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${CYAN}Project: $PROJECT_NAME${NC}"
echo -e "${CYAN}Environment: $ENVIRONMENT${NC}"
echo -e "${CYAN}Region: $REGION${NC}"
echo -e "${CYAN}Cluster: $CLUSTER_NAME${NC}"
echo -e "${CYAN}Service: $SERVICE_NAME${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo ""

# Check prerequisites
check_prerequisites() {
    echo -e "${BLUE}${BOLD}${INFO_ICON} Checking Prerequisites...${NC}"
    
    local errors=()
    
    if ! command -v aws &> /dev/null; then
        errors+=("AWS CLI is not installed")
    fi
    
    if ! aws sts get-caller-identity &> /dev/null; then
        errors+=("AWS credentials not configured")
    fi
    
    if ! command -v jq &> /dev/null; then
        errors+=("jq is not installed")
    fi
    
    if [ ${#errors[@]} -gt 0 ]; then
        echo -e "${RED}${BOLD}Prerequisites check failed:${NC}"
        for error in "${errors[@]}"; do
            echo -e "${RED}  ${ERROR_ICON} $error${NC}"
        done
        echo ""
        exit 1
    fi
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Prerequisites validated${NC}"
    echo ""
}

# Create comprehensive CloudWatch dashboard
create_dashboard() {
    echo -e "${BLUE}${BOLD}${MONITOR_ICON} Creating Comprehensive Dashboard...${NC}"
    
    local dashboard_name="${PROJECT_NAME}-${ENVIRONMENT}-apm"
    
    # Get ALB and Target Group ARNs
    local alb_arn_suffix=""
    local target_group_arn_suffix=""
    
    # Try to find ALB
    local alb_info
    alb_info=$(aws elbv2 describe-load-balancers \
        --region "$REGION" \
        --query "LoadBalancers[?contains(LoadBalancerName, '$PROJECT_NAME')].[LoadBalancerArn]" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$alb_info" ] && [ "$alb_info" != "None" ]; then
        alb_arn_suffix=$(echo "$alb_info" | cut -d'/' -f2-)
        
        # Get target group
        local target_group_info
        target_group_info=$(aws elbv2 describe-target-groups \
            --region "$REGION" \
            --query "TargetGroups[?contains(TargetGroupName, '$PROJECT_NAME')].[TargetGroupArn]" \
            --output text 2>/dev/null | head -1)
        
        if [ -n "$target_group_info" ] && [ "$target_group_info" != "None" ]; then
            target_group_arn_suffix=$(echo "$target_group_info" | cut -d'/' -f2-)
        fi
    fi
    
    # Create dashboard JSON
    local dashboard_body
    dashboard_body=$(cat << EOF
{
  "widgets": [
    {
      "type": "metric",
      "x": 0,
      "y": 0,
      "width": 12,
      "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ECS", "CPUUtilization", "ServiceName", "$SERVICE_NAME", "ClusterName", "$CLUSTER_NAME"],
          [".", "MemoryUtilization", ".", ".", ".", "."]
        ],
        "view": "timeSeries",
        "stacked": false,
        "region": "$REGION",
        "title": "ECS Resource Utilization",
        "period": 300,
        "stat": "Average",
        "yAxis": {
          "left": {
            "min": 0,
            "max": 100
          }
        }
      }
    },
    {
      "type": "metric",
      "x": 12,
      "y": 0,
      "width": 12,
      "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ECS", "ServiceCount", "ServiceName", "$SERVICE_NAME", "ClusterName", "$CLUSTER_NAME"],
          [".", "RunningTaskCount", ".", ".", ".", "."],
          [".", "PendingTaskCount", ".", ".", ".", "."]
        ],
        "view": "timeSeries",
        "region": "$REGION",
        "title": "ECS Service Health",
        "period": 300,
        "stat": "Average"
      }
    }
EOF
    )
    
    # Add ALB metrics if available
    if [ -n "$alb_arn_suffix" ]; then
        dashboard_body+=',
    {
      "type": "metric",
      "x": 0,
      "y": 6,
      "width": 12,
      "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", "'$alb_arn_suffix'"],
          [".", "TargetResponseTime", "TargetGroup", "'$target_group_arn_suffix'", {"yAxis": "right"}]
        ],
        "view": "timeSeries",
        "region": "'$REGION'",
        "title": "Request Volume and Response Time",
        "period": 300,
        "yAxis": {
          "left": {
            "min": 0
          },
          "right": {
            "min": 0
          }
        }
      }
    },
    {
      "type": "metric",
      "x": 12,
      "y": 6,
      "width": 12,
      "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "HTTPCode_Target_2XX_Count", "TargetGroup", "'$target_group_arn_suffix'"],
          [".", "HTTPCode_Target_4XX_Count", ".", "."],
          [".", "HTTPCode_Target_5XX_Count", ".", "."]
        ],
        "view": "timeSeries",
        "region": "'$REGION'",
        "title": "HTTP Response Codes",
        "period": 300,
        "stat": "Sum"
      }
    },
    {
      "type": "metric",
      "x": 0,
      "y": 12,
      "width": 12,
      "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", "'$target_group_arn_suffix'"],
          [".", "UnHealthyHostCount", ".", "."]
        ],
        "view": "timeSeries",
        "region": "'$REGION'",
        "title": "Target Health Status",
        "period": 300,
        "stat": "Average"
      }
    }'
    fi
    
    # Add log insights widget
    dashboard_body+=',
    {
      "type": "log",
      "x": 12,
      "y": 12,
      "width": 12,
      "height": 6,
      "properties": {
        "query": "SOURCE \"/ecs/'$PROJECT_NAME'-'$ENVIRONMENT'\" | fields @timestamp, @message\n| filter @message like /ERROR/\n| sort @timestamp desc\n| limit 20",
        "region": "'$REGION'",
        "title": "Recent Errors",
        "view": "table"
      }
    },
    {
      "type": "metric",
      "x": 0,
      "y": 18,
      "width": 24,
      "height": 6,
      "properties": {
        "metrics": [
          ["Custom/'$PROJECT_NAME'", "ApplicationErrors"],
          [".", "JVMMemoryPressure"],
          [".", "DeploymentEvents"]
        ],
        "view": "timeSeries",
        "region": "'$REGION'",
        "title": "Custom Application Metrics",
        "period": 300,
        "stat": "Sum"
      }
    }
  ]
}'
    
    # Create the dashboard
    aws cloudwatch put-dashboard \
        --dashboard-name "$dashboard_name" \
        --dashboard-body "$dashboard_body" \
        --region "$REGION" >/dev/null
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Dashboard created: $dashboard_name${NC}"
    echo -e "${CYAN}  URL: https://$REGION.console.aws.amazon.com/cloudwatch/home?region=$REGION#dashboards:name=$dashboard_name${NC}"
    echo ""
}

# Create CloudWatch alarms
create_alarms() {
    echo -e "${BLUE}${BOLD}${WARNING_ICON} Creating CloudWatch Alarms...${NC}"
    
    # SNS topic for alerts
    local sns_topic_arn
    sns_topic_arn=$(aws sns create-topic \
        --name "${PROJECT_NAME}-${ENVIRONMENT}-alerts" \
        --region "$REGION" \
        --query 'TopicArn' \
        --output text 2>/dev/null)
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created SNS topic: ${PROJECT_NAME}-${ENVIRONMENT}-alerts${NC}"
    
    # High CPU alarm
    aws cloudwatch put-metric-alarm \
        --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-high-cpu" \
        --alarm-description "High CPU utilization in ECS service" \
        --metric-name CPUUtilization \
        --namespace AWS/ECS \
        --statistic Average \
        --period 300 \
        --evaluation-periods 2 \
        --threshold 80 \
        --comparison-operator GreaterThanThreshold \
        --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
        --alarm-actions "$sns_topic_arn" \
        --ok-actions "$sns_topic_arn" \
        --region "$REGION" >/dev/null
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created high CPU alarm${NC}"
    
    # High Memory alarm
    aws cloudwatch put-metric-alarm \
        --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-high-memory" \
        --alarm-description "High memory utilization in ECS service" \
        --metric-name MemoryUtilization \
        --namespace AWS/ECS \
        --statistic Average \
        --period 300 \
        --evaluation-periods 2 \
        --threshold 85 \
        --comparison-operator GreaterThanThreshold \
        --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
        --alarm-actions "$sns_topic_arn" \
        --ok-actions "$sns_topic_arn" \
        --region "$REGION" >/dev/null
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created high memory alarm${NC}"
    
    # Low running tasks alarm
    aws cloudwatch put-metric-alarm \
        --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-low-running-tasks" \
        --alarm-description "Low number of running tasks" \
        --metric-name RunningTaskCount \
        --namespace AWS/ECS \
        --statistic Average \
        --period 300 \
        --evaluation-periods 2 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
        --alarm-actions "$sns_topic_arn" \
        --region "$REGION" >/dev/null
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created low running tasks alarm${NC}"
    
    # Deployment timeout alarm
    aws cloudwatch put-metric-alarm \
        --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-deployment-timeout" \
        --alarm-description "Deployment timeout - tasks pending too long" \
        --metric-name PendingTaskCount \
        --namespace AWS/ECS \
        --statistic Average \
        --period 300 \
        --evaluation-periods 6 \
        --threshold 0 \
        --comparison-operator GreaterThanThreshold \
        --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
        --alarm-actions "$sns_topic_arn" \
        --region "$REGION" >/dev/null
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created deployment timeout alarm (30min threshold)${NC}"
    
    # Find and create ALB alarms if ALB exists
    local target_group_arn
    target_group_arn=$(aws elbv2 describe-target-groups \
        --region "$REGION" \
        --query "TargetGroups[?contains(TargetGroupName, '$PROJECT_NAME')].[TargetGroupArn]" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$target_group_arn" ] && [ "$target_group_arn" != "None" ]; then
        local target_group_arn_suffix
        target_group_arn_suffix=$(echo "$target_group_arn" | cut -d'/' -f2-)
        
        # High response time alarm
        aws cloudwatch put-metric-alarm \
            --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-high-response-time" \
            --alarm-description "High response time" \
            --metric-name TargetResponseTime \
            --namespace AWS/ApplicationELB \
            --statistic Average \
            --period 300 \
            --evaluation-periods 3 \
            --threshold 5 \
            --comparison-operator GreaterThanThreshold \
            --dimensions Name=TargetGroup,Value="$target_group_arn_suffix" \
            --alarm-actions "$sns_topic_arn" \
            --ok-actions "$sns_topic_arn" \
            --region "$REGION" >/dev/null
        
        echo -e "${GREEN}  ${SUCCESS_ICON} Created high response time alarm${NC}"
        
        # High 5XX errors alarm
        aws cloudwatch put-metric-alarm \
            --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-high-5xx-errors" \
            --alarm-description "High 5XX error rate" \
            --metric-name HTTPCode_Target_5XX_Count \
            --namespace AWS/ApplicationELB \
            --statistic Sum \
            --period 300 \
            --evaluation-periods 2 \
            --threshold 10 \
            --comparison-operator GreaterThanThreshold \
            --dimensions Name=TargetGroup,Value="$target_group_arn_suffix" \
            --alarm-actions "$sns_topic_arn" \
            --region "$REGION" >/dev/null
        
        echo -e "${GREEN}  ${SUCCESS_ICON} Created high 5XX errors alarm${NC}"
        
        # Unhealthy hosts alarm
        aws cloudwatch put-metric-alarm \
            --alarm-name "${PROJECT_NAME}-${ENVIRONMENT}-unhealthy-hosts" \
            --alarm-description "Unhealthy target hosts" \
            --metric-name UnHealthyHostCount \
            --namespace AWS/ApplicationELB \
            --statistic Average \
            --period 300 \
            --evaluation-periods 2 \
            --threshold 0 \
            --comparison-operator GreaterThanThreshold \
            --dimensions Name=TargetGroup,Value="$target_group_arn_suffix" \
            --alarm-actions "$sns_topic_arn" \
            --ok-actions "$sns_topic_arn" \
            --region "$REGION" >/dev/null
        
        echo -e "${GREEN}  ${SUCCESS_ICON} Created unhealthy hosts alarm${NC}"
    fi
    
    echo -e "${CYAN}  SNS Topic ARN: $sns_topic_arn${NC}"
    echo ""
}

# Create log metric filters for custom metrics
create_log_metrics() {
    echo -e "${BLUE}${BOLD}${MONITOR_ICON} Creating Log Metric Filters...${NC}"
    
    local log_group="/ecs/${PROJECT_NAME}-${ENVIRONMENT}"
    
    # Application errors metric
    aws logs put-metric-filter \
        --log-group-name "$log_group" \
        --filter-name "${PROJECT_NAME}-${ENVIRONMENT}-application-errors" \
        --filter-pattern '[timestamp, requestId, level=ERROR, ...]' \
        --metric-transformations \
            metricName=ApplicationErrors,metricNamespace=Custom/"$PROJECT_NAME",metricValue=1 \
        --region "$REGION" 2>/dev/null || true
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created application errors metric filter${NC}"
    
    # JVM memory pressure metric
    aws logs put-metric-filter \
        --log-group-name "$log_group" \
        --filter-name "${PROJECT_NAME}-${ENVIRONMENT}-jvm-memory-pressure" \
        --filter-pattern '[..., message="*OutOfMemoryError*" || message="*GC overhead*" || message="*heap space*"]' \
        --metric-transformations \
            metricName=JVMMemoryPressure,metricNamespace=Custom/"$PROJECT_NAME",metricValue=1 \
        --region "$REGION" 2>/dev/null || true
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created JVM memory pressure metric filter${NC}"
    
    # Health check failures metric
    aws logs put-metric-filter \
        --log-group-name "$log_group" \
        --filter-name "${PROJECT_NAME}-${ENVIRONMENT}-health-check-failures" \
        --filter-pattern '[timestamp, requestId, level, logger, message="*health*", ..., status="failed" || status="timeout"]' \
        --metric-transformations \
            metricName=HealthCheckFailures,metricNamespace=Custom/"$PROJECT_NAME",metricValue=1 \
        --region "$REGION" 2>/dev/null || true
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created health check failures metric filter${NC}"
    
    # Deployment events metric
    aws logs put-metric-filter \
        --log-group-name "$log_group" \
        --filter-name "${PROJECT_NAME}-${ENVIRONMENT}-deployment-events" \
        --filter-pattern '[timestamp, requestId, level, logger, message="*Started*" || message="*Stopping*"]' \
        --metric-transformations \
            metricName=DeploymentEvents,metricNamespace=Custom/"$PROJECT_NAME",metricValue=1 \
        --region "$REGION" 2>/dev/null || true
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created deployment events metric filter${NC}"
    
    echo ""
}

# Create monitoring automation script
create_monitoring_script() {
    echo -e "${BLUE}${BOLD}${INFO_ICON} Creating Monitoring Automation Script...${NC}"
    
    cat > "/tmp/${PROJECT_NAME}-monitor-health.sh" << 'EOF'
#!/bin/bash

# Automated ECS Health Monitoring Script
# Runs periodic health checks and sends alerts

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"

# Check ECS service health
check_ecs_health() {
    local service_details
    service_details=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0]' \
        --output json 2>/dev/null)
    
    if [ -z "$service_details" ] || [ "$service_details" == "null" ]; then
        echo "ERROR: Service not found"
        return 1
    fi
    
    local desired running pending
    desired=$(echo "$service_details" | jq -r '.desiredCount')
    running=$(echo "$service_details" | jq -r '.runningCount')
    pending=$(echo "$service_details" | jq -r '.pendingCount')
    
    echo "Service Health: Desired=$desired, Running=$running, Pending=$pending"
    
    # Check if service is healthy
    if [ "$running" -lt "$desired" ]; then
        echo "WARNING: Service not at desired capacity"
        return 1
    fi
    
    if [ "$pending" -gt 0 ]; then
        local pending_duration
        pending_duration=$(aws ecs describe-services \
            --cluster "$CLUSTER_NAME" \
            --services "$SERVICE_NAME" \
            --region "$REGION" \
            --query 'services[0].deployments[0].createdAt' \
            --output text)
        
        local current_time
        current_time=$(date +%s)
        local created_time
        created_time=$(date -d "$pending_duration" +%s 2>/dev/null || echo "$current_time")
        local duration=$((current_time - created_time))
        
        if [ $duration -gt 1800 ]; then  # 30 minutes
            echo "ERROR: Tasks pending for too long (${duration}s)"
            return 1
        fi
    fi
    
    return 0
}

# Check application endpoint
check_app_endpoint() {
    # Try to find ALB endpoint
    local alb_dns
    alb_dns=$(aws elbv2 describe-load-balancers \
        --region "$REGION" \
        --query "LoadBalancers[?contains(LoadBalancerName, '$PROJECT_NAME')].DNSName" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$alb_dns" ] && [ "$alb_dns" != "None" ]; then
        local health_url="http://$alb_dns/actuator/health"
        local response
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$health_url" 2>/dev/null || echo "000")
        
        echo "Health endpoint check: $response"
        
        if [ "$response" != "200" ]; then
            echo "ERROR: Health endpoint returned $response"
            return 1
        fi
    else
        echo "INFO: No ALB found, skipping endpoint check"
    fi
    
    return 0
}

# Main health check
main() {
    echo "=== Health Check $(date) ==="
    
    local ecs_status=0
    local app_status=0
    
    check_ecs_health || ecs_status=1
    check_app_endpoint || app_status=1
    
    if [ $ecs_status -eq 0 ] && [ $app_status -eq 0 ]; then
        echo "RESULT: All checks passed"
        exit 0
    else
        echo "RESULT: Health check failed"
        exit 1
    fi
}

main "$@"
EOF
    
    chmod +x "/tmp/${PROJECT_NAME}-monitor-health.sh"
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Created monitoring script: /tmp/${PROJECT_NAME}-monitor-health.sh${NC}"
    echo -e "${CYAN}  Usage: /tmp/${PROJECT_NAME}-monitor-health.sh${NC}"
    echo ""
}

# Create CI/CD integration script
create_cicd_integration() {
    echo -e "${BLUE}${BOLD}${SETUP_ICON} Creating CI/CD Integration Scripts...${NC}"
    
    # Pre-deployment check script
    cat > "/tmp/${PROJECT_NAME}-pre-deploy-check.sh" << 'EOF'
#!/bin/bash
# Pre-deployment health check for CI/CD pipeline

set -e

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"

echo "=== Pre-Deployment Check ==="

# Check if service is stable
echo "Checking service stability..."
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" \
    --cli-read-timeout 300 \
    --cli-connect-timeout 60

echo "✅ Service is stable and ready for deployment"

# Check current resource utilization
echo "Checking current resource utilization..."
cpu_util=$(aws cloudwatch get-metric-statistics \
    --namespace AWS/ECS \
    --metric-name CPUUtilization \
    --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
    --start-time "$(date -d '10 minutes ago' -Iseconds)" \
    --end-time "$(date -Iseconds)" \
    --period 300 \
    --statistics Average \
    --region "$REGION" \
    --query 'Datapoints[0].Average' \
    --output text 2>/dev/null || echo "0")

memory_util=$(aws cloudwatch get-metric-statistics \
    --namespace AWS/ECS \
    --metric-name MemoryUtilization \
    --dimensions Name=ServiceName,Value="$SERVICE_NAME" Name=ClusterName,Value="$CLUSTER_NAME" \
    --start-time "$(date -d '10 minutes ago' -Iseconds)" \
    --end-time "$(date -Iseconds)" \
    --period 300 \
    --statistics Average \
    --region "$REGION" \
    --query 'Datapoints[0].Average' \
    --output text 2>/dev/null || echo "0")

echo "Current CPU: ${cpu_util}%"
echo "Current Memory: ${memory_util}%"

# Warning if high utilization
if (( $(echo "$cpu_util > 70" | bc -l 2>/dev/null || echo 0) )); then
    echo "⚠️  WARNING: High CPU utilization before deployment"
fi

if (( $(echo "$memory_util > 70" | bc -l 2>/dev/null || echo 0) )); then
    echo "⚠️  WARNING: High memory utilization before deployment"
fi

echo "✅ Pre-deployment check completed"
EOF
    
    chmod +x "/tmp/${PROJECT_NAME}-pre-deploy-check.sh"
    echo -e "${GREEN}  ${SUCCESS_ICON} Created pre-deployment check: /tmp/${PROJECT_NAME}-pre-deploy-check.sh${NC}"
    
    # Post-deployment validation script
    cat > "/tmp/${PROJECT_NAME}-post-deploy-validate.sh" << 'EOF'
#!/bin/bash
# Post-deployment validation for CI/CD pipeline

set -e

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"
TIMEOUT_MINUTES="${TIMEOUT_MINUTES:-20}"

echo "=== Post-Deployment Validation ==="

# Wait for service to stabilize
echo "Waiting for service to stabilize (timeout: ${TIMEOUT_MINUTES} minutes)..."
timeout "${TIMEOUT_MINUTES}m" aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" || {
    echo "❌ Deployment timeout or failed"
    exit 1
}

echo "✅ Service stabilized successfully"

# Validate task health
echo "Validating task health..."
healthy_tasks=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" \
    --query 'services[0].runningCount' \
    --output text)

desired_tasks=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" \
    --query 'services[0].desiredCount' \
    --output text)

echo "Healthy tasks: $healthy_tasks/$desired_tasks"

if [ "$healthy_tasks" -lt "$desired_tasks" ]; then
    echo "❌ Not all tasks are healthy"
    exit 1
fi

# Check health endpoint if ALB exists
alb_dns=$(aws elbv2 describe-load-balancers \
    --region "$REGION" \
    --query "LoadBalancers[?contains(LoadBalancerName, '$PROJECT_NAME')].DNSName" \
    --output text 2>/dev/null | head -1)

if [ -n "$alb_dns" ] && [ "$alb_dns" != "None" ]; then
    echo "Testing health endpoint..."
    for i in {1..5}; do
        response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "http://$alb_dns/actuator/health" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            echo "✅ Health endpoint responding correctly"
            break
        else
            echo "⚠️  Health endpoint returned $response (attempt $i/5)"
            if [ $i -eq 5 ]; then
                echo "❌ Health endpoint check failed after 5 attempts"
                exit 1
            fi
            sleep 10
        fi
    done
fi

echo "✅ Post-deployment validation completed successfully"
EOF
    
    chmod +x "/tmp/${PROJECT_NAME}-post-deploy-validate.sh"
    echo -e "${GREEN}  ${SUCCESS_ICON} Created post-deployment validation: /tmp/${PROJECT_NAME}-post-deploy-validate.sh${NC}"
    echo ""
}

# Generate monitoring documentation
generate_documentation() {
    echo -e "${BLUE}${BOLD}${INFO_ICON} Generating Monitoring Documentation...${NC}"
    
    local doc_file="/tmp/${PROJECT_NAME}-monitoring-guide.md"
    
    cat > "$doc_file" << EOF
# ${PROJECT_NAME} Monitoring and Observability Guide

## Overview
This document provides comprehensive monitoring setup and troubleshooting guidance for the ${PROJECT_NAME} application.

## Monitoring Components

### 1. CloudWatch Dashboard
- **Name**: ${PROJECT_NAME}-${ENVIRONMENT}-apm
- **URL**: https://${REGION}.console.aws.amazon.com/cloudwatch/home?region=${REGION}#dashboards:name=${PROJECT_NAME}-${ENVIRONMENT}-apm

### 2. CloudWatch Alarms
The following alarms are configured:

- **High CPU**: Triggers when CPU > 80% for 10 minutes
- **High Memory**: Triggers when Memory > 85% for 10 minutes
- **Low Running Tasks**: Triggers when running tasks < 1
- **Deployment Timeout**: Triggers when tasks are pending > 30 minutes
- **High Response Time**: Triggers when response time > 5s for 15 minutes
- **High 5XX Errors**: Triggers when 5XX errors > 10 in 10 minutes
- **Unhealthy Hosts**: Triggers when unhealthy hosts > 0

### 3. Custom Metrics
- **ApplicationErrors**: Count of ERROR log entries
- **JVMMemoryPressure**: JVM memory-related issues
- **HealthCheckFailures**: Health check failures
- **DeploymentEvents**: Application start/stop events

## Monitoring Scripts

### Health Check Script
\`\`\`bash
/tmp/${PROJECT_NAME}-monitor-health.sh
\`\`\`

### Pre-deployment Check
\`\`\`bash
/tmp/${PROJECT_NAME}-pre-deploy-check.sh
\`\`\`

### Post-deployment Validation
\`\`\`bash
/tmp/${PROJECT_NAME}-post-deploy-validate.sh
\`\`\`

### Log Analysis
\`\`\`bash
# Analyze logs for errors and performance issues
./scripts/log-analyzer.sh -t 24

# Monitor deployment in real-time
./scripts/deployment-monitor.sh -t 30
\`\`\`

## Troubleshooting Guide

### Common Issues

#### 1. High CPU/Memory Usage
- Check application logs for memory leaks
- Review JVM settings in task definition
- Consider scaling up resources or scaling out instances

#### 2. Health Check Failures
- Verify \`/actuator/health\` endpoint is accessible
- Check security group rules
- Review application startup logs

#### 3. Deployment Timeouts
- Check for resource constraints
- Verify image exists in ECR
- Review task definition resource allocation

#### 4. High Response Times
- Analyze database connection pool settings
- Check for long-running queries
- Review external service dependencies

### Diagnostic Commands

\`\`\`bash
# Check service status
aws ecs describe-services --cluster ${CLUSTER_NAME} --services ${SERVICE_NAME} --region ${REGION}

# Check running tasks
aws ecs list-tasks --cluster ${CLUSTER_NAME} --service-name ${SERVICE_NAME} --region ${REGION}

# View recent logs
aws logs tail /ecs/${PROJECT_NAME}-${ENVIRONMENT} --follow --region ${REGION}

# Check target group health
aws elbv2 describe-target-health --target-group-arn [TARGET_GROUP_ARN] --region ${REGION}
\`\`\`

## Best Practices

1. **Monitor Key Metrics**: CPU, Memory, Response Time, Error Rate
2. **Set Up Alerting**: Configure SNS notifications for critical alarms
3. **Log Analysis**: Regularly review application logs for patterns
4. **Capacity Planning**: Monitor trends for resource scaling decisions
5. **Deployment Validation**: Use pre/post deployment checks

## Alert Response Procedures

### Critical Alerts (Immediate Response)
- Service down (running tasks = 0)
- High error rate (5XX > 50/minute)
- Deployment failures

### High Priority (Response within 1 hour)
- High resource utilization
- Unhealthy targets
- Response time degradation

### Medium Priority (Response within 4 hours)
- Memory pressure warnings
- Intermittent health check failures

## Integration with CI/CD

Add these scripts to your CI/CD pipeline:

1. **Pre-deployment**: Run health check and resource validation
2. **During deployment**: Monitor deployment progress
3. **Post-deployment**: Validate service health and performance

\`\`\`yaml
# Example GitHub Actions integration
steps:
  - name: Pre-deployment Check
    run: ./tmp/${PROJECT_NAME}-pre-deploy-check.sh
    
  - name: Deploy to ECS
    run: ./scripts/deploy-to-ecs.sh
    
  - name: Post-deployment Validation
    run: ./tmp/${PROJECT_NAME}-post-deploy-validate.sh
\`\`\`
EOF
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Generated documentation: $doc_file${NC}"
    echo ""
}

# Generate summary report
generate_summary() {
    echo -e "${BLUE}${BOLD}${SUCCESS_ICON} SETUP COMPLETE${NC}"
    echo -e "${BLUE}${BOLD}===========================================${NC}"
    echo ""
    echo -e "${GREEN}${BOLD}Monitoring Components Created:${NC}"
    echo -e "${GREEN}  ✅ CloudWatch Dashboard: ${PROJECT_NAME}-${ENVIRONMENT}-apm${NC}"
    echo -e "${GREEN}  ✅ CloudWatch Alarms: CPU, Memory, Tasks, Deployment, Response Time${NC}"
    echo -e "${GREEN}  ✅ Log Metric Filters: Errors, Memory Pressure, Health Checks${NC}"
    echo -e "${GREEN}  ✅ SNS Topic: ${PROJECT_NAME}-${ENVIRONMENT}-alerts${NC}"
    echo ""
    echo -e "${CYAN}${BOLD}Generated Scripts:${NC}"
    echo -e "${CYAN}  📋 Health Monitor: /tmp/${PROJECT_NAME}-monitor-health.sh${NC}"
    echo -e "${CYAN}  🚀 Pre-deploy Check: /tmp/${PROJECT_NAME}-pre-deploy-check.sh${NC}"
    echo -e "${CYAN}  ✅ Post-deploy Validation: /tmp/${PROJECT_NAME}-post-deploy-validate.sh${NC}"
    echo -e "${CYAN}  📖 Documentation: /tmp/${PROJECT_NAME}-monitoring-guide.md${NC}"
    echo ""
    echo -e "${YELLOW}${BOLD}Next Steps:${NC}"
    echo -e "${YELLOW}  1. Subscribe to SNS alerts (email/Slack)${NC}"
    echo -e "${YELLOW}  2. Review and customize alarm thresholds${NC}"
    echo -e "${YELLOW}  3. Integrate monitoring scripts with CI/CD${NC}"
    echo -e "${YELLOW}  4. Set up regular log analysis schedule${NC}"
    echo ""
    echo -e "${BLUE}Dashboard URL:${NC}"
    echo -e "${BLUE}https://${REGION}.console.aws.amazon.com/cloudwatch/home?region=${REGION}#dashboards:name=${PROJECT_NAME}-${ENVIRONMENT}-apm${NC}"
    echo ""
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -p, --project NAME      Project name (default: oddiya)"
    echo "  -e, --environment ENV   Environment (default: dev)"
    echo "  --skip-dashboard        Skip dashboard creation"
    echo "  --skip-alarms           Skip alarm creation"
    echo "  --skip-scripts          Skip script generation"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION              AWS region (default: ap-northeast-2)"
    echo "  PROJECT_NAME            Project name (default: oddiya)"
    echo "  ENVIRONMENT             Environment (default: dev)"
    echo "  ECS_CLUSTER             ECS cluster name"
    echo "  ECS_SERVICE             ECS service name"
    echo ""
}

# Parse command line arguments
SKIP_DASHBOARD=false
SKIP_ALARMS=false
SKIP_SCRIPTS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -p|--project)
            PROJECT_NAME="$2"
            shift 2
            ;;
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --skip-dashboard)
            SKIP_DASHBOARD=true
            shift
            ;;
        --skip-alarms)
            SKIP_ALARMS=true
            shift
            ;;
        --skip-scripts)
            SKIP_SCRIPTS=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Update cluster and service names based on parsed arguments
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"

# Main execution
main() {
    check_prerequisites
    
    if [ "$SKIP_DASHBOARD" = false ]; then
        create_dashboard
    fi
    
    if [ "$SKIP_ALARMS" = false ]; then
        create_alarms
    fi
    
    create_log_metrics
    
    if [ "$SKIP_SCRIPTS" = false ]; then
        create_monitoring_script
        create_cicd_integration
    fi
    
    generate_documentation
    generate_summary
}

main "$@"