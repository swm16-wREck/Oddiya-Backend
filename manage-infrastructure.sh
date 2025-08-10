#!/bin/bash

# Oddiya Infrastructure Management Script
# Easily manage your minimal cost AWS infrastructure

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
REGION="ap-northeast-2"
CLUSTER_NAME="oddiya-minimal-cluster"
SERVICE_NAME="oddiya-minimal-app"

# Function to show usage
show_usage() {
    echo -e "${GREEN}Oddiya Infrastructure Manager${NC}"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  destroy-old    - Destroy expensive production infrastructure"
    echo "  deploy-minimal - Deploy cost-optimized minimal infrastructure"
    echo "  start          - Start application (costs ~$0.50/day)"
    echo "  stop           - Stop application (zero cost)"
    echo "  scale <n>      - Scale to n containers"
    echo "  status         - Show current status and costs"
    echo "  costs          - Show current month AWS costs"
    echo "  destroy        - Destroy minimal infrastructure"
    echo ""
    echo "Examples:"
    echo "  $0 deploy-minimal  # Deploy minimal setup"
    echo "  $0 stop           # Stop to save costs"
    echo "  $0 start          # Start when needed"
    echo "  $0 scale 3        # Scale to 3 containers"
}

# Function to destroy old infrastructure
destroy_old() {
    echo -e "${YELLOW}Destroying expensive production infrastructure...${NC}"
    cd terraform
    
    # Force unlock if needed
    if terraform state list &>/dev/null; then
        echo "Found existing resources, destroying..."
        terraform destroy -var="environment=prod" -auto-approve || true
        echo -e "${GREEN}✓ Old infrastructure destroyed${NC}"
    else
        echo -e "${GREEN}✓ No old infrastructure found${NC}"
    fi
}

# Function to deploy minimal infrastructure
deploy_minimal() {
    echo -e "${YELLOW}Deploying minimal cost-optimized infrastructure...${NC}"
    
    # Create minimal directory if it doesn't exist
    mkdir -p terraform/environments/minimal
    cd terraform/environments/minimal
    
    # Initialize and apply
    terraform init
    terraform apply -auto-approve
    
    echo -e "${GREEN}✓ Minimal infrastructure deployed!${NC}"
    echo -e "${BLUE}Note: Application is deployed with 0 containers (zero cost)${NC}"
    echo -e "${BLUE}Run '$0 start' to start the application${NC}"
}

# Function to start application
start_app() {
    echo -e "${YELLOW}Starting application...${NC}"
    
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --desired-count 1 \
        --region "$REGION" \
        --output json > /dev/null
    
    echo -e "${GREEN}✓ Application started!${NC}"
    echo -e "${BLUE}Cost: ~$0.50/day with Fargate Spot${NC}"
    
    # Wait for task to start
    echo -e "${YELLOW}Waiting for container to start...${NC}"
    sleep 30
    
    # Get task ARN
    TASK_ARN=$(aws ecs list-tasks \
        --cluster "$CLUSTER_NAME" \
        --service-name "$SERVICE_NAME" \
        --region "$REGION" \
        --query 'taskArns[0]' \
        --output text)
    
    if [ "$TASK_ARN" != "None" ] && [ -n "$TASK_ARN" ]; then
        # Get public IP
        TASK_DETAILS=$(aws ecs describe-tasks \
            --cluster "$CLUSTER_NAME" \
            --tasks "$TASK_ARN" \
            --region "$REGION" \
            --query 'tasks[0].attachments[0].details')
        
        PUBLIC_IP=$(echo "$TASK_DETAILS" | jq -r '.[] | select(.name=="networkInterfaceId") | .value' | \
            xargs -I {} aws ec2 describe-network-interfaces \
            --network-interface-ids {} \
            --region "$REGION" \
            --query 'NetworkInterfaces[0].Association.PublicIp' \
            --output text 2>/dev/null || echo "IP pending...")
        
        echo -e "${GREEN}Application available at: http://$PUBLIC_IP:8080${NC}"
    fi
}

# Function to stop application
stop_app() {
    echo -e "${YELLOW}Stopping application to save costs...${NC}"
    
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --desired-count 0 \
        --region "$REGION" \
        --output json > /dev/null
    
    echo -e "${GREEN}✓ Application stopped!${NC}"
    echo -e "${BLUE}Cost: $0/day (zero cost when stopped)${NC}"
}

# Function to scale application
scale_app() {
    local count=$1
    echo -e "${YELLOW}Scaling application to $count containers...${NC}"
    
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --desired-count "$count" \
        --region "$REGION" \
        --output json > /dev/null
    
    echo -e "${GREEN}✓ Scaled to $count containers!${NC}"
    
    if [ "$count" -eq 0 ]; then
        echo -e "${BLUE}Cost: $0/day${NC}"
    else
        COST=$(echo "scale=2; $count * 0.5" | bc)
        echo -e "${BLUE}Estimated cost: ~\$$COST/day with Fargate Spot${NC}"
    fi
}

