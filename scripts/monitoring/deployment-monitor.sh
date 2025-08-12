#!/bin/bash

# Advanced ECS Deployment Monitoring Script
# Monitors deployment progress with real-time updates, timeout detection, and troubleshooting

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER_NAME="${ECS_CLUSTER:-oddiya-dev}"
SERVICE_NAME="${ECS_SERVICE:-oddiya-dev}"
ECR_REPOSITORY="${ECR_REPOSITORY:-oddiya}"
TIMEOUT_MINUTES="${DEPLOYMENT_TIMEOUT:-30}"
CHECK_INTERVAL="${CHECK_INTERVAL:-30}"
SLACK_WEBHOOK="${SLACK_WEBHOOK_URL:-}"

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
SUCCESS_ICON="✅"
ERROR_ICON="❌"
WARNING_ICON="⚠️"
INFO_ICON="ℹ️"
PROGRESS_ICON="🔄"
CLOCK_ICON="⏰"
ROCKET_ICON="🚀"

# Initialize logging
LOG_FILE="/tmp/deployment-monitor-$(date +%Y%m%d-%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2> >(tee -a "$LOG_FILE" >&2)

echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${BLUE}${BOLD}${ROCKET_ICON} ECS DEPLOYMENT MONITOR${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${CYAN}Region: $REGION${NC}"
echo -e "${CYAN}Cluster: $CLUSTER_NAME${NC}"
echo -e "${CYAN}Service: $SERVICE_NAME${NC}"
echo -e "${CYAN}Timeout: ${TIMEOUT_MINUTES} minutes${NC}"
echo -e "${CYAN}Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${CYAN}Log: $LOG_FILE${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo ""

# Utility functions
log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

send_slack_notification() {
    local message="$1"
    local color="${2:-#36a64f}"
    
    if [ -n "$SLACK_WEBHOOK" ]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{
                \"text\": \"🚀 ECS Deployment Update\",
                \"attachments\": [{
                    \"color\": \"$color\",
                    \"fields\": [{
                        \"title\": \"Service\",
                        \"value\": \"$SERVICE_NAME\",
                        \"short\": true
                    }, {
                        \"title\": \"Status\",
                        \"value\": \"$message\",
                        \"short\": true
                    }, {
                        \"title\": \"Time\",
                        \"value\": \"$(date '+%Y-%m-%d %H:%M:%S')\",
                        \"short\": true
                    }]
                }]
            }" \
            "$SLACK_WEBHOOK" >/dev/null 2>&1
    fi
}

get_service_details() {
    aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0]' \
        --output json 2>/dev/null || echo "{}"
}

get_task_definition_arn() {
    local service_details="$1"
    echo "$service_details" | jq -r '.taskDefinition // empty'
}

get_task_counts() {
    local service_details="$1"
    echo "$service_details" | jq -r '[.desiredCount, .runningCount, .pendingCount] | @tsv'
}

get_deployment_status() {
    local service_details="$1"
    echo "$service_details" | jq -r '.deployments[] | select(.status == "PRIMARY") | [.status, .taskDefinition, .createdAt, .updatedAt] | @tsv'
}

get_recent_events() {
    local service_details="$1"
    local limit="${2:-5}"
    echo "$service_details" | jq -r ".events[:$limit][] | [.createdAt, .message] | @tsv"
}

check_task_health() {
    local task_arns
    task_arns=$(aws ecs list-tasks \
        --cluster "$CLUSTER_NAME" \
        --service-name "$SERVICE_NAME" \
        --desired-status RUNNING \
        --region "$REGION" \
        --query 'taskArns' \
        --output text 2>/dev/null)
    
    if [ -z "$task_arns" ] || [ "$task_arns" == "None" ]; then
        echo "No running tasks found"
        return
    fi
    
    local task_details
    task_details=$(aws ecs describe-tasks \
        --cluster "$CLUSTER_NAME" \
        --tasks $task_arns \
        --region "$REGION" \
        --query 'tasks[].[taskArn, lastStatus, healthStatus, connectivityAt, startedAt, stoppedReason]' \
        --output json 2>/dev/null)
    
    echo "$task_details" | jq -r '.[] | @tsv'
}

