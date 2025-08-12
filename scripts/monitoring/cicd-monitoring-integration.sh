#!/bin/bash

# CI/CD Monitoring Integration Script
# Integrates comprehensive monitoring into CI/CD pipeline

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-${PROJECT_NAME}-${ENVIRONMENT}}"
SERVICE_NAME="${ECS_SERVICE:-${PROJECT_NAME}-${ENVIRONMENT}}"
BUILD_NUMBER="${BUILD_NUMBER:-$(date +%Y%m%d%H%M%S)}"
PIPELINE_STAGE="${PIPELINE_STAGE:-deploy}"
NOTIFICATION_WEBHOOK="${NOTIFICATION_WEBHOOK:-}"

# Colors and formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Icons
PIPELINE_ICON="🏗️"
SUCCESS_ICON="✅"
ERROR_ICON="❌"
WARNING_ICON="⚠️"
INFO_ICON="ℹ️"
MONITOR_ICON="📊"
ROCKET_ICON="🚀"
CLOCK_ICON="⏰"

echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${BLUE}${BOLD}${PIPELINE_ICON} CI/CD MONITORING INTEGRATION${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${CYAN}Project: $PROJECT_NAME${NC}"
echo -e "${CYAN}Environment: $ENVIRONMENT${NC}"
echo -e "${CYAN}Pipeline Stage: $PIPELINE_STAGE${NC}"
echo -e "${CYAN}Build: $BUILD_NUMBER${NC}"
echo -e "${CYAN}Timestamp: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo ""

# Send notification
send_notification() {
    local stage="$1"
    local status="$2"
    local message="$3"
    local color="${4:-#36a64f}"
    
    if [ -n "$NOTIFICATION_WEBHOOK" ]; then
        local payload=$(cat << EOF
{
    "text": "🏗️ CI/CD Pipeline Update",
    "attachments": [{
        "color": "$color",
        "fields": [
            {"title": "Project", "value": "$PROJECT_NAME", "short": true},
            {"title": "Environment", "value": "$ENVIRONMENT", "short": true},
            {"title": "Stage", "value": "$stage", "short": true},
            {"title": "Status", "value": "$status", "short": true},
            {"title": "Build", "value": "$BUILD_NUMBER", "short": true},
            {"title": "Time", "value": "$(date '+%Y-%m-%d %H:%M:%S')", "short": true},
            {"title": "Message", "value": "$message", "short": false}
        ]
    }]
}
EOF
        )
        
        curl -X POST -H 'Content-type: application/json' \
            --data "$payload" \
            "$NOTIFICATION_WEBHOOK" >/dev/null 2>&1 || true
    fi
}

# Log custom metrics to CloudWatch
log_custom_metric() {
    local metric_name="$1"
    local value="$2"
    local unit="${3:-Count}"
    local dimensions="$4"
    
    aws cloudwatch put-metric-data \
        --namespace "Custom/$PROJECT_NAME/Pipeline" \
        --metric-data MetricName="$metric_name",Value="$value",Unit="$unit",Dimensions="$dimensions" \
        --region "$REGION" >/dev/null 2>&1 || true
}

# Pre-deployment validation
pre_deployment_validation() {
    echo -e "${BLUE}${BOLD}${INFO_ICON} Pre-Deployment Validation${NC}"
    echo -e "${BLUE}${BOLD}======================================${NC}"
    
    local start_time=$(date +%s)
    local validation_errors=0
    
    # Check if service exists and is stable
    echo -e "${CYAN}Checking service stability...${NC}"
    
    local service_exists
    service_exists=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'length(services)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$service_exists" -eq 0 ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} Service does not exist yet (first deployment)${NC}"
        log_custom_metric "PreDeployValidation" 1 "Count" "Stage=pre-deploy,Status=new-service"
    else
        # Wait for service to be stable before deploying
        echo -e "${CYAN}  Waiting for current service to stabilize...${NC}"
        
        local wait_result=0
        timeout 300 aws ecs wait services-stable \
            --cluster "$CLUSTER_NAME" \
            --services "$SERVICE_NAME" \
            --region "$REGION" || wait_result=$?
        
        if [ $wait_result -eq 0 ]; then
            echo -e "${GREEN}  ${SUCCESS_ICON} Service is stable and ready for deployment${NC}"
        else
            echo -e "${RED}  ${ERROR_ICON} Service is not stable - deployment may be risky${NC}"
            validation_errors=$((validation_errors + 1))
        fi
    fi
    
    # Check current resource utilization
    echo -e "${CYAN}Checking current resource utilization...${NC}"
    
    if [ "$service_exists" -gt 0 ]; then
        local cpu_util memory_util
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
        
        echo -e "${CYAN}  Current CPU: ${cpu_util}%${NC}"
        echo -e "${CYAN}  Current Memory: ${memory_util}%${NC}"
        
        # Log current resource utilization
        log_custom_metric "PreDeployCPU" "$cpu_util" "Percent" "Stage=pre-deploy,Build=$BUILD_NUMBER"
        log_custom_metric "PreDeployMemory" "$memory_util" "Percent" "Stage=pre-deploy,Build=$BUILD_NUMBER"
        
        # Warning for high utilization
        if (( $(echo "$cpu_util > 80" | bc -l 2>/dev/null || echo 0) )); then
            echo -e "${YELLOW}  ${WARNING_ICON} High CPU utilization detected before deployment${NC}"
            validation_errors=$((validation_errors + 1))
        fi
        
        if (( $(echo "$memory_util > 80" | bc -l 2>/dev/null || echo 0) )); then
            echo -e "${YELLOW}  ${WARNING_ICON} High memory utilization detected before deployment${NC}"
            validation_errors=$((validation_errors + 1))
        fi
    fi
    
    # Check for recent alarms
    echo -e "${CYAN}Checking for active alarms...${NC}"
    
    local active_alarms
    active_alarms=$(aws cloudwatch describe-alarms \
        --state-value ALARM \
        --alarm-name-prefix "$PROJECT_NAME-$ENVIRONMENT" \
        --region "$REGION" \
        --query 'length(MetricAlarms)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$active_alarms" -gt 0 ]; then
        echo -e "${RED}  ${ERROR_ICON} $active_alarms active alarm(s) detected${NC}"
        
        # List active alarms
        aws cloudwatch describe-alarms \
            --state-value ALARM \
            --alarm-name-prefix "$PROJECT_NAME-$ENVIRONMENT" \
            --region "$REGION" \
            --query 'MetricAlarms[].[AlarmName,StateReason]' \
            --output text 2>/dev/null | while IFS=$'\t' read -r alarm_name reason; do
            echo -e "${RED}    ${ERROR_ICON} $alarm_name: $reason${NC}"
        done
        
        validation_errors=$((validation_errors + 1))
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No active alarms${NC}"
    fi
    
    # Check ECR image availability
    echo -e "${CYAN}Checking ECR image availability...${NC}"
    
    local ecr_repo="${ECR_REPOSITORY:-$PROJECT_NAME}"
    local image_exists
    image_exists=$(aws ecr describe-images \
        --repository-name "$ecr_repo" \
        --image-ids imageTag=latest \
        --region "$REGION" \
        --query 'length(imageDetails)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$image_exists" -gt 0 ]; then
        echo -e "${GREEN}  ${SUCCESS_ICON} ECR image available${NC}"
        
        # Get image details
        local image_size push_date
        image_size=$(aws ecr describe-images \
            --repository-name "$ecr_repo" \
            --image-ids imageTag=latest \
            --region "$REGION" \
            --query 'imageDetails[0].imageSizeInBytes' \
            --output text 2>/dev/null)
        
        push_date=$(aws ecr describe-images \
            --repository-name "$ecr_repo" \
            --image-ids imageTag=latest \
            --region "$REGION" \
            --query 'imageDetails[0].imagePushedAt' \
            --output text 2>/dev/null)
        
        local size_mb=$((image_size / 1024 / 1024))
        echo -e "${CYAN}    Size: ${size_mb}MB, Pushed: $push_date${NC}"
    else
        echo -e "${RED}  ${ERROR_ICON} ECR image not found${NC}"
        validation_errors=$((validation_errors + 1))
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # Log validation results
    log_custom_metric "PreDeployValidationTime" "$duration" "Seconds" "Stage=pre-deploy,Build=$BUILD_NUMBER"
    log_custom_metric "PreDeployValidationErrors" "$validation_errors" "Count" "Stage=pre-deploy,Build=$BUILD_NUMBER"
    
    echo ""
    if [ $validation_errors -eq 0 ]; then
        echo -e "${GREEN}${BOLD}${SUCCESS_ICON} Pre-deployment validation passed${NC}"
        send_notification "Pre-deployment" "PASSED" "All validation checks passed. Ready for deployment." "#36a64f"
        return 0
    else
        echo -e "${YELLOW}${BOLD}${WARNING_ICON} Pre-deployment validation completed with $validation_errors warning(s)${NC}"
        send_notification "Pre-deployment" "WARNING" "$validation_errors validation warnings detected." "#ffaa00"
        
        if [ "$validation_errors" -gt 3 ]; then
            echo -e "${RED}${BOLD}${ERROR_ICON} Too many validation errors - consider postponing deployment${NC}"
            send_notification "Pre-deployment" "FAILED" "Too many validation errors. Deployment not recommended." "#ff0000"
            return 1
        fi
        
        return 0
    fi
}

# Monitor deployment progress
monitor_deployment() {
    echo -e "${BLUE}${BOLD}${ROCKET_ICON} Monitoring Deployment Progress${NC}"
    echo -e "${BLUE}${BOLD}======================================${NC}"
    
    local start_time=$(date +%s)
    local timeout_seconds=1800  # 30 minutes
    local check_interval=30
    
    send_notification "Deployment" "STARTED" "Deployment monitoring started." "#ffaa00"
    
    while true; do
        local current_time=$(date +%s)
        local elapsed_seconds=$((current_time - start_time))
        local elapsed_minutes=$((elapsed_seconds / 60))
        
        # Get service status
        local service_details
        service_details=$(aws ecs describe-services \
            --cluster "$CLUSTER_NAME" \
            --services "$SERVICE_NAME" \
            --region "$REGION" \
            --query 'services[0]' \
            --output json 2>/dev/null || echo "{}")
        
        if [ "$service_details" = "{}" ]; then
            echo -e "${RED}${ERROR_ICON} Failed to get service details${NC}"
            log_custom_metric "DeploymentMonitoringError" 1 "Count" "Stage=monitoring,Build=$BUILD_NUMBER"
            send_notification "Deployment" "ERROR" "Failed to get service details." "#ff0000"
            return 1
        fi
        
        # Parse service status
        local desired running pending
        desired=$(echo "$service_details" | jq -r '.desiredCount')
        running=$(echo "$service_details" | jq -r '.runningCount')
        pending=$(echo "$service_details" | jq -r '.pendingCount')
        
        echo -e "${CYAN}${CLOCK_ICON} Elapsed: ${elapsed_minutes}m | Desired: $desired | Running: $running | Pending: $pending${NC}"
        
        # Log current deployment metrics
        log_custom_metric "DeploymentRunningTasks" "$running" "Count" "Stage=deployment,Build=$BUILD_NUMBER"
        log_custom_metric "DeploymentPendingTasks" "$pending" "Count" "Stage=deployment,Build=$BUILD_NUMBER"
        log_custom_metric "DeploymentElapsedTime" "$elapsed_seconds" "Seconds" "Stage=deployment,Build=$BUILD_NUMBER"
        
        # Check for deployment completion
        if [ "$running" -ge "$desired" ] && [ "$pending" -eq 0 ]; then
            # Additional health check
            local healthy_targets=0
            
            # Check ALB target health if available
            local target_group_arn
            target_group_arn=$(aws elbv2 describe-target-groups \
                --region "$REGION" \
                --query "TargetGroups[?contains(TargetGroupName, '$PROJECT_NAME')].TargetGroupArn" \
                --output text 2>/dev/null | head -1)
            
            if [ -n "$target_group_arn" ] && [ "$target_group_arn" != "None" ]; then
                healthy_targets=$(aws elbv2 describe-target-health \
                    --target-group-arn "$target_group_arn" \
                    --region "$REGION" \
                    --query 'length(TargetHealthDescriptions[?TargetHealth.State==`healthy`])' \
                    --output text 2>/dev/null || echo "0")
                
                echo -e "${CYAN}  Healthy targets: $healthy_targets${NC}"
            fi
            
            if [ "$healthy_targets" -ge "$desired" ] || [ -z "$target_group_arn" ]; then
                echo -e "${GREEN}${BOLD}${SUCCESS_ICON} Deployment completed successfully!${NC}"
                echo -e "${GREEN}  Time taken: ${elapsed_minutes} minutes${NC}"
                
                log_custom_metric "DeploymentSuccess" 1 "Count" "Stage=deployment,Build=$BUILD_NUMBER"
                log_custom_metric "DeploymentDuration" "$elapsed_seconds" "Seconds" "Stage=deployment,Build=$BUILD_NUMBER"
                
                send_notification "Deployment" "SUCCESS" "Deployment completed in ${elapsed_minutes} minutes." "#36a64f"
                return 0
            fi
        fi
        
        # Check for timeout
        if [ $elapsed_seconds -ge $timeout_seconds ]; then
            echo -e "${RED}${BOLD}${ERROR_ICON} Deployment timeout (30 minutes)${NC}"
            
            # Get recent service events for troubleshooting
            local recent_events
            recent_events=$(echo "$service_details" | jq -r '.events[:3][] | [.createdAt, .message] | @tsv')
            
            echo -e "${RED}Recent service events:${NC}"
            while IFS=$'\t' read -r timestamp message; do
                echo -e "${RED}  ${ERROR_ICON} $timestamp: $message${NC}"
            done <<< "$recent_events"
            
            log_custom_metric "DeploymentTimeout" 1 "Count" "Stage=deployment,Build=$BUILD_NUMBER"
            send_notification "Deployment" "TIMEOUT" "Deployment timeout after 30 minutes." "#ff0000"
            return 1
        fi
        
        sleep $check_interval
    done
}

# Post-deployment validation
post_deployment_validation() {
    echo -e "${BLUE}${BOLD}${SUCCESS_ICON} Post-Deployment Validation${NC}"
    echo -e "${BLUE}${BOLD}======================================${NC}"
    
    local start_time=$(date +%s)
    local validation_errors=0
    
    # Wait a bit for service to fully stabilize
    echo -e "${CYAN}Allowing service to stabilize...${NC}"
    sleep 60
    
    # Validate service health
    echo -e "${CYAN}Validating service health...${NC}"
    
    local service_details
    service_details=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0]' \
        --output json 2>/dev/null)
    
    local desired running
    desired=$(echo "$service_details" | jq -r '.desiredCount')
    running=$(echo "$service_details" | jq -r '.runningCount')
    
    if [ "$running" -eq "$desired" ]; then
        echo -e "${GREEN}  ${SUCCESS_ICON} All tasks are running ($running/$desired)${NC}"
    else
        echo -e "${RED}  ${ERROR_ICON} Not all tasks are running ($running/$desired)${NC}"
        validation_errors=$((validation_errors + 1))
    fi
    
    # Validate target health
    echo -e "${CYAN}Validating load balancer targets...${NC}"
    
    local target_group_arn
    target_group_arn=$(aws elbv2 describe-target-groups \
        --region "$REGION" \
        --query "TargetGroups[?contains(TargetGroupName, '$PROJECT_NAME')].TargetGroupArn" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$target_group_arn" ] && [ "$target_group_arn" != "None" ]; then
        local healthy_targets unhealthy_targets
        healthy_targets=$(aws elbv2 describe-target-health \
            --target-group-arn "$target_group_arn" \
            --region "$REGION" \
            --query 'length(TargetHealthDescriptions[?TargetHealth.State==`healthy`])' \
            --output text 2>/dev/null || echo "0")
        
        unhealthy_targets=$(aws elbv2 describe-target-health \
            --target-group-arn "$target_group_arn" \
            --region "$REGION" \
            --query 'length(TargetHealthDescriptions[?TargetHealth.State!=`healthy`])' \
            --output text 2>/dev/null || echo "0")
        
        echo -e "${CYAN}  Healthy targets: $healthy_targets${NC}"
        echo -e "${CYAN}  Unhealthy targets: $unhealthy_targets${NC}"
        
        if [ "$healthy_targets" -lt "$desired" ]; then
            echo -e "${RED}  ${ERROR_ICON} Not enough healthy targets${NC}"
            validation_errors=$((validation_errors + 1))
        fi
        
        if [ "$unhealthy_targets" -gt 0 ]; then
            echo -e "${YELLOW}  ${WARNING_ICON} Some targets are unhealthy${NC}"
        fi
        
        log_custom_metric "PostDeployHealthyTargets" "$healthy_targets" "Count" "Stage=post-deploy,Build=$BUILD_NUMBER"
    else
        echo -e "${YELLOW}  ${WARNING_ICON} No target group found${NC}"
    fi
    
    # Test application endpoint
    echo -e "${CYAN}Testing application endpoint...${NC}"
    
    local alb_dns
    alb_dns=$(aws elbv2 describe-load-balancers \
        --region "$REGION" \
        --query "LoadBalancers[?contains(LoadBalancerName, '$PROJECT_NAME')].DNSName" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$alb_dns" ] && [ "$alb_dns" != "None" ]; then
        local health_url="http://$alb_dns/actuator/health"
        local response_code response_time
        
        response_time=$(curl -o /dev/null -s -w "%{time_total}" --max-time 15 "$health_url" 2>/dev/null || echo "15.000")
        response_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$health_url" 2>/dev/null || echo "000")
        
        echo -e "${CYAN}  Health endpoint: $health_url${NC}"
        echo -e "${CYAN}  Response code: $response_code${NC}"
        echo -e "${CYAN}  Response time: ${response_time}s${NC}"
        
        log_custom_metric "PostDeployResponseTime" "$response_time" "Seconds" "Stage=post-deploy,Build=$BUILD_NUMBER"
        log_custom_metric "PostDeployResponseCode" "$response_code" "Count" "Stage=post-deploy,Build=$BUILD_NUMBER"
        
        if [ "$response_code" = "200" ]; then
            echo -e "${GREEN}  ${SUCCESS_ICON} Health endpoint responding correctly${NC}"
            
            if (( $(echo "$response_time > 5" | bc -l 2>/dev/null || echo 0) )); then
                echo -e "${YELLOW}  ${WARNING_ICON} Slow response time detected${NC}"
            fi
        else
            echo -e "${RED}  ${ERROR_ICON} Health endpoint not responding correctly${NC}"
            validation_errors=$((validation_errors + 1))
        fi
    else
        echo -e "${YELLOW}  ${WARNING_ICON} No load balancer found${NC}"
    fi
    
    # Check recent logs for errors
    echo -e "${CYAN}Checking recent logs for errors...${NC}"
    
    local log_group="/ecs/$PROJECT_NAME-$ENVIRONMENT"
    local recent_errors
    recent_errors=$(aws logs filter-log-events \
        --log-group-name "$log_group" \
        --start-time "$((start_time * 1000))" \
        --filter-pattern "ERROR" \
        --region "$REGION" \
        --query 'length(events)' \
        --output text 2>/dev/null || echo "0")
    
    echo -e "${CYAN}  Recent errors: $recent_errors${NC}"
    
    if [ "$recent_errors" -gt 10 ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} High error rate detected in logs${NC}"
        validation_errors=$((validation_errors + 1))
    fi
    
    log_custom_metric "PostDeployLogErrors" "$recent_errors" "Count" "Stage=post-deploy,Build=$BUILD_NUMBER"
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # Log validation results
    log_custom_metric "PostDeployValidationTime" "$duration" "Seconds" "Stage=post-deploy,Build=$BUILD_NUMBER"
    log_custom_metric "PostDeployValidationErrors" "$validation_errors" "Count" "Stage=post-deploy,Build=$BUILD_NUMBER"
    
    echo ""
    if [ $validation_errors -eq 0 ]; then
        echo -e "${GREEN}${BOLD}${SUCCESS_ICON} Post-deployment validation passed${NC}"
        send_notification "Post-deployment" "PASSED" "All validation checks passed. Deployment successful!" "#36a64f"
        return 0
    else
        echo -e "${YELLOW}${BOLD}${WARNING_ICON} Post-deployment validation completed with $validation_errors warning(s)${NC}"
        send_notification "Post-deployment" "WARNING" "$validation_errors validation warnings detected." "#ffaa00"
        return 0
    fi
}

# Rollback monitoring
monitor_rollback() {
    echo -e "${BLUE}${BOLD}${WARNING_ICON} Monitoring Rollback${NC}"
    echo -e "${BLUE}${BOLD}======================================${NC}"
    
    local start_time=$(date +%s)
    
    send_notification "Rollback" "STARTED" "Rollback monitoring started." "#ffaa00"
    
    # Wait for rollback to complete
    local timeout_seconds=900  # 15 minutes
    local wait_result=0
    
    timeout "${timeout_seconds}s" aws ecs wait services-stable \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" || wait_result=$?
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    if [ $wait_result -eq 0 ]; then
        echo -e "${GREEN}${BOLD}${SUCCESS_ICON} Rollback completed successfully${NC}"
        echo -e "${GREEN}  Time taken: $((duration / 60)) minutes${NC}"
        
        log_custom_metric "RollbackSuccess" 1 "Count" "Stage=rollback,Build=$BUILD_NUMBER"
        log_custom_metric "RollbackDuration" "$duration" "Seconds" "Stage=rollback,Build=$BUILD_NUMBER"
        
        send_notification "Rollback" "SUCCESS" "Rollback completed in $((duration / 60)) minutes." "#36a64f"
        return 0
    else
        echo -e "${RED}${BOLD}${ERROR_ICON} Rollback failed or timed out${NC}"
        
        log_custom_metric "RollbackFailure" 1 "Count" "Stage=rollback,Build=$BUILD_NUMBER"
        send_notification "Rollback" "FAILED" "Rollback failed or timed out." "#ff0000"
        return 1
    fi
}

# Show usage
show_usage() {
    echo "Usage: $0 [STAGE] [OPTIONS]"
    echo ""
    echo "Stages:"
    echo "  pre-deploy              Run pre-deployment validation"
    echo "  monitor                 Monitor deployment progress"  
    echo "  post-deploy             Run post-deployment validation"
    echo "  rollback                Monitor rollback process"
    echo "  full                    Run complete deployment monitoring"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -b, --build BUILD       Build number/identifier"
    echo "  -w, --webhook URL       Notification webhook URL"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION              AWS region (default: ap-northeast-2)"
    echo "  PROJECT_NAME            Project name (default: oddiya)"
    echo "  ENVIRONMENT             Environment (default: dev)"
    echo "  ECS_CLUSTER             ECS cluster name"
    echo "  ECS_SERVICE             ECS service name"
    echo "  BUILD_NUMBER            Build number for tracking"
    echo "  NOTIFICATION_WEBHOOK    Webhook URL for notifications"
    echo ""
    echo "Examples:"
    echo "  $0 pre-deploy           # Run pre-deployment checks"
    echo "  $0 monitor              # Monitor deployment progress"
    echo "  $0 post-deploy          # Validate deployment"
    echo "  $0 full                 # Complete monitoring cycle"
    echo ""
}

# Parse command line arguments
STAGE="$1"
shift || true

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -b|--build)
            BUILD_NUMBER="$2"
            shift 2
            ;;
        -w|--webhook)
            NOTIFICATION_WEBHOOK="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validate stage parameter
case "$STAGE" in
    pre-deploy)
        pre_deployment_validation
        ;;
    monitor)
        monitor_deployment
        ;;
    post-deploy)
        post_deployment_validation
        ;;
    rollback)
        monitor_rollback
        ;;
    full)
        if pre_deployment_validation; then
            if monitor_deployment; then
                post_deployment_validation
            else
                echo -e "${RED}${ERROR_ICON} Deployment failed, consider rollback${NC}"
                exit 1
            fi
        else
            echo -e "${RED}${ERROR_ICON} Pre-deployment validation failed${NC}"
            exit 1
        fi
        ;;
    "")
        echo "Error: Stage parameter required"
        show_usage
        exit 1
        ;;
    *)
        echo "Error: Unknown stage '$STAGE'"
        show_usage
        exit 1
        ;;
esac