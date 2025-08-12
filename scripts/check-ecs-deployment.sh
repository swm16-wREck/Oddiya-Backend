#!/bin/bash

# ECS Deployment Status Checker
# Checks the status of ECS services and troubleshoots deployment issues

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER_NAME="${ECS_CLUSTER:-oddiya-dev}"
SERVICE_NAME="${ECS_SERVICE:-oddiya-dev}"
ECR_REPOSITORY="${ECR_REPOSITORY:-oddiya}"

echo "========================================="
echo "ECS DEPLOYMENT STATUS CHECK"
echo "Region: $REGION"
echo "Cluster: $CLUSTER_NAME"
echo "Service: $SERVICE_NAME"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check if cluster exists
check_cluster() {
    echo -e "${BLUE}Checking ECS Cluster...${NC}"
    
    CLUSTER_STATUS=$(aws ecs describe-clusters \
        --clusters $CLUSTER_NAME \
        --region $REGION \
        --query 'clusters[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$CLUSTER_STATUS" == "ACTIVE" ]; then
        echo -e "${GREEN}✓ Cluster '$CLUSTER_NAME' is ACTIVE${NC}"
        
        # Get cluster details
        CLUSTER_INFO=$(aws ecs describe-clusters \
            --clusters $CLUSTER_NAME \
            --region $REGION \
            --query 'clusters[0].[registeredContainerInstancesCount,runningTasksCount,pendingTasksCount,activeServicesCount]' \
            --output text 2>/dev/null)
        
        echo "  Container Instances: $(echo $CLUSTER_INFO | awk '{print $1}')"
        echo "  Running Tasks: $(echo $CLUSTER_INFO | awk '{print $2}')"
        echo "  Pending Tasks: $(echo $CLUSTER_INFO | awk '{print $3}')"
        echo "  Active Services: $(echo $CLUSTER_INFO | awk '{print $4}')"
    else
        echo -e "${RED}✗ Cluster '$CLUSTER_NAME' not found or not active${NC}"
        return 1
    fi
    echo ""
}

# Check if service exists
check_service() {
    echo -e "${BLUE}Checking ECS Service...${NC}"
    
    SERVICE_INFO=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].[status,desiredCount,runningCount,pendingCount]' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$SERVICE_INFO" != "NOT_FOUND" ] && [ ! -z "$SERVICE_INFO" ]; then
        STATUS=$(echo $SERVICE_INFO | awk '{print $1}')
        DESIRED=$(echo $SERVICE_INFO | awk '{print $2}')
        RUNNING=$(echo $SERVICE_INFO | awk '{print $3}')
        PENDING=$(echo $SERVICE_INFO | awk '{print $4}')
        
        if [ "$STATUS" == "ACTIVE" ]; then
            echo -e "${GREEN}✓ Service '$SERVICE_NAME' is ACTIVE${NC}"
        else
            echo -e "${YELLOW}⚠ Service '$SERVICE_NAME' status: $STATUS${NC}"
        fi
        
        echo "  Desired Count: $DESIRED"
        echo "  Running Count: $RUNNING"
        echo "  Pending Count: $PENDING"
        
        if [ "$RUNNING" -lt "$DESIRED" ]; then
            echo -e "${YELLOW}  ⚠ Service is not at desired capacity${NC}"
        fi
    else
        echo -e "${RED}✗ Service '$SERVICE_NAME' not found${NC}"
        return 1
    fi
    echo ""
}

# Check task definition
check_task_definition() {
    echo -e "${BLUE}Checking Task Definition...${NC}"
    
    # Get latest task definition
    TASK_DEF=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].taskDefinition' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$TASK_DEF" != "NOT_FOUND" ] && [ ! -z "$TASK_DEF" ]; then
        TASK_DEF_NAME=$(echo $TASK_DEF | awk -F'/' '{print $NF}')
        echo -e "${GREEN}✓ Task Definition: $TASK_DEF_NAME${NC}"
        
        # Get task definition details
        TASK_INFO=$(aws ecs describe-task-definition \
            --task-definition $TASK_DEF_NAME \
            --region $REGION \
            --query 'taskDefinition.[cpu,memory,networkMode,requiresCompatibilities[0]]' \
            --output text 2>/dev/null)
        
        echo "  CPU: $(echo $TASK_INFO | awk '{print $1}')"
        echo "  Memory: $(echo $TASK_INFO | awk '{print $2}')"
        echo "  Network Mode: $(echo $TASK_INFO | awk '{print $3}')"
        echo "  Launch Type: $(echo $TASK_INFO | awk '{print $4}')"
    else
        echo -e "${RED}✗ No task definition found for service${NC}"
    fi
    echo ""
}

# Check recent events
check_events() {
    echo -e "${BLUE}Recent Service Events:${NC}"
    
    aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].events[:5].[createdAt,message]' \
        --output text 2>/dev/null | while IFS=$'\t' read -r timestamp message; do
        if [[ "$message" == *"error"* ]] || [[ "$message" == *"failed"* ]] || [[ "$message" == *"unable"* ]]; then
            echo -e "${RED}  $timestamp: $message${NC}"
        elif [[ "$message" == *"progress"* ]] || [[ "$message" == *"started"* ]]; then
            echo -e "${YELLOW}  $timestamp: $message${NC}"
        else
            echo -e "${GREEN}  $timestamp: $message${NC}"
        fi
    done
    echo ""
}