get_target_group_health() {
    # Try to find target group ARN from ALB configuration
    local target_groups
    target_groups=$(aws elbv2 describe-target-groups \
        --region "$REGION" \
        --query "TargetGroups[?contains(TargetGroupName, '$SERVICE_NAME')].TargetGroupArn" \
        --output text 2>/dev/null | head -1)
    
    if [ -n "$target_groups" ] && [ "$target_groups" != "None" ]; then
        aws elbv2 describe-target-health \
            --target-group-arn "$target_groups" \
            --region "$REGION" \
            --query 'TargetHealthDescriptions[].[Target.Id, TargetHealth.State, TargetHealth.Reason]' \
            --output json 2>/dev/null | jq -r '.[] | @tsv'
    fi
}

analyze_deployment_issues() {
    local service_details="$1"
    local issues=()
    
    # Check for resource constraints
    local cpu_reservation memory_reservation
    cpu_reservation=$(echo "$service_details" | jq -r '.deployments[0].taskDefinition' | xargs -I {} aws ecs describe-task-definition --task-definition {} --region "$REGION" --query 'taskDefinition.cpu' --output text 2>/dev/null)
    memory_reservation=$(echo "$service_details" | jq -r '.deployments[0].taskDefinition' | xargs -I {} aws ecs describe-task-definition --task-definition {} --region "$REGION" --query 'taskDefinition.memory' --output text 2>/dev/null)
    
    # Check for failed tasks
    local failed_tasks
    failed_tasks=$(aws ecs list-tasks \
        --cluster "$CLUSTER_NAME" \
        --service-name "$SERVICE_NAME" \
        --desired-status STOPPED \
        --region "$REGION" \
        --query 'length(taskArns)' \
        --output text 2>/dev/null)
    
    if [ "$failed_tasks" -gt 0 ]; then
        issues+=("Found $failed_tasks recently stopped tasks")
        
        # Get stop reasons for recent failed tasks
        local stop_reasons
        stop_reasons=$(aws ecs list-tasks \
            --cluster "$CLUSTER_NAME" \
            --service-name "$SERVICE_NAME" \
            --desired-status STOPPED \
            --region "$REGION" \
            --query 'taskArns[:3]' \
            --output text 2>/dev/null)
        
        for task_arn in $stop_reasons; do
            local stop_reason
            stop_reason=$(aws ecs describe-tasks \
                --cluster "$CLUSTER_NAME" \
                --tasks "$task_arn" \
                --region "$REGION" \
                --query 'tasks[0].stoppedReason' \
                --output text 2>/dev/null)
            
            if [ "$stop_reason" != "None" ] && [ -n "$stop_reason" ]; then
                issues+=("Task stopped: $stop_reason")
            fi
        done
    fi
    
    # Check events for common issues
    local recent_events
    recent_events=$(get_recent_events "$service_details" 10)
    
    while IFS=$'\t' read -r timestamp message; do
        if [[ "$message" == *"unable to place"* ]]; then
            issues+=("Resource constraint: $message")
        elif [[ "$message" == *"health check"* ]] && [[ "$message" == *"failed"* ]]; then
            issues+=("Health check failure: $message")
        elif [[ "$message" == *"error"* ]] || [[ "$message" == *"failed"* ]]; then
            issues+=("Error: $message")
        fi
    done <<< "$recent_events"
    
    # Output issues
    if [ ${#issues[@]} -gt 0 ]; then
        echo -e "${RED}${BOLD}Potential Issues Detected:${NC}"
        for issue in "${issues[@]}"; do
            echo -e "${RED}  ${ERROR_ICON} $issue${NC}"
        done
        echo ""
        
        # Suggest solutions
        echo -e "${YELLOW}${BOLD}Troubleshooting Suggestions:${NC}"
        echo -e "${YELLOW}  1. Check task definition resource requirements (CPU: $cpu_reservation, Memory: $memory_reservation)${NC}"
        echo -e "${YELLOW}  2. Verify health check endpoint: /actuator/health${NC}"
        echo -e "${YELLOW}  3. Check application logs: aws logs tail /ecs/$SERVICE_NAME --follow${NC}"
        echo -e "${YELLOW}  4. Verify ECR image exists and is accessible${NC}"
        echo -e "${YELLOW}  5. Check security group and network configuration${NC}"
        echo ""
    fi
}

display_status() {
    local service_details="$1"
    local duration="$2"
    
    # Parse service details
    local task_counts deployment_info
    task_counts=$(get_task_counts "$service_details")
    deployment_info=$(get_deployment_status "$service_details")
    
    IFS=$'\t' read -r desired running pending <<< "$task_counts"
    IFS=$'\t' read -r deploy_status task_def created_at updated_at <<< "$deployment_info"
    
    # Display current status
    echo -e "${BLUE}${BOLD}Current Status (${duration}):${NC}"
    echo -e "${CYAN}  Desired Tasks: $desired${NC}"
    echo -e "${CYAN}  Running Tasks: $running${NC}"
    echo -e "${CYAN}  Pending Tasks: $pending${NC}"
    echo -e "${CYAN}  Deployment: $deploy_status${NC}"
    
    # Task health status
    echo -e "${BLUE}${BOLD}Task Health:${NC}"
    local task_health
    task_health=$(check_task_health)
    
    if [ -n "$task_health" ]; then
        while IFS=$'\t' read -r task_arn status health_status connectivity started stopped_reason; do
            local task_id
            task_id=$(basename "$task_arn")
            
            if [ "$status" == "RUNNING" ]; then
                echo -e "${GREEN}  ${SUCCESS_ICON} Task ${task_id:0:8}: $status${NC}"
            else
                echo -e "${YELLOW}  ${WARNING_ICON} Task ${task_id:0:8}: $status${NC}"
            fi
            
            if [ -n "$stopped_reason" ] && [ "$stopped_reason" != "null" ]; then
                echo -e "${RED}    Reason: $stopped_reason${NC}"
            fi
        done <<< "$task_health"
    else
        echo -e "${RED}  ${ERROR_ICON} No running tasks found${NC}"
    fi
    
    # Target group health
    echo -e "${BLUE}${BOLD}Load Balancer Health:${NC}"
    local target_health
    target_health=$(get_target_group_health)
    
    if [ -n "$target_health" ]; then
        while IFS=$'\t' read -r target_id state reason; do
            if [ "$state" == "healthy" ]; then
                echo -e "${GREEN}  ${SUCCESS_ICON} Target ${target_id:0:12}: $state${NC}"
            else
                echo -e "${RED}  ${ERROR_ICON} Target ${target_id:0:12}: $state ($reason)${NC}"
            fi
        done <<< "$target_health"
    else
        echo -e "${YELLOW}  ${WARNING_ICON} No target group information available${NC}"
    fi
    
    # Recent events
    echo -e "${BLUE}${BOLD}Recent Events:${NC}"
    local recent_events
    recent_events=$(get_recent_events "$service_details" 3)
    
    while IFS=$'\t' read -r timestamp message; do
        local formatted_time
        formatted_time=$(date -d "$timestamp" "+%H:%M:%S" 2>/dev/null || echo "$timestamp")
        
        if [[ "$message" == *"error"* ]] || [[ "$message" == *"failed"* ]] || [[ "$message" == *"unable"* ]]; then
            echo -e "${RED}  ${ERROR_ICON} $formatted_time: $message${NC}"
        elif [[ "$message" == *"started"* ]] || [[ "$message" == *"healthy"* ]]; then
            echo -e "${GREEN}  ${SUCCESS_ICON} $formatted_time: $message${NC}"
        else
            echo -e "${CYAN}  ${INFO_ICON} $formatted_time: $message${NC}"
        fi
    done <<< "$recent_events"
    
    echo ""
}

wait_for_deployment() {
    local start_time
    start_time=$(date +%s)
    local timeout_seconds=$((TIMEOUT_MINUTES * 60))
    
    log "${INFO_ICON} Starting deployment monitoring..."
    send_slack_notification "Deployment started" "#ffaa00"
    
    while true; do
        local current_time duration_seconds duration_formatted
        current_time=$(date +%s)
        duration_seconds=$((current_time - start_time))
        duration_formatted=$(printf "%02d:%02d" $((duration_seconds / 60)) $((duration_seconds % 60)))
        
        # Get current service status
        local service_details
        service_details=$(get_service_details)
        
        if [ "$service_details" == "{}" ]; then
            log "${ERROR_ICON} Failed to get service details"
            return 1
        fi
        
        # Display current status
        clear
        echo -e "${BLUE}${BOLD}=========================================${NC}"
        echo -e "${BLUE}${BOLD}${ROCKET_ICON} ECS DEPLOYMENT MONITOR${NC}"
        echo -e "${BLUE}${BOLD}=========================================${NC}"
        display_status "$service_details" "$duration_formatted"
        
        # Check if deployment is complete
        local task_counts
        task_counts=$(get_task_counts "$service_details")
        IFS=$'\t' read -r desired running pending <<< "$task_counts"
        
        if [ "$running" -ge "$desired" ] && [ "$pending" -eq 0 ]; then
            # Check if all tasks are healthy
            local healthy_count=0
            local task_health
            task_health=$(check_task_health)
            
            while IFS=$'\t' read -r task_arn status health_status connectivity started stopped_reason; do
                if [ "$status" == "RUNNING" ]; then
                    healthy_count=$((healthy_count + 1))
                fi
            done <<< "$task_health"
            
            if [ "$healthy_count" -ge "$desired" ]; then
                echo -e "${GREEN}${BOLD}${SUCCESS_ICON} Deployment completed successfully!${NC}"
                log "${SUCCESS_ICON} Deployment completed in $duration_formatted"
                send_slack_notification "Deployment completed successfully in $duration_formatted" "#36a64f"
                return 0
            fi
        fi
        
        # Check for timeout
        if [ $duration_seconds -ge $timeout_seconds ]; then
            echo -e "${RED}${BOLD}${CLOCK_ICON} Deployment timeout reached (${TIMEOUT_MINUTES} minutes)${NC}"
            log "${ERROR_ICON} Deployment timeout after $duration_formatted"
            analyze_deployment_issues "$service_details"
            send_slack_notification "Deployment timeout after $duration_formatted" "#ff0000"
            return 1
        fi
        
        # Check for deployment issues every 5 minutes
        if [ $((duration_seconds % 300)) -eq 0 ] && [ $duration_seconds -gt 0 ]; then
            analyze_deployment_issues "$service_details"
        fi
        
        echo -e "${CYAN}${PROGRESS_ICON} Waiting... (timeout in $((timeout_seconds - duration_seconds)) seconds)${NC}"
        echo -e "${CYAN}Next check in $CHECK_INTERVAL seconds...${NC}"
        
        sleep "$CHECK_INTERVAL"
    done
}

# Validate prerequisites
validate_prerequisites() {
    local errors=()
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        errors+=("AWS CLI is not installed")
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        errors+=("AWS credentials not configured")
    fi
    
    # Check jq
    if ! command -v jq &> /dev/null; then
        errors+=("jq is not installed (required for JSON parsing)")
    fi
    
    # Check if service exists
    local service_exists
    service_exists=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'length(services)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$service_exists" -eq 0 ]; then
        errors+=("ECS service '$SERVICE_NAME' not found in cluster '$CLUSTER_NAME'")
    fi
    
    if [ ${#errors[@]} -gt 0 ]; then
        echo -e "${RED}${BOLD}Prerequisites check failed:${NC}"
        for error in "${errors[@]}"; do
            echo -e "${RED}  ${ERROR_ICON} $error${NC}"
        done
        echo ""
        exit 1
    fi
    
    echo -e "${GREEN}${SUCCESS_ICON} Prerequisites validated${NC}"
}

# Trap for cleanup
cleanup() {
    echo -e "\n${YELLOW}${INFO_ICON} Monitoring stopped by user${NC}"
    log "${INFO_ICON} Monitoring stopped by user signal"
    send_slack_notification "Monitoring stopped manually" "#ffaa00"
    exit 130
}

trap cleanup SIGINT SIGTERM

# Main execution
main() {
    validate_prerequisites
    wait_for_deployment
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -t, --timeout MINUTES   Set deployment timeout (default: 30)"
    echo "  -i, --interval SECONDS  Set check interval (default: 30)"
    echo "  -s, --slack-webhook URL Set Slack webhook URL for notifications"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION              AWS region (default: ap-northeast-2)"
    echo "  ECS_CLUSTER             ECS cluster name (default: oddiya-dev)"
    echo "  ECS_SERVICE             ECS service name (default: oddiya-dev)"
    echo "  DEPLOYMENT_TIMEOUT      Timeout in minutes (default: 30)"
    echo "  CHECK_INTERVAL          Check interval in seconds (default: 30)"
    echo "  SLACK_WEBHOOK_URL       Slack webhook URL for notifications"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Monitor with defaults"
    echo "  $0 -t 45 -i 60                      # 45-min timeout, 60-sec intervals"
    echo "  ECS_SERVICE=my-service $0           # Monitor different service"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -t|--timeout)
            TIMEOUT_MINUTES="$2"
            shift 2
            ;;
        -i|--interval)
            CHECK_INTERVAL="$2"
            shift 2
            ;;
        -s|--slack-webhook)
            SLACK_WEBHOOK="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Run main function
main "$@"