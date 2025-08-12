#!/bin/bash

# Automated ECS Deployment Troubleshooter
# Agent 6 - Comprehensive troubleshooting automation for ECS deployment issues
# Specifically designed to prevent and diagnose 28+ minute timeout issues

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER_NAME="${ECS_CLUSTER:-oddiya-dev}"
SERVICE_NAME="${ECS_SERVICE:-oddiya-dev}"
ECR_REPOSITORY="${ECR_REPOSITORY:-oddiya}"
TIMEOUT_THRESHOLD="${TIMEOUT_THRESHOLD:-1800}" # 30 minutes in seconds
LOG_GROUP="/ecs/$SERVICE_NAME"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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
WRENCH_ICON="🔧"
MAGNIFIER_ICON="🔍"

# Logging
LOG_FILE="/tmp/troubleshooter-$(date +%Y%m%d-%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2> >(tee -a "$LOG_FILE" >&2)

# Initialize troubleshooter
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${BLUE}${BOLD}${WRENCH_ICON} AUTOMATED ECS TROUBLESHOOTER${NC}"
echo -e "${BLUE}${BOLD}Agent 6 - Deployment Issue Resolution${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${CYAN}Region: $REGION${NC}"
echo -e "${CYAN}Cluster: $CLUSTER_NAME${NC}"
echo -e "${CYAN}Service: $SERVICE_NAME${NC}"
echo -e "${CYAN}Started: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${CYAN}Log: $LOG_FILE${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo ""

# Utility functions
log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

run_check() {
    local check_name="$1"
    local check_function="$2"
    
    echo -e "${BLUE}${BOLD}${MAGNIFIER_ICON} Running Check: $check_name${NC}"
    echo "----------------------------------------"
    
    if $check_function; then
        echo -e "${GREEN}${SUCCESS_ICON} Check Passed: $check_name${NC}"
        return 0
    else
        echo -e "${RED}${ERROR_ICON} Check Failed: $check_name${NC}"
        return 1
    fi
    echo ""
}

auto_fix() {
    local issue_type="$1"
    local fix_function="$2"
    
    echo -e "${YELLOW}${WRENCH_ICON} Auto-fixing: $issue_type${NC}"
    if $fix_function; then
        echo -e "${GREEN}${SUCCESS_ICON} Auto-fix successful: $issue_type${NC}"
        return 0
    else
        echo -e "${RED}${ERROR_ICON} Auto-fix failed: $issue_type${NC}"
        return 1
    fi
}

# Check 1: Prerequisites and Setup
check_prerequisites() {
    local issues=0
    
    # AWS CLI
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}  ${ERROR_ICON} AWS CLI not installed${NC}"
        issues=$((issues + 1))
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} AWS CLI installed${NC}"
    fi
    
    # AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}  ${ERROR_ICON} AWS credentials not configured${NC}"
        issues=$((issues + 1))
    else
        local account_id=$(aws sts get-caller-identity --query Account --output text)
        echo -e "${GREEN}  ${SUCCESS_ICON} AWS credentials configured (Account: $account_id)${NC}"
    fi
    
    # jq for JSON parsing
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}  ${ERROR_ICON} jq not installed${NC}"
        issues=$((issues + 1))
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} jq installed${NC}"
    fi
    
    # Docker for image operations
    if ! command -v docker &> /dev/null; then
        echo -e "${YELLOW}  ${WARNING_ICON} Docker not installed (needed for image builds)${NC}"
    else
        if docker info &> /dev/null; then
            echo -e "${GREEN}  ${SUCCESS_ICON} Docker installed and running${NC}"
        else
            echo -e "${YELLOW}  ${WARNING_ICON} Docker installed but not running${NC}"
        fi
    fi
    
    return $issues
}

