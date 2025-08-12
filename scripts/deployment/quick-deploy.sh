#!/bin/bash

# Quick Deploy Script
# Builds and deploys the application to ECS in one command

set -e

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"

echo "========================================="
echo "QUICK DEPLOY TO ECS"
echo "Region: $REGION"
echo "Project: $PROJECT_NAME"
echo "Environment: $ENVIRONMENT"
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check if infrastructure exists
check_infrastructure() {
    echo -e "${BLUE}Checking ECS infrastructure...${NC}"
    
    CLUSTER_EXISTS=$(aws ecs describe-clusters \
        --clusters "${PROJECT_NAME}-${ENVIRONMENT}" \
        --region $REGION \
        --query 'clusters[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$CLUSTER_EXISTS" != "ACTIVE" ]; then
        echo -e "${YELLOW}⚠ ECS infrastructure not found or inactive${NC}"
        echo "Deploying infrastructure first..."
        ./scripts/deploy-infrastructure.sh
        echo ""
    else
        echo -e "${GREEN}✓ ECS infrastructure is ready${NC}"
    fi
    echo ""
}

# Get ECR repository URL
get_ecr_url() {
    echo -e "${BLUE}Getting ECR repository URL...${NC}"
    
    ECR_URL=$(aws ecr describe-repositories \
        --repository-names $PROJECT_NAME \
        --region $REGION \
        --query 'repositories[0].repositoryUri' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$ECR_URL" == "NOT_FOUND" ]; then
        echo -e "${RED}✗ ECR repository not found${NC}"
        echo "Please run infrastructure deployment first:"
        echo "  ./scripts/deploy-infrastructure.sh"
        exit 1
    fi
    
    echo "ECR URL: $ECR_URL"
    echo ""
}

# Build application
build_application() {
    echo -e "${BLUE}Building application...${NC}"
    
    # Clean and build
    ./gradlew clean build -x test
    
    echo -e "${GREEN}✓ Application built successfully${NC}"
    echo ""
}

# Build and push Docker image
build_and_push_image() {
    echo -e "${BLUE}Building and pushing Docker image...${NC}"
    
    # Login to ECR
    aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_URL
    
    # Build image
    docker build -t $PROJECT_NAME .
    
    # Tag with commit hash if in git repo
    if git rev-parse --git-dir > /dev/null 2>&1; then
        COMMIT_HASH=$(git rev-parse --short HEAD)
        docker tag $PROJECT_NAME:latest $ECR_URL:$COMMIT_HASH
        docker push $ECR_URL:$COMMIT_HASH
        echo "Pushed with tag: $COMMIT_HASH"
    fi
    
    # Tag and push latest
    docker tag $PROJECT_NAME:latest $ECR_URL:latest
    docker push $ECR_URL:latest
    
    echo -e "${GREEN}✓ Docker image pushed successfully${NC}"
    echo ""
}

# Update ECS service
update_ecs_service() {
    echo -e "${BLUE}Updating ECS service...${NC}"
    
    # Force new deployment
    aws ecs update-service \
        --cluster "${PROJECT_NAME}-${ENVIRONMENT}" \
        --service "${PROJECT_NAME}-${ENVIRONMENT}" \
        --force-new-deployment \
        --region $REGION > /dev/null
    
    echo -e "${GREEN}✓ ECS service update triggered${NC}"
    echo ""
}

# Wait for deployment to complete
wait_for_deployment() {
    echo -e "${BLUE}Waiting for deployment to complete...${NC}"
    
    # Wait up to 10 minutes
    timeout=600
    elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        STATUS=$(aws ecs describe-services \
            --cluster "${PROJECT_NAME}-${ENVIRONMENT}" \
            --services "${PROJECT_NAME}-${ENVIRONMENT}" \
            --region $REGION \
            --query 'services[0].[runningCount,desiredCount]' \
            --output text 2>/dev/null || echo "0 0")
        
        RUNNING=$(echo $STATUS | awk '{print $1}')
        DESIRED=$(echo $STATUS | awk '{print $2}')
        
        if [ "$RUNNING" == "$DESIRED" ] && [ "$RUNNING" -gt 0 ]; then
            echo -e "${GREEN}✓ Deployment completed successfully${NC}"
            echo "  Running: $RUNNING, Desired: $DESIRED"
            break
        else
            echo -e "${YELLOW}  Deploying... (Running: $RUNNING, Desired: $DESIRED)${NC}"
            sleep 15
            elapsed=$((elapsed + 15))
        fi
    done
    
    if [ $elapsed -ge $timeout ]; then
        echo -e "${RED}✗ Deployment timed out after 10 minutes${NC}"
        echo "Check ECS console for details"
        exit 1
    fi
    echo ""
}

# Get application URL
get_application_url() {
    echo -e "${BLUE}Getting application URL...${NC}"
    
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --names "${PROJECT_NAME}-alb" \
        --region $REGION \
        --query 'LoadBalancers[0].DNSName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$ALB_DNS" != "NOT_FOUND" ]; then
        APP_URL="http://$ALB_DNS"
        echo "Application URL: $APP_URL"
        
        # Test health endpoint
        echo "Testing health endpoint..."
        sleep 5  # Give ALB a moment to update
        
        if curl -s "$APP_URL/actuator/health" > /dev/null; then
            echo -e "${GREEN}✓ Application is healthy${NC}"
        else
            echo -e "${YELLOW}⚠ Application may still be starting up${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Load balancer not found${NC}"
    fi
    echo ""
}

# Show deployment summary
show_summary() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}DEPLOYMENT SUMMARY${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    echo "🚀 Application: $PROJECT_NAME"
    echo "🌍 Region: $REGION"
    echo "📦 Environment: $ENVIRONMENT"
    echo "🐳 Image: $ECR_URL:latest"
    
    if [ "$APP_URL" != "" ]; then
        echo "🌐 URL: $APP_URL"
        echo ""
        echo "Health Check:"
        echo "  curl $APP_URL/actuator/health"
    fi
    
    echo ""
    echo "ECS Console:"
    echo "  https://console.aws.amazon.com/ecs/home?region=$REGION#/clusters/${PROJECT_NAME}-${ENVIRONMENT}/services"
    echo ""
}

# Main execution
main() {
    # Check prerequisites
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}AWS CLI is not installed${NC}"
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker is not installed${NC}"
        exit 1
    fi
    
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}AWS credentials not configured${NC}"
        exit 1
    fi
    
    # Execute deployment steps
    check_infrastructure
    get_ecr_url
    build_application
    build_and_push_image
    update_ecs_service
    wait_for_deployment
    get_application_url
    show_summary
    
    echo -e "${GREEN}🎉 Quick deployment completed successfully!${NC}"
}

# Run main function
main