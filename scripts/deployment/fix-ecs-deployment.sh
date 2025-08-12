#!/bin/bash

# ECS Deployment Fix Script
# Resolves common ECS deployment issues

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER_NAME="${ECS_CLUSTER:-oddiya-dev}"
SERVICE_NAME="${ECS_SERVICE:-oddiya-dev}"
ECR_REPOSITORY="${ECR_REPOSITORY:-oddiya}"

echo "========================================="
echo "ECS DEPLOYMENT FIX"
echo "Region: $REGION"
echo "Cluster: $CLUSTER_NAME"
echo "Service: $SERVICE_NAME"
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Function to create ECS cluster if it doesn't exist
create_cluster() {
    echo -e "${BLUE}Checking ECS Cluster...${NC}"
    
    CLUSTER_EXISTS=$(aws ecs describe-clusters \
        --clusters $CLUSTER_NAME \
        --region $REGION \
        --query 'clusters[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$CLUSTER_EXISTS" == "NOT_FOUND" ] || [ "$CLUSTER_EXISTS" == "INACTIVE" ]; then
        echo -e "${YELLOW}Creating ECS Cluster '$CLUSTER_NAME'...${NC}"
        aws ecs create-cluster --cluster-name $CLUSTER_NAME --region $REGION
        echo -e "${GREEN}✓ Cluster created${NC}"
    else
        echo -e "${GREEN}✓ Cluster already exists${NC}"
    fi
    echo ""
}

# Function to create ECR repository if it doesn't exist
create_ecr_repository() {
    echo -e "${BLUE}Checking ECR Repository...${NC}"
    
    REPO_EXISTS=$(aws ecr describe-repositories \
        --repository-names $ECR_REPOSITORY \
        --region $REGION \
        --query 'repositories[0].repositoryName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$REPO_EXISTS" == "NOT_FOUND" ]; then
        echo -e "${YELLOW}Creating ECR Repository '$ECR_REPOSITORY'...${NC}"
        aws ecr create-repository \
            --repository-name $ECR_REPOSITORY \
            --region $REGION \
            --image-scanning-configuration scanOnPush=true \
            --encryption-configuration encryptionType=AES256
        echo -e "${GREEN}✓ Repository created${NC}"
        
        # Set lifecycle policy to keep only last 10 images
        aws ecr put-lifecycle-policy \
            --repository-name $ECR_REPOSITORY \
            --region $REGION \
            --lifecycle-policy-text '{
                "rules": [
                    {
                        "rulePriority": 1,
                        "description": "Keep only 10 images",
                        "selection": {
                            "tagStatus": "any",
                            "countType": "imageCountMoreThan",
                            "countNumber": 10
                        },
                        "action": {
                            "type": "expire"
                        }
                    }
                ]
            }' 2>/dev/null || true
    else
        echo -e "${GREEN}✓ Repository already exists${NC}"
    fi
    
    # Get repository URI
    ECR_URI=$(aws ecr describe-repositories \
        --repository-names $ECR_REPOSITORY \
        --region $REGION \
        --query 'repositories[0].repositoryUri' \
        --output text)
    echo "  Repository URI: $ECR_URI"
    echo ""
}

# Function to create task definition
create_task_definition() {
    echo -e "${BLUE}Creating/Updating Task Definition...${NC}"
    
    # Get ECR URI
    ECR_URI=$(aws ecr describe-repositories \
        --repository-names $ECR_REPOSITORY \
        --region $REGION \
        --query 'repositories[0].repositoryUri' \
        --output text)
    
    # Create task definition JSON
    cat > /tmp/task-definition.json <<EOF
{
    "family": "$SERVICE_NAME",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "256",
    "memory": "512",
    "executionRoleArn": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/ecsTaskExecutionRole",
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
                "timeout": 5,
                "retries": 3,
                "startPeriod": 60
            }
        }
    ]
}
EOF
    
    # Create CloudWatch log group
    aws logs create-log-group --log-group-name "/ecs/$SERVICE_NAME" --region $REGION 2>/dev/null || true
    
    # Register task definition
    aws ecs register-task-definition \
        --cli-input-json file:///tmp/task-definition.json \
        --region $REGION > /dev/null
    
    echo -e "${GREEN}✓ Task definition registered${NC}"
    echo ""
}