# Check 2: ECS Cluster Health
check_cluster_health() {
    local cluster_status
    cluster_status=$(aws ecs describe-clusters \
        --clusters "$CLUSTER_NAME" \
        --region "$REGION" \
        --query 'clusters[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$cluster_status" != "ACTIVE" ]; then
        echo -e "${RED}  ${ERROR_ICON} Cluster not active or not found: $cluster_status${NC}"
        return 1
    fi
    
    # Get cluster details
    local cluster_info
    cluster_info=$(aws ecs describe-clusters \
        --clusters "$CLUSTER_NAME" \
        --region "$REGION" \
        --query 'clusters[0].[registeredContainerInstancesCount,runningTasksCount,pendingTasksCount,activeServicesCount]' \
        --output text 2>/dev/null)
    
    local instances running pending services
    instances=$(echo "$cluster_info" | awk '{print $1}')
    running=$(echo "$cluster_info" | awk '{print $2}')
    pending=$(echo "$cluster_info" | awk '{print $3}')
    services=$(echo "$cluster_info" | awk '{print $4}')
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Cluster Status: ACTIVE${NC}"
    echo -e "${CYAN}  Container Instances: $instances${NC}"
    echo -e "${CYAN}  Running Tasks: $running${NC}"
    echo -e "${CYAN}  Pending Tasks: $pending${NC}"
    echo -e "${CYAN}  Active Services: $services${NC}"
    
    # Check for excessive pending tasks (timeout indicator)
    if [ "$pending" -gt 0 ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} $pending tasks are pending - potential deployment delay${NC}"
        
        # Check how long tasks have been pending
        local pending_tasks
        pending_tasks=$(aws ecs list-tasks \
            --cluster "$CLUSTER_NAME" \
            --service-name "$SERVICE_NAME" \
            --desired-status RUNNING \
            --region "$REGION" \
            --query 'taskArns[:3]' \
            --output text 2>/dev/null)
        
        if [ -n "$pending_tasks" ]; then
            for task_arn in $pending_tasks; do
                local task_info
                task_info=$(aws ecs describe-tasks \
                    --cluster "$CLUSTER_NAME" \
                    --tasks "$task_arn" \
                    --region "$REGION" \
                    --query 'tasks[0].[lastStatus,createdAt]' \
                    --output text 2>/dev/null)
                
                local status created_at
                status=$(echo "$task_info" | awk '{print $1}')
                created_at=$(echo "$task_info" | awk '{print $2}')
                
                if [ "$status" == "PENDING" ]; then
                    local created_timestamp
                    created_timestamp=$(date -d "$created_at" +%s 2>/dev/null || echo "0")
                    local current_timestamp
                    current_timestamp=$(date +%s)
                    local duration
                    duration=$((current_timestamp - created_timestamp))
                    
                    if [ "$duration" -gt "$TIMEOUT_THRESHOLD" ]; then
                        echo -e "${RED}  ${ERROR_ICON} Task pending for ${duration}s (>${TIMEOUT_THRESHOLD}s threshold)${NC}"
                        return 1
                    else
                        echo -e "${YELLOW}  ${WARNING_ICON} Task pending for ${duration}s${NC}"
                    fi
                fi
            done
        fi
    fi
    
    return 0
}

# Check 3: Service Health and Configuration
check_service_health() {
    local service_info
    service_info=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0]' \
        --output json 2>/dev/null)
    
    if [ "$service_info" == "null" ] || [ -z "$service_info" ]; then
        echo -e "${RED}  ${ERROR_ICON} Service not found${NC}"
        return 1
    fi
    
    local status desired running pending
    status=$(echo "$service_info" | jq -r '.status')
    desired=$(echo "$service_info" | jq -r '.desiredCount')
    running=$(echo "$service_info" | jq -r '.runningCount')
    pending=$(echo "$service_info" | jq -r '.pendingCount')
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Service Status: $status${NC}"
    echo -e "${CYAN}  Desired Count: $desired${NC}"
    echo -e "${CYAN}  Running Count: $running${NC}"
    echo -e "${CYAN}  Pending Count: $pending${NC}"
    
    # Check service scaling
    if [ "$running" -lt "$desired" ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} Service not at desired capacity${NC}"
        
        # Analyze deployment status
        local deployments
        deployments=$(echo "$service_info" | jq -r '.deployments[] | select(.status == "PRIMARY") | [.status, .taskDefinition, .createdAt] | @csv')
        
        if [ -n "$deployments" ]; then
            echo -e "${CYAN}  Active Deployment:${NC}"
            echo "$deployments" | while IFS=',' read -r deploy_status task_def created; do
                created_clean=$(echo "$created" | tr -d '"')
                echo -e "${CYAN}    Status: $(echo "$deploy_status" | tr -d '"')${NC}"
                echo -e "${CYAN}    Task Def: $(echo "$task_def" | tr -d '"')${NC}"
                echo -e "${CYAN}    Created: $created_clean${NC}"
            done
        fi
    fi
    
    # Check recent service events for errors
    local events
    events=$(echo "$service_info" | jq -r '.events[:5][] | [.createdAt, .message] | @csv')
    
    echo -e "${CYAN}  Recent Events:${NC}"
    echo "$events" | while IFS=',' read -r timestamp message; do
        timestamp_clean=$(echo "$timestamp" | tr -d '"')
        message_clean=$(echo "$message" | tr -d '"')
        
        if [[ "$message_clean" == *"error"* ]] || [[ "$message_clean" == *"failed"* ]]; then
            echo -e "${RED}    ${ERROR_ICON} $timestamp_clean: $message_clean${NC}"
        elif [[ "$message_clean" == *"unable to place"* ]]; then
            echo -e "${RED}    ${ERROR_ICON} RESOURCE CONSTRAINT: $message_clean${NC}"
            return 1
        else
            echo -e "${CYAN}    ${INFO_ICON} $timestamp_clean: $message_clean${NC}"
        fi
    done
    
    return 0
}

