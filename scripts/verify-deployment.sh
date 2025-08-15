#!/bin/bash

# Verify Oddiya deployment
# This script checks all components of the deployment

set -e

echo "========================================"
echo "Oddiya Deployment Verification"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AWS_REGION="ap-northeast-2"
ECS_CLUSTER="oddiya-cluster"
ECS_SERVICE="oddiya-service"
DB_HOST="172.31.10.25"
ALB_DNS="oddiya-lb-1376928959.ap-northeast-2.elb.amazonaws.com"

echo ""
echo "1. Checking ECS Service Status..."
echo "--------------------------------"
SERVICE_STATUS=$(aws ecs describe-services \
    --cluster $ECS_CLUSTER \
    --services $ECS_SERVICE \
    --region $AWS_REGION \
    --query 'services[0].status' \
    --output text 2>/dev/null || echo "NOT_FOUND")

if [ "$SERVICE_STATUS" = "ACTIVE" ]; then
    echo -e "${GREEN}✓ ECS Service is ACTIVE${NC}"
    
    # Get running tasks count
    RUNNING_COUNT=$(aws ecs describe-services \
        --cluster $ECS_CLUSTER \
        --services $ECS_SERVICE \
        --region $AWS_REGION \
        --query 'services[0].runningCount' \
        --output text)
    
    DESIRED_COUNT=$(aws ecs describe-services \
        --cluster $ECS_CLUSTER \
        --services $ECS_SERVICE \
        --region $AWS_REGION \
        --query 'services[0].desiredCount' \
        --output text)
    
    echo "  Running tasks: $RUNNING_COUNT / $DESIRED_COUNT"
else
    echo -e "${RED}✗ ECS Service is not active: $SERVICE_STATUS${NC}"
fi

echo ""
echo "2. Checking Database Connectivity..."
echo "------------------------------------"
# Check if DB is reachable (using nc)
if nc -z -w5 $DB_HOST 5432 2>/dev/null; then
    echo -e "${GREEN}✓ PostgreSQL is reachable on $DB_HOST:5432${NC}"
else
    echo -e "${RED}✗ PostgreSQL is not reachable on $DB_HOST:5432${NC}"
fi

echo ""
echo "3. Checking Application Health..."
echo "---------------------------------"
# Check ALB health endpoint
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://$ALB_DNS/actuator/health || echo "000")

if [ "$HEALTH_STATUS" = "200" ]; then
    echo -e "${GREEN}✓ Application health check passed (HTTP 200)${NC}"
    
    # Get detailed health info
    HEALTH_JSON=$(curl -s http://$ALB_DNS/actuator/health | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    echo "  Health status: $HEALTH_JSON"
else
    echo -e "${RED}✗ Application health check failed (HTTP $HEALTH_STATUS)${NC}"
fi

echo ""
echo "4. Checking API Endpoints..."
echo "----------------------------"
# Test a few key endpoints
endpoints=(
    "/api/places"
    "/api/travel-plans"
    "/swagger-ui/index.html"
)

for endpoint in "${endpoints[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://$ALB_DNS$endpoint || echo "000")
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
        echo -e "${GREEN}✓ $endpoint is accessible (HTTP $STATUS)${NC}"
    else
        echo -e "${RED}✗ $endpoint is not accessible (HTTP $STATUS)${NC}"
    fi
done

echo ""
echo "5. Checking CloudWatch Logs..."
echo "------------------------------"
# Get recent logs
RECENT_LOGS=$(aws logs tail /ecs/oddiya \
    --region $AWS_REGION \
    --since 5m \
    --format short 2>/dev/null | head -5)

if [ -n "$RECENT_LOGS" ]; then
    echo -e "${GREEN}✓ Recent logs found in CloudWatch${NC}"
    echo "  Latest log entries:"
    echo "$RECENT_LOGS" | head -3 | sed 's/^/    /'
else
    echo -e "${YELLOW}⚠ No recent logs found (service might be starting)${NC}"
fi

echo ""
echo "6. Checking Docker Image..."
echo "---------------------------"
# Get latest image from ECR
LATEST_IMAGE=$(aws ecr describe-images \
    --repository-name oddiya \
    --region $AWS_REGION \
    --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
    --output text 2>/dev/null || echo "UNKNOWN")

if [ "$LATEST_IMAGE" != "UNKNOWN" ]; then
    echo -e "${GREEN}✓ Latest Docker image: $LATEST_IMAGE${NC}"
    
    # Get image push time
    PUSH_TIME=$(aws ecr describe-images \
        --repository-name oddiya \
        --region $AWS_REGION \
        --query 'sort_by(imageDetails,& imagePushedAt)[-1].imagePushedAt' \
        --output text 2>/dev/null || echo "UNKNOWN")
    
    echo "  Pushed at: $PUSH_TIME"
else
    echo -e "${RED}✗ Could not retrieve Docker image information${NC}"
fi

echo ""
echo "7. Checking GitHub Actions Status..."
echo "------------------------------------"
# Check latest workflow run
if command -v gh &> /dev/null; then
    LATEST_RUN=$(gh run list --repo swm16-wREck/Oddiya-Backend --workflow deploy.yml --limit 1 --json status,conclusion,name --jq '.[0]' 2>/dev/null)
    
    if [ -n "$LATEST_RUN" ]; then
        RUN_STATUS=$(echo "$LATEST_RUN" | jq -r '.status')
        RUN_CONCLUSION=$(echo "$LATEST_RUN" | jq -r '.conclusion')
        
        if [ "$RUN_STATUS" = "completed" ] && [ "$RUN_CONCLUSION" = "success" ]; then
            echo -e "${GREEN}✓ Latest GitHub Actions run succeeded${NC}"
        elif [ "$RUN_STATUS" = "in_progress" ]; then
            echo -e "${YELLOW}⚠ GitHub Actions run is in progress${NC}"
        else
            echo -e "${RED}✗ Latest GitHub Actions run failed or was cancelled${NC}"
        fi
    fi
else
    echo -e "${YELLOW}⚠ GitHub CLI not installed, skipping check${NC}"
fi

echo ""
echo "========================================"
echo "Deployment Verification Complete"
echo "========================================"

# Summary
echo ""
echo "Summary:"
if [ "$SERVICE_STATUS" = "ACTIVE" ] && [ "$HEALTH_STATUS" = "200" ]; then
    echo -e "${GREEN}✓ Deployment is successful and application is running!${NC}"
    echo ""
    echo "Access your application at:"
    echo "  - API: http://$ALB_DNS"
    echo "  - Swagger UI: http://$ALB_DNS/swagger-ui/index.html"
    echo "  - Health: http://$ALB_DNS/actuator/health"
    exit 0
else
    echo -e "${RED}✗ Deployment verification failed. Please check the logs for details.${NC}"
    echo ""
    echo "Troubleshooting commands:"
    echo "  - View ECS service: aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE"
    echo "  - View logs: aws logs tail /ecs/oddiya --follow"
    echo "  - Check tasks: aws ecs list-tasks --cluster $ECS_CLUSTER --service-name $ECS_SERVICE"
    exit 1
fi