# Check ECR repository
check_ecr() {
    echo -e "${BLUE}Checking ECR Repository...${NC}"
    
    REPO_EXISTS=$(aws ecr describe-repositories \
        --repository-names $ECR_REPOSITORY \
        --region $REGION \
        --query 'repositories[0].repositoryName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$REPO_EXISTS" != "NOT_FOUND" ]; then
        echo -e "${GREEN}✓ ECR Repository '$ECR_REPOSITORY' exists${NC}"
        
        # Get latest images
        IMAGES=$(aws ecr describe-images \
            --repository-name $ECR_REPOSITORY \
            --region $REGION \
            --query 'imageDetails | sort_by(@, &imagePushedAt) | reverse(@) | [:3].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
            --output text 2>/dev/null)
        
        if [ ! -z "$IMAGES" ]; then
            echo "  Recent Images:"
            echo "$IMAGES" | while IFS=$'\t' read -r tag pushed size; do
                SIZE_MB=$(( size / 1024 / 1024 ))
                echo "    Tag: ${tag:-untagged}, Pushed: $pushed, Size: ${SIZE_MB}MB"
            done
        else
            echo -e "${YELLOW}  ⚠ No images found in repository${NC}"
        fi
    else
        echo -e "${RED}✗ ECR Repository '$ECR_REPOSITORY' not found${NC}"
    fi
    echo ""
}

# Check for common issues
check_common_issues() {
    echo -e "${BLUE}Checking for Common Issues...${NC}"
    
    # Check if Fargate or EC2
    LAUNCH_TYPE=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].launchType' \
        --output text 2>/dev/null || echo "UNKNOWN")
    
    if [ "$LAUNCH_TYPE" == "EC2" ]; then
        # Check container instances
        INSTANCES=$(aws ecs list-container-instances \
            --cluster $CLUSTER_NAME \
            --region $REGION \
            --query 'containerInstanceArns' \
            --output text 2>/dev/null)
        
        if [ -z "$INSTANCES" ]; then
            echo -e "${RED}✗ No container instances in cluster (EC2 launch type requires instances)${NC}"
        else
            INSTANCE_COUNT=$(echo "$INSTANCES" | wc -w)
            echo -e "${GREEN}✓ Found $INSTANCE_COUNT container instance(s)${NC}"
        fi
    elif [ "$LAUNCH_TYPE" == "FARGATE" ]; then
        echo -e "${GREEN}✓ Using Fargate launch type (no instances needed)${NC}"
    fi
    
    # Check for failed tasks
    FAILED_TASKS=$(aws ecs list-tasks \
        --cluster $CLUSTER_NAME \
        --service-name $SERVICE_NAME \
        --desired-status STOPPED \
        --region $REGION \
        --query 'taskArns[:3]' \
        --output text 2>/dev/null)
    
    if [ ! -z "$FAILED_TASKS" ]; then
        echo -e "${YELLOW}⚠ Recent stopped tasks found:${NC}"
        for TASK in $FAILED_TASKS; do
            TASK_ID=$(echo $TASK | awk -F'/' '{print $NF}')
            STOP_REASON=$(aws ecs describe-tasks \
                --cluster $CLUSTER_NAME \
                --tasks $TASK \
                --region $REGION \
                --query 'tasks[0].stoppedReason' \
                --output text 2>/dev/null || echo "Unknown")
            
            if [ "$STOP_REASON" != "null" ] && [ "$STOP_REASON" != "Unknown" ]; then
                echo -e "${RED}  Task ${TASK_ID:0:8}: $STOP_REASON${NC}"
            fi
        done
    else
        echo -e "${GREEN}✓ No recently failed tasks${NC}"
    fi
    echo ""
}

# Provide recommendations
provide_recommendations() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}RECOMMENDATIONS${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    # Check if service needs updating
    if [ "$RUNNING" -lt "$DESIRED" ] 2>/dev/null; then
        echo "1. Service is not at desired capacity. Possible solutions:"
        echo "   - Check if there are enough resources (CPU/Memory)"
        echo "   - Review task definition for resource requirements"
        echo "   - Check container instance capacity (if using EC2)"
        echo ""
    fi
    
    # Check for image issues
    if [ "$REPO_EXISTS" == "NOT_FOUND" ]; then
        echo "2. ECR repository not found. Create it with:"
        echo -e "${GREEN}   aws ecr create-repository --repository-name $ECR_REPOSITORY --region $REGION${NC}"
        echo ""
    fi
    
    # Deployment command
    echo "To manually deploy a new version:"
    echo -e "${GREEN}# 1. Build and push Docker image${NC}"
    echo "   docker build -t $ECR_REPOSITORY ."
    echo "   aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin [YOUR_ECR_URI]"
    echo "   docker tag $ECR_REPOSITORY:latest [YOUR_ECR_URI]/$ECR_REPOSITORY:latest"
    echo "   docker push [YOUR_ECR_URI]/$ECR_REPOSITORY:latest"
    echo ""
    echo -e "${GREEN}# 2. Update service with new task definition${NC}"
    echo "   aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_NAME --force-new-deployment --region $REGION"
    echo ""
    
    echo "To check deployment progress:"
    echo -e "${GREEN}   aws ecs wait services-stable --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $REGION${NC}"
    echo ""
}

# Main execution
main() {
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}AWS CLI is not installed. Please install it first.${NC}"
        exit 1
    fi
    
    # Check credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}AWS credentials not configured. Please configure AWS CLI.${NC}"
        exit 1
    fi
    
    # Run checks
    check_cluster || true
    check_service || true
    check_task_definition || true
    check_ecr || true
    check_events || true
    check_common_issues || true
    provide_recommendations
    
    echo -e "${YELLOW}For real-time monitoring, visit:${NC}"
    echo "https://console.aws.amazon.com/ecs/home?region=$REGION#/clusters/$CLUSTER_NAME/services/$SERVICE_NAME/events"
}

# Run main function
main