# Check 4: Task Definition Issues
check_task_definition() {
    local task_def_arn
    task_def_arn=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0].taskDefinition' \
        --output text 2>/dev/null)
    
    if [ -z "$task_def_arn" ] || [ "$task_def_arn" == "None" ]; then
        echo -e "${RED}  ${ERROR_ICON} No task definition found${NC}"
        return 1
    fi
    
    local task_def_info
    task_def_info=$(aws ecs describe-task-definition \
        --task-definition "$task_def_arn" \
        --region "$REGION" \
        --query 'taskDefinition' \
        --output json 2>/dev/null)
    
    local cpu memory network_mode compatibility
    cpu=$(echo "$task_def_info" | jq -r '.cpu // "256"')
    memory=$(echo "$task_def_info" | jq -r '.memory // "512"')
    network_mode=$(echo "$task_def_info" | jq -r '.networkMode // "bridge"')
    compatibility=$(echo "$task_def_info" | jq -r '.requiresCompatibilities[0] // "EC2"')
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Task Definition: $(basename "$task_def_arn")${NC}"
    echo -e "${CYAN}  CPU: $cpu${NC}"
    echo -e "${CYAN}  Memory: $memory${NC}"
    echo -e "${CYAN}  Network Mode: $network_mode${NC}"
    echo -e "${CYAN}  Compatibility: $compatibility${NC}"
    
    # Check resource allocation
    if [ "$compatibility" == "FARGATE" ]; then
        # Validate Fargate CPU/Memory combinations
        case "$cpu" in
            "256")
                if [[ ! "$memory" =~ ^(512|1024|2048)$ ]]; then
                    echo -e "${RED}  ${ERROR_ICON} Invalid CPU/Memory combination for Fargate${NC}"
                    return 1
                fi
                ;;
            "512")
                if [[ ! "$memory" =~ ^(1024|2048|3072|4096)$ ]]; then
                    echo -e "${RED}  ${ERROR_ICON} Invalid CPU/Memory combination for Fargate${NC}"
                    return 1
                fi
                ;;
        esac
    fi
    
    # Check container definitions
    local container_count
    container_count=$(echo "$task_def_info" | jq '.containerDefinitions | length')
    echo -e "${CYAN}  Containers: $container_count${NC}"
    
    # Check for health check configuration
    local has_health_check
    has_health_check=$(echo "$task_def_info" | jq '.containerDefinitions[0].healthCheck != null')
    
    if [ "$has_health_check" == "true" ]; then
        echo -e "${GREEN}  ${SUCCESS_ICON} Health check configured${NC}"
    else
        echo -e "${YELLOW}  ${WARNING_ICON} No health check configured${NC}"
    fi
    
    # Check execution role
    local execution_role
    execution_role=$(echo "$task_def_info" | jq -r '.executionRoleArn // "none"')
    
    if [ "$execution_role" == "none" ]; then
        echo -e "${RED}  ${ERROR_ICON} No execution role configured${NC}"
        return 1
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} Execution role: $(basename "$execution_role")${NC}"
    fi
    
    return 0
}