# Function to create or update service
create_or_update_service() {
    echo -e "${BLUE}Checking ECS Service...${NC}"
    
    # Check if service exists
    SERVICE_EXISTS=$(aws ecs describe-services \
        --cluster $CLUSTER_NAME \
        --services $SERVICE_NAME \
        --region $REGION \
        --query 'services[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$SERVICE_EXISTS" == "NOT_FOUND" ] || [ "$SERVICE_EXISTS" == "INACTIVE" ]; then
        echo -e "${YELLOW}Creating ECS Service '$SERVICE_NAME'...${NC}"
        
        # Get default VPC and subnets
        VPC_ID=$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --region $REGION --query 'Vpcs[0].VpcId' --output text)
        SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" --region $REGION --query 'Subnets[*].SubnetId' --output text | tr '\t' ',')
        
        # Create security group for ECS service
        SG_ID=$(aws ec2 create-security-group \
            --group-name "$SERVICE_NAME-sg" \
            --description "Security group for $SERVICE_NAME ECS service" \
            --vpc-id $VPC_ID \
            --region $REGION \
            --query 'GroupId' \
            --output text 2>/dev/null || \
            aws ec2 describe-security-groups \
                --filters "Name=group-name,Values=$SERVICE_NAME-sg" \
                --region $REGION \
                --query 'SecurityGroups[0].GroupId' \
                --output text)
        
        # Add ingress rule for port 8080
        aws ec2 authorize-security-group-ingress \
            --group-id $SG_ID \
            --protocol tcp \
            --port 8080 \
            --cidr 0.0.0.0/0 \
            --region $REGION 2>/dev/null || true
        
        # Create service
        aws ecs create-service \
            --cluster $CLUSTER_NAME \
            --service-name $SERVICE_NAME \
            --task-definition $SERVICE_NAME \
            --desired-count 1 \
            --launch-type FARGATE \
            --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_ID],assignPublicIp=ENABLED}" \
            --region $REGION > /dev/null
        
        echo -e "${GREEN}✓ Service created${NC}"
    else
        echo -e "${GREEN}✓ Service already exists${NC}"
        
        # Force new deployment
        echo -e "${YELLOW}Forcing new deployment...${NC}"
        aws ecs update-service \
            --cluster $CLUSTER_NAME \
            --service $SERVICE_NAME \
            --force-new-deployment \
            --region $REGION > /dev/null
        
        echo -e "${GREEN}✓ Deployment triggered${NC}"
    fi
    echo ""
}

# Function to create ECS task execution role
create_execution_role() {
    echo -e "${BLUE}Checking ECS Task Execution Role...${NC}"
    
    ROLE_EXISTS=$(aws iam get-role --role-name ecsTaskExecutionRole --query 'Role.RoleName' --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$ROLE_EXISTS" == "NOT_FOUND" ]; then
        echo -e "${YELLOW}Creating ECS Task Execution Role...${NC}"
        
        # Create trust policy
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
        
        # Create role
        aws iam create-role \
            --role-name ecsTaskExecutionRole \
            --assume-role-policy-document file:///tmp/trust-policy.json
        
        # Attach policy
        aws iam attach-role-policy \
            --role-name ecsTaskExecutionRole \
            --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
        
        echo -e "${GREEN}✓ Role created${NC}"
    else
        echo -e "${GREEN}✓ Role already exists${NC}"
    fi
    echo ""
}

# Function to wait for service stability
wait_for_service() {
    echo -e "${BLUE}Waiting for service to stabilize...${NC}"
    
    # Wait up to 10 minutes
    timeout=600
    elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        STATUS=$(aws ecs describe-services \
            --cluster $CLUSTER_NAME \
            --services $SERVICE_NAME \
            --region $REGION \
            --query 'services[0].[runningCount,desiredCount]' \
            --output text 2>/dev/null || echo "0 0")
        
        RUNNING=$(echo $STATUS | awk '{print $1}')
        DESIRED=$(echo $STATUS | awk '{print $2}')
        
        if [ "$RUNNING" == "$DESIRED" ] && [ "$RUNNING" -gt 0 ]; then
            echo -e "${GREEN}✓ Service is stable (Running: $RUNNING, Desired: $DESIRED)${NC}"
            break
        else
            echo -e "${YELLOW}Waiting... (Running: $RUNNING, Desired: $DESIRED)${NC}"
            sleep 10
            elapsed=$((elapsed + 10))
        fi
    done
    
    if [ $elapsed -ge $timeout ]; then
        echo -e "${RED}✗ Service failed to stabilize within 10 minutes${NC}"
        
        # Show recent events
        echo -e "${YELLOW}Recent events:${NC}"
        aws ecs describe-services \
            --cluster $CLUSTER_NAME \
            --services $SERVICE_NAME \
            --region $REGION \
            --query 'services[0].events[:5].[createdAt,message]' \
            --output table
        
        exit 1
    fi
    echo ""
}

# Main execution
main() {
    echo "Starting ECS deployment fix..."
    echo ""
    
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
    
    # Execute fixes
    create_execution_role
    create_cluster
    create_ecr_repository
    create_task_definition
    create_or_update_service
    wait_for_service
    
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}DEPLOYMENT FIX COMPLETE${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    echo "Service URL: https://console.aws.amazon.com/ecs/home?region=$REGION#/clusters/$CLUSTER_NAME/services/$SERVICE_NAME/tasks"
    echo ""
    echo "To push a Docker image:"
    echo "  aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_URI"
    echo "  docker build -t $ECR_REPOSITORY ."
    echo "  docker tag $ECR_REPOSITORY:latest $ECR_URI:latest"
    echo "  docker push $ECR_URI:latest"
}

# Run main function
main