# Function to show status
show_status() {
    echo -e "${GREEN}=== Oddiya Infrastructure Status ===${NC}"
    echo ""
    
    # Check if cluster exists
    if aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$REGION" &>/dev/null; then
        echo -e "${GREEN}✓ Cluster: $CLUSTER_NAME exists${NC}"
        
        # Get service status
        DESIRED_COUNT=$(aws ecs describe-services \
            --cluster "$CLUSTER_NAME" \
            --services "$SERVICE_NAME" \
            --region "$REGION" \
            --query 'services[0].desiredCount' \
            --output text 2>/dev/null || echo "0")
        
        RUNNING_COUNT=$(aws ecs describe-services \
            --cluster "$CLUSTER_NAME" \
            --services "$SERVICE_NAME" \
            --region "$REGION" \
            --query 'services[0].runningCount' \
            --output text 2>/dev/null || echo "0")
        
        echo -e "Desired containers: ${YELLOW}$DESIRED_COUNT${NC}"
        echo -e "Running containers: ${GREEN}$RUNNING_COUNT${NC}"
        
        if [ "$DESIRED_COUNT" -eq 0 ]; then
            echo -e "${GREEN}Status: STOPPED (Zero cost)${NC}"
        else
            COST=$(echo "scale=2; $DESIRED_COUNT * 0.5" | bc)
            echo -e "${YELLOW}Status: RUNNING (~\$$COST/day)${NC}"
        fi
        
        # Show S3 buckets
        echo ""
        echo -e "${BLUE}S3 Buckets:${NC}"
        aws s3 ls | grep oddiya-minimal || echo "  No buckets found"
        
        # Show DynamoDB tables
        echo ""
        echo -e "${BLUE}DynamoDB Tables:${NC}"
        aws dynamodb list-tables --region "$REGION" | jq -r '.TableNames[]' | grep oddiya-minimal || echo "  No tables found"
        
    else
        echo -e "${RED}✗ Infrastructure not deployed${NC}"
        echo -e "Run '$0 deploy-minimal' to deploy"
    fi
}

# Function to show costs
show_costs() {
    echo -e "${GREEN}=== Current Month AWS Costs ===${NC}"
    echo ""
    
    START_DATE=$(date +%Y-%m-01)
    END_DATE=$(date +%Y-%m-%d)
    
    aws ce get-cost-and-usage \
        --time-period Start="$START_DATE",End="$END_DATE" \
        --granularity MONTHLY \
        --metrics "UnblendedCost" \
        --region us-east-1 \
        --output json | jq -r '
        .ResultsByTime[0].Total.UnblendedCost | 
        "Total cost this month: $\(.Amount) \(.Unit)"'
    
    echo ""
    echo -e "${BLUE}Cost by service:${NC}"
    aws ce get-cost-and-usage \
        --time-period Start="$START_DATE",End="$END_DATE" \
        --granularity MONTHLY \
        --metrics "UnblendedCost" \
        --group-by Type=DIMENSION,Key=SERVICE \
        --region us-east-1 \
        --output json | jq -r '
        .ResultsByTime[0].Groups[] | 
        select(.Metrics.UnblendedCost.Amount != "0") |
        "\(.Keys[0]): $\(.Metrics.UnblendedCost.Amount)"' | sort -t: -k2 -rn
}

# Function to destroy minimal infrastructure
destroy_minimal() {
    echo -e "${YELLOW}Destroying minimal infrastructure...${NC}"
    
    read -p "Are you sure? This will delete all resources (yes/no): " confirm
    if [[ "$confirm" != "yes" ]]; then
        echo "Cancelled"
        exit 0
    fi
    
    cd terraform/environments/minimal
    terraform destroy -auto-approve
    
    echo -e "${GREEN}✓ Minimal infrastructure destroyed${NC}"
}

# Main execution
case "$1" in
    destroy-old)
        destroy_old
        ;;
    deploy-minimal)
        deploy_minimal
        ;;
    start)
        start_app
        ;;
    stop)
        stop_app
        ;;
    scale)
        if [ -z "$2" ]; then
            echo -e "${RED}Error: Please specify number of containers${NC}"
            echo "Usage: $0 scale <number>"
            exit 1
        fi
        scale_app "$2"
        ;;
    status)
        show_status
        ;;
    costs)
        show_costs
        ;;
    destroy)
        destroy_minimal
        ;;
    *)
        show_usage
        ;;
esac