# Check 5: ECR Repository and Image Availability
check_ecr_status() {
    local repo_exists
    repo_exists=$(aws ecr describe-repositories \
        --repository-names "$ECR_REPOSITORY" \
        --region "$REGION" \
        --query 'repositories[0].repositoryName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$repo_exists" == "NOT_FOUND" ]; then
        echo -e "${RED}  ${ERROR_ICON} ECR repository not found${NC}"
        return 1
    fi
    
    echo -e "${GREEN}  ${SUCCESS_ICON} ECR Repository: $repo_exists${NC}"
    
    # Check for available images
    local images
    images=$(aws ecr describe-images \
        --repository-name "$ECR_REPOSITORY" \
        --region "$REGION" \
        --query 'imageDetails | sort_by(@, &imagePushedAt) | reverse(@) | [:3]' \
        --output json 2>/dev/null)
    
    local image_count
    image_count=$(echo "$images" | jq 'length')
    
    if [ "$image_count" -eq 0 ]; then
        echo -e "${RED}  ${ERROR_ICON} No images found in repository${NC}"
        return 1
    fi
    
    echo -e "${CYAN}  Available Images: $image_count${NC}"
    
    # Show recent images
    echo "$images" | jq -r '.[] | [(.imageTags[0] // "untagged"), .imagePushedAt, (.imageSizeInBytes / 1024 / 1024 | round)] | @csv' | \
    while IFS=',' read -r tag pushed size; do
        tag_clean=$(echo "$tag" | tr -d '"')
        pushed_clean=$(echo "$pushed" | tr -d '"')
        size_clean=$(echo "$size" | tr -d '"')
        echo -e "${CYAN}    Tag: $tag_clean, Pushed: $pushed_clean, Size: ${size_clean}MB${NC}"
    done
    
    # Verify the image being used by the service
    local current_image
    current_image=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0].taskDefinition' \
        --output text 2>/dev/null | xargs -I {} aws ecs describe-task-definition \
        --task-definition {} \
        --region "$REGION" \
        --query 'taskDefinition.containerDefinitions[0].image' \
        --output text 2>/dev/null)
    
    if [ -n "$current_image" ]; then
        echo -e "${CYAN}  Service Image: $current_image${NC}"
        
        # Check if image exists in ECR
        local image_tag
        image_tag=$(echo "$current_image" | cut -d':' -f2)
        local image_exists
        image_exists=$(echo "$images" | jq -r --arg tag "$image_tag" '.[] | select(.imageTags[]? == $tag) | .imageDigest')
        
        if [ -n "$image_exists" ]; then
            echo -e "${GREEN}  ${SUCCESS_ICON} Service image exists in ECR${NC}"
        else
            echo -e "${RED}  ${ERROR_ICON} Service image not found in ECR${NC}"
            return 1
        fi
    fi
    
    return 0
}

