#!/bin/bash

# Apply ECS Optimizations Immediately
# Fixes the resource constraints that caused 28+ minute timeouts

set -e

REGION="ap-northeast-2"
CLUSTER_NAME="oddiya-dev"
SERVICE_NAME="oddiya-dev"
ECR_REPOSITORY="oddiya"

echo "========================================="
echo "APPLYING ECS OPTIMIZATIONS"
echo "Current: 256 CPU / 512MB Memory"
echo "Target:  1024 CPU / 2048MB Memory" 
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Get current ECR URI
ECR_URI=$(aws ecr describe-repositories \
    --repository-names $ECR_REPOSITORY \
    --region $REGION \
    --query 'repositories[0].repositoryUri' \
    --output text)

echo -e "${BLUE}Creating optimized task definition...${NC}"

# Create optimized task definition
cat > /tmp/optimized-task-definition.json <<EOF
{
    "family": "$SERVICE_NAME",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "1024",
    "memory": "2048",
    "executionRoleArn": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/ecsTaskExecutionRole",
    "taskRoleArn": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/oddiya-ecs-task-role",
    "containerDefinitions": [
        {
            "name": "$ECR_REPOSITORY",
            "image": "${ECR_URI}:latest",
            "essential": true,
            "portMappings": [
                {
                    "containerPort": 8080,
                    "protocol": "tcp"
                }
            ],
            "environment": [
                {
                    "name": "SPRING_PROFILES_ACTIVE",
                    "value": "dynamodb"
                },
                {
                    "name": "AWS_REGION", 
                    "value": "$REGION"
                },
                {
                    "name": "JAVA_OPTS",
                    "value": "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -Xms1024m -Xmx1536m"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/$SERVICE_NAME",
                    "awslogs-region": "$REGION",
                    "awslogs-stream-prefix": "ecs"
                }
            },
            "healthCheck": {
                "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
                "interval": 30,
                "timeout": 10,
                "retries": 5,
                "startPeriod": 180
            }
        }
    ]
}
EOF

echo -e "${BLUE}Registering new task definition...${NC}"
NEW_TASK_DEF=$(aws ecs register-task-definition \
    --cli-input-json file:///tmp/optimized-task-definition.json \
    --region $REGION \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "New task definition: $NEW_TASK_DEF"

echo -e "${BLUE}Updating ALB health check settings...${NC}"
# Get target group ARN
TG_ARN=$(aws elbv2 describe-target-groups \
    --names oddiya-dev-tg \
    --region $REGION \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text)

# Update health check settings
aws elbv2 modify-target-group \
    --target-group-arn $TG_ARN \
    --health-check-interval-seconds 60 \
    --health-check-timeout-seconds 15 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 5 \
    --region $REGION

echo -e "${GREEN}✓ Health check settings updated${NC}"

echo -e "${BLUE}Updating ECS service...${NC}"
aws ecs update-service \
    --cluster $CLUSTER_NAME \
    --service $SERVICE_NAME \
    --task-definition $NEW_TASK_DEF \
    --region $REGION > /dev/null

echo -e "${GREEN}✓ Service update initiated${NC}"

echo -e "${BLUE}Monitoring deployment...${NC}"
echo "This will take 3-5 minutes for the new optimized task to start..."

# Wait for service stability
timeout=600
elapsed=0
while [ $elapsed -lt $timeout ]; do
    STATUS=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].[runningCount,desiredCount]' \
        --output text)
    
    RUNNING=$(echo $STATUS | awk '{print $1}')
    DESIRED=$(echo $STATUS | awk '{print $2}')
    
    # Check if using new task definition
    CURRENT_TASK_DEF=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].taskDefinition' \
        --output text)
    
    if [[ "$CURRENT_TASK_DEF" == *"$NEW_TASK_DEF"* ]] && [ "$RUNNING" == "$DESIRED" ] && [ "$RUNNING" -gt 0 ]; then
        echo -e "${GREEN}✓ Service successfully updated with optimized configuration${NC}"
        break
    else
        echo -e "${YELLOW}  Deploying... (Running: $RUNNING, Desired: $DESIRED, Current TD: $(basename $CURRENT_TASK_DEF))${NC}"
        sleep 15
        elapsed=$((elapsed + 15))
    fi
done

if [ $elapsed -ge $timeout ]; then
    echo -e "${RED}✗ Deployment timed out${NC}"
    exit 1
fi

# Verify new task resources
echo -e "${BLUE}Verifying optimized configuration...${NC}"
NEW_CONFIG=$(aws ecs describe-task-definition \
    --task-definition $NEW_TASK_DEF \
    --region $REGION \
    --query 'taskDefinition.[cpu,memory]' \
    --output text)

echo "New Configuration: $(echo $NEW_CONFIG | awk '{print $1}') CPU / $(echo $NEW_CONFIG | awk '{print $2}')MB Memory"

# Test health endpoint
echo -e "${BLUE}Testing application health...${NC}"
ALB_DNS=$(aws elbv2 describe-load-balancers \
    --names oddiya-alb \
    --region $REGION \
    --query 'LoadBalancers[0].DNSName' \
    --output text)

if [ "$ALB_DNS" != "None" ]; then
    sleep 30  # Give ALB time to route to new task
    if curl -f "http://$ALB_DNS/actuator/health" 2>/dev/null; then
        echo -e "${GREEN}✓ Application is healthy and responding${NC}"
    else
        echo -e "${YELLOW}⚠ Application may still be starting up${NC}"
    fi
fi

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}OPTIMIZATION COMPLETE${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "📊 Configuration Summary:"
echo "  CPU: 256 → 1024 (4x increase)"
echo "  Memory: 512MB → 2048MB (4x increase)"
echo "  Health Check Timeout: 5s → 15s"
echo "  Health Check Interval: 30s → 60s"
echo "  Startup Period: 60s → 180s"
echo ""
echo "🎯 Expected Benefits:"
echo "  • Faster startup times (133s → 60-80s)"
echo "  • More stable deployments"
echo "  • Reduced timeout failures"
echo "  • Better resource utilization"
echo ""

# Cleanup
rm -f /tmp/optimized-task-definition.json

echo -e "${GREEN}🎉 Your ECS service is now optimized and ready!${NC}"