# Check 6: Network Configuration and Security Groups
check_network_config() {
    # Get service network configuration
    local network_config
    network_config=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'services[0].networkConfiguration.awsvpcConfiguration' \
        --output json 2>/dev/null)
    
    if [ "$network_config" == "null" ] || [ -z "$network_config" ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} No network configuration (bridge mode)${NC}"
        return 0
    fi
    
    local subnets security_groups assign_public_ip
    subnets=$(echo "$network_config" | jq -r '.subnets[]' | tr '\n' ' ')
    security_groups=$(echo "$network_config" | jq -r '.securityGroups[]' | tr '\n' ' ')
    assign_public_ip=$(echo "$network_config" | jq -r '.assignPublicIp // "DISABLED"')
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Network Configuration (awsvpc)${NC}"
    echo -e "${CYAN}  Subnets: $subnets${NC}"
    echo -e "${CYAN}  Security Groups: $security_groups${NC}"
    echo -e "${CYAN}  Public IP: $assign_public_ip${NC}"
    
    # Check subnet availability zones
    for subnet_id in $subnets; do
        local subnet_info
        subnet_info=$(aws ec2 describe-subnets \
            --subnet-ids "$subnet_id" \
            --region "$REGION" \
            --query 'Subnets[0].[AvailabilityZone,State,AvailableIpAddressCount]' \
            --output text 2>/dev/null)
        
        local az state available_ips
        az=$(echo "$subnet_info" | awk '{print $1}')
        state=$(echo "$subnet_info" | awk '{print $2}')
        available_ips=$(echo "$subnet_info" | awk '{print $3}')
        
        if [ "$state" != "available" ]; then
            echo -e "${RED}  ${ERROR_ICON} Subnet $subnet_id is not available${NC}"
            return 1
        fi
        
        if [ "$available_ips" -lt 10 ]; then
            echo -e "${YELLOW}  ${WARNING_ICON} Subnet $subnet_id has low IP availability: $available_ips${NC}"
        fi
        
        echo -e "${CYAN}    Subnet $subnet_id: AZ=$az, IPs=$available_ips${NC}"
    done
    
    # Check security group rules
    for sg_id in $security_groups; do
        local sg_rules
        sg_rules=$(aws ec2 describe-security-groups \
            --group-ids "$sg_id" \
            --region "$REGION" \
            --query 'SecurityGroups[0].IpPermissions' \
            --output json 2>/dev/null)
        
        local rule_count
        rule_count=$(echo "$sg_rules" | jq 'length')
        echo -e "${CYAN}    Security Group $sg_id: $rule_count inbound rules${NC}"
        
        # Check for health check port (8080)
        local has_health_port
        has_health_port=$(echo "$sg_rules" | jq --arg port "8080" '.[] | select(.FromPort == ($port | tonumber)) | length > 0')
        
        if [ "$has_health_port" != "true" ]; then
            echo -e "${YELLOW}  ${WARNING_ICON} Security group may not allow health check on port 8080${NC}"
        fi
    done
    
    return 0
}

# Check 7: Load Balancer and Target Group Health
check_load_balancer() {
    # Find target groups associated with the service
    local target_groups
    target_groups=$(aws elbv2 describe-target-groups \
        --region "$REGION" \
        --query "TargetGroups[?contains(TargetGroupName, '$SERVICE_NAME')] | [0].TargetGroupArn" \
        --output text 2>/dev/null)
    
    if [ -z "$target_groups" ] || [ "$target_groups" == "None" ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} No target groups found for service${NC}"
        return 0
    fi
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Target Group Found${NC}"
    
    # Get target group health
    local target_health
    target_health=$(aws elbv2 describe-target-health \
        --target-group-arn "$target_groups" \
        --region "$REGION" \
        --output json 2>/dev/null)
    
    local healthy_count unhealthy_count
    healthy_count=$(echo "$target_health" | jq '[.TargetHealthDescriptions[] | select(.TargetHealth.State == "healthy")] | length')
    unhealthy_count=$(echo "$target_health" | jq '[.TargetHealthDescriptions[] | select(.TargetHealth.State != "healthy")] | length')
    
    echo -e "${CYAN}  Healthy Targets: $healthy_count${NC}"
    echo -e "${CYAN}  Unhealthy Targets: $unhealthy_count${NC}"
    
    if [ "$unhealthy_count" -gt 0 ]; then
        echo -e "${RED}  ${ERROR_ICON} Unhealthy targets detected${NC}"
        
        # Show unhealthy target details
        echo "$target_health" | jq -r '.TargetHealthDescriptions[] | select(.TargetHealth.State != "healthy") | [.Target.Id, .TargetHealth.State, .TargetHealth.Reason] | @csv' | \
        while IFS=',' read -r target_id state reason; do
            target_clean=$(echo "$target_id" | tr -d '"')
            state_clean=$(echo "$state" | tr -d '"')
            reason_clean=$(echo "$reason" | tr -d '"')
            echo -e "${RED}    Target ${target_clean}: $state_clean ($reason_clean)${NC}"
        done
        
        return 1
    fi
    
    # Check health check configuration
    local health_check_path health_check_port
    health_check_path=$(aws elbv2 describe-target-groups \
        --target-group-arns "$target_groups" \
        --region "$REGION" \
        --query 'TargetGroups[0].HealthCheckPath' \
        --output text 2>/dev/null)
    health_check_port=$(aws elbv2 describe-target-groups \
        --target-group-arns "$target_groups" \
        --region "$REGION" \
        --query 'TargetGroups[0].HealthCheckPort' \
        --output text 2>/dev/null)
    
    echo -e "${CYAN}  Health Check Path: $health_check_path${NC}"
    echo -e "${CYAN}  Health Check Port: $health_check_port${NC}"
    
    return 0
}

# Check 8: Application Logs and Errors
check_application_logs() {
    echo -e "${BLUE}Analyzing recent application logs...${NC}"
    
    # Check if log group exists
    local log_group_exists
    log_group_exists=$(aws logs describe-log-groups \
        --log-group-name-prefix "$LOG_GROUP" \
        --region "$REGION" \
        --query 'logGroups[0].logGroupName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$log_group_exists" == "NOT_FOUND" ]; then
        echo -e "${RED}  ${ERROR_ICON} Log group not found: $LOG_GROUP${NC}"
        return 1
    fi
    
    echo -e "${GREEN}  ${SUCCESS_ICON} Log group exists: $LOG_GROUP${NC}"
    
    # Get recent error logs (last 10 minutes)
    local start_time
    start_time=$(date -d '10 minutes ago' +%s)000
    
    local error_logs
    error_logs=$(aws logs filter-log-events \
        --log-group-name "$LOG_GROUP" \
        --region "$REGION" \
        --start-time "$start_time" \
        --filter-pattern "ERROR" \
        --query 'events[*].message' \
        --output json 2>/dev/null)
    
    local error_count
    error_count=$(echo "$error_logs" | jq 'length')
    
    if [ "$error_count" -gt 0 ]; then
        echo -e "${RED}  ${ERROR_ICON} $error_count errors found in last 10 minutes${NC}"
        
        # Show recent errors (limit to 5)
        echo "$error_logs" | jq -r '.[:5][]' | while IFS= read -r error; do
            echo -e "${RED}    ERROR: $error${NC}"
        done
        
        # Check for specific timeout-related errors
        local timeout_errors
        timeout_errors=$(echo "$error_logs" | jq -r '.[] | select(contains("timeout") or contains("Timeout") or contains("TIMEOUT"))')
        
        if [ -n "$timeout_errors" ]; then
            echo -e "${RED}  ${ERROR_ICON} Timeout-related errors detected${NC}"
            return 1
        fi
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No errors in last 10 minutes${NC}"
    fi
    
    # Check for health check failures
    local health_failures
    health_failures=$(aws logs filter-log-events \
        --log-group-name "$LOG_GROUP" \
        --region "$REGION" \
        --start-time "$start_time" \
        --filter-pattern "health" \
        --query 'events[*].message' \
        --output json 2>/dev/null)
    
    local health_failure_count
    health_failure_count=$(echo "$health_failures" | jq 'length')
    
    if [ "$health_failure_count" -gt 0 ]; then
        echo -e "${CYAN}  ${INFO_ICON} $health_failure_count health-related log entries${NC}"
    fi
    
    return 0
}

# Auto-fix functions
fix_create_ecr_repository() {
    echo "Creating ECR repository: $ECR_REPOSITORY"
    aws ecr create-repository \
        --repository-name "$ECR_REPOSITORY" \
        --region "$REGION" \
        --image-scanning-configuration scanOnPush=true \
        --encryption-configuration encryptionType=AES256 >/dev/null 2>&1
}

fix_create_execution_role() {
    echo "Creating ECS task execution role"
    cat > /tmp/trust-policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ecs-tasks.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF
    
    aws iam create-role \
        --role-name ecsTaskExecutionRole \
        --assume-role-policy-document file:///tmp/trust-policy.json >/dev/null 2>&1
    
    aws iam attach-role-policy \
        --role-name ecsTaskExecutionRole \
        --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy >/dev/null 2>&1
}

fix_restart_service() {
    echo "Forcing service deployment restart"
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --force-new-deployment \
        --region "$REGION" >/dev/null 2>&1
}

fix_create_log_group() {
    echo "Creating CloudWatch log group"
    aws logs create-log-group \
        --log-group-name "$LOG_GROUP" \
        --region "$REGION" 2>/dev/null || true
}

# Main troubleshooting flow
run_comprehensive_diagnosis() {
    local total_checks=8
    local passed_checks=0
    local failed_checks=0
    local issues=()
    
    echo -e "${BLUE}${BOLD}Running Comprehensive Diagnosis...${NC}"
    echo -e "${BLUE}Progress: [                    ] 0%${NC}"
    echo ""
    
    # Check 1: Prerequisites
    if run_check "Prerequisites and Setup" check_prerequisites; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("Prerequisites missing or misconfigured")
    fi
    
    # Check 2: Cluster Health
    if run_check "ECS Cluster Health" check_cluster_health; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("ECS cluster issues or timeout detected")
    fi
    
    # Check 3: Service Health
    if run_check "Service Health and Configuration" check_service_health; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("ECS service configuration problems")
    fi
    
    # Check 4: Task Definition
    if run_check "Task Definition Validation" check_task_definition; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("Task definition configuration errors")
    fi
    
    # Check 5: ECR Status
    if run_check "ECR Repository and Images" check_ecr_status; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("ECR repository or image problems")
        
        # Auto-fix ECR repository
        auto_fix "ECR Repository Creation" fix_create_ecr_repository
    fi
    
    # Check 6: Network Configuration
    if run_check "Network Configuration" check_network_config; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("Network or security group configuration issues")
    fi
    
    # Check 7: Load Balancer
    if run_check "Load Balancer and Target Health" check_load_balancer; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("Load balancer or target group health issues")
    fi
    
    # Check 8: Application Logs
    if run_check "Application Logs and Errors" check_application_logs; then
        passed_checks=$((passed_checks + 1))
    else
        failed_checks=$((failed_checks + 1))
        issues+=("Application errors or timeout issues in logs")
        
        # Auto-fix log group
        auto_fix "CloudWatch Log Group Creation" fix_create_log_group
    fi
    
    # Summary
    echo -e "${BLUE}${BOLD}=========================================${NC}"
    echo -e "${BLUE}${BOLD}DIAGNOSIS SUMMARY${NC}"
    echo -e "${BLUE}${BOLD}=========================================${NC}"
    echo -e "${GREEN}Passed Checks: $passed_checks/$total_checks${NC}"
    echo -e "${RED}Failed Checks: $failed_checks/$total_checks${NC}"
    echo ""
    
    if [ ${#issues[@]} -gt 0 ]; then
        echo -e "${RED}${BOLD}Issues Identified:${NC}"
        for issue in "${issues[@]}"; do
            echo -e "${RED}  ${ERROR_ICON} $issue${NC}"
        done
        echo ""
    fi
    
    # Provide specific recommendations
    provide_troubleshooting_recommendations
    
    # Return status
    if [ "$failed_checks" -eq 0 ]; then
        echo -e "${GREEN}${BOLD}${SUCCESS_ICON} All checks passed - system appears healthy${NC}"
        return 0
    else
        echo -e "${RED}${BOLD}${ERROR_ICON} $failed_checks issues require attention${NC}"
        return 1
    fi
}

provide_troubleshooting_recommendations() {
    echo -e "${BLUE}${BOLD}TROUBLESHOOTING RECOMMENDATIONS${NC}"
    echo -e "${BLUE}${BOLD}=========================================${NC}"
    
    echo -e "${YELLOW}1. Immediate Actions:${NC}"
    echo -e "${CYAN}   • Check deployment status: ./scripts/deployment-monitor.sh${NC}"
    echo -e "${CYAN}   • Analyze logs: ./scripts/log-analyzer.sh${NC}"
    echo -e "${CYAN}   • Force service restart: aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_NAME --force-new-deployment${NC}"
    echo ""
    
    echo -e "${YELLOW}2. For 28+ Minute Timeout Issues:${NC}"
    echo -e "${CYAN}   • Resource Constraints: Increase CPU/Memory in task definition${NC}"
    echo -e "${CYAN}   • Image Issues: Verify ECR image exists and is accessible${NC}"
    echo -e "${CYAN}   • Health Check: Ensure /actuator/health endpoint responds${NC}"
    echo -e "${CYAN}   • Network: Check security groups allow ALB→ECS communication${NC}"
    echo ""
    
    echo -e "${YELLOW}3. Automated Fixes Available:${NC}"
    echo -e "${CYAN}   • ECR Repository: ./scripts/fix-ecs-deployment.sh${NC}"
    echo -e "${CYAN}   • Complete Fix: ./scripts/fix-ecs-deployment.sh${NC}"
    echo -e "${CYAN}   • Infrastructure: cd terraform && terraform apply${NC}"
    echo ""
    
    echo -e "${YELLOW}4. Monitoring and Prevention:${NC}"
    echo -e "${CYAN}   • Setup APM: ./scripts/setup-apm.sh${NC}"
    echo -e "${CYAN}   • Monitor Deployments: ./scripts/deployment-monitor.sh${NC}"
    echo -e "${CYAN}   • CI/CD Integration: Use .github/workflows/deploy-with-monitoring.yml${NC}"
    echo ""
    
    echo -e "${YELLOW}5. Emergency Recovery:${NC}"
    echo -e "${CYAN}   • Rollback: aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_NAME --task-definition <previous-task-def>${NC}"
    echo -e "${CYAN}   • Scale Down/Up: aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_NAME --desired-count 0${NC}"
    echo -e "${CYAN}   • Complete Reset: ./scripts/fix-ecs-deployment.sh${NC}"
    echo ""
    
    echo -e "${YELLOW}6. Get Help:${NC}"
    echo -e "${CYAN}   • Log File: $LOG_FILE${NC}"
    echo -e "${CYAN}   • Deployment Guide: ./DEPLOYMENT.md${NC}"
    echo -e "${CYAN}   • Monitoring Guide: ./MONITORING_AND_OBSERVABILITY.md${NC}"
    echo -e "${CYAN}   • Troubleshooting Runbook: ./docs/deployment-troubleshooting-runbook.md${NC}"
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Automated ECS Deployment Troubleshooter"
    echo "Diagnoses and fixes common deployment issues, especially 28+ minute timeouts"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -q, --quick             Run quick diagnosis (essential checks only)"
    echo "  -f, --fix               Attempt automatic fixes for detected issues"
    echo "  -v, --verbose           Enable verbose output"
    echo "  --timeout SECONDS       Set timeout threshold (default: 1800s/30min)"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION              AWS region (default: ap-northeast-2)"
    echo "  ECS_CLUSTER             ECS cluster name (default: oddiya-dev)"
    echo "  ECS_SERVICE             ECS service name (default: oddiya-dev)"
    echo "  ECR_REPOSITORY          ECR repository name (default: oddiya)"
    echo ""
    echo "Examples:"
    echo "  $0                      # Full diagnosis"
    echo "  $0 -q                   # Quick check"
    echo "  $0 -f                   # Diagnosis with auto-fixes"
    echo "  $0 --timeout 2700       # 45-minute timeout threshold"
    echo ""
    echo "Related Tools:"
    echo "  ./scripts/deployment-monitor.sh     # Real-time deployment monitoring"
    echo "  ./scripts/log-analyzer.sh          # Log analysis and error detection"
    echo "  ./scripts/fix-ecs-deployment.sh    # Automated deployment fixes"
    echo "  ./scripts/setup-apm.sh             # Monitoring setup"
}

# Parse command line arguments
QUICK_MODE=false
AUTO_FIX=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -q|--quick)
            QUICK_MODE=true
            shift
            ;;
        -f|--fix)
            AUTO_FIX=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        --timeout)
            TIMEOUT_THRESHOLD="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    if $QUICK_MODE; then
        echo -e "${YELLOW}Running quick diagnosis mode...${NC}"
        # Run essential checks only
        run_check "Prerequisites" check_prerequisites
        run_check "Service Health" check_service_health
        run_check "ECR Status" check_ecr_status
    else
        # Run comprehensive diagnosis
        run_comprehensive_diagnosis
    fi
}

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}${INFO_ICON} Troubleshooting stopped by user${NC}"
    echo "Log saved to: $LOG_FILE"
    exit 130
}

trap cleanup SIGINT SIGTERM

# Run main function
main "$@"