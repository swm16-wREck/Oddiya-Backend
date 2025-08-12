#!/bin/bash

# AWS ECS Deployment Resource Verification Script
# This script checks if all required AWS resources exist for ECS deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
ECR_REPOSITORY="oddiya"
ECS_CLUSTER="oddiya-${ENVIRONMENT}-cluster"
ECS_SERVICE="oddiya-${ENVIRONMENT}-service"
LOG_GROUP="/ecs/oddiya-${ENVIRONMENT}"

echo "========================================="
echo "AWS ECS Deployment Resource Verification"
echo "========================================="
echo "Region: $AWS_REGION"
echo "Environment: $ENVIRONMENT"
echo "========================================="
echo ""

# Function to check if AWS CLI is configured
check_aws_cli() {
    echo "Checking AWS CLI configuration..."
    if ! aws sts get-caller-identity &>/dev/null; then
        echo -e "${RED}✗ AWS CLI is not configured or credentials are invalid${NC}"
        echo "Please run: aws configure"
        exit 1
    else
        ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
        echo -e "${GREEN}✓ AWS CLI configured for account: $ACCOUNT_ID${NC}"
    fi
}

# Function to check ECR repository
check_ecr() {
    echo ""
    echo "Checking ECR repository..."
    if aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION &>/dev/null; then
        echo -e "${GREEN}✓ ECR repository '$ECR_REPOSITORY' exists${NC}"
        
        # Check if there are any images
        IMAGE_COUNT=$(aws ecr list-images --repository-name $ECR_REPOSITORY --region $AWS_REGION --query 'length(imageIds)' --output text)
        if [ "$IMAGE_COUNT" -gt 0 ]; then
            echo -e "${GREEN}  └─ Found $IMAGE_COUNT image(s) in repository${NC}"
        else
            echo -e "${YELLOW}  └─ Warning: No images found in repository${NC}"
        fi
    else
        echo -e "${RED}✗ ECR repository '$ECR_REPOSITORY' does not exist${NC}"
        echo "  Creating ECR repository..."
        aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION
        echo -e "${GREEN}  └─ ECR repository created${NC}"
    fi
}

# Function to check ECS cluster
check_ecs_cluster() {
    echo ""
    echo "Checking ECS cluster..."
    if aws ecs describe-clusters --clusters $ECS_CLUSTER --region $AWS_REGION --query 'clusters[0].clusterName' --output text 2>/dev/null | grep -q $ECS_CLUSTER; then
        echo -e "${GREEN}✓ ECS cluster '$ECS_CLUSTER' exists${NC}"
        
        # Get cluster status
        STATUS=$(aws ecs describe-clusters --clusters $ECS_CLUSTER --region $AWS_REGION --query 'clusters[0].status' --output text)
        SERVICES_COUNT=$(aws ecs describe-clusters --clusters $ECS_CLUSTER --region $AWS_REGION --query 'clusters[0].activeServicesCount' --output text)
        TASKS_COUNT=$(aws ecs describe-clusters --clusters $ECS_CLUSTER --region $AWS_REGION --query 'clusters[0].runningTasksCount' --output text)
        
        echo -e "${GREEN}  ├─ Status: $STATUS${NC}"
        echo -e "${GREEN}  ├─ Active services: $SERVICES_COUNT${NC}"
        echo -e "${GREEN}  └─ Running tasks: $TASKS_COUNT${NC}"
    else
        echo -e "${RED}✗ ECS cluster '$ECS_CLUSTER' does not exist${NC}"
        echo "  Creating ECS cluster..."
        aws ecs create-cluster --cluster-name $ECS_CLUSTER --region $AWS_REGION --capacity-providers FARGATE FARGATE_SPOT
        echo -e "${GREEN}  └─ ECS cluster created${NC}"
    fi
}

# Function to check ECS service
check_ecs_service() {
    echo ""
    echo "Checking ECS service..."
    if aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].serviceName' --output text 2>/dev/null | grep -q $ECS_SERVICE; then
        echo -e "${GREEN}✓ ECS service '$ECS_SERVICE' exists${NC}"
        
        # Get service details
        STATUS=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].status' --output text)
        DESIRED=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].desiredCount' --output text)
        RUNNING=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].runningCount' --output text)
        PENDING=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].pendingCount' --output text)
        
        echo -e "${GREEN}  ├─ Status: $STATUS${NC}"
        echo -e "${GREEN}  ├─ Desired count: $DESIRED${NC}"
        echo -e "${GREEN}  ├─ Running count: $RUNNING${NC}"
        echo -e "${GREEN}  └─ Pending count: $PENDING${NC}"
        
        if [ "$STATUS" != "ACTIVE" ]; then
            echo -e "${YELLOW}  Warning: Service is not ACTIVE${NC}"
        fi
    else
        echo -e "${YELLOW}✗ ECS service '$ECS_SERVICE' does not exist${NC}"
        echo -e "${YELLOW}  Note: Service will be created during first deployment${NC}"
    fi
}

# Function to check task definition
check_task_definition() {
    echo ""
    echo "Checking task definition..."
    TASK_FAMILY="oddiya-${ENVIRONMENT}"
    
    if aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION &>/dev/null; then
        LATEST_REVISION=$(aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION --query 'taskDefinition.revision' --output text)
        echo -e "${GREEN}✓ Task definition '$TASK_FAMILY' exists (revision: $LATEST_REVISION)${NC}"
        
        # Get task definition details
        CPU=$(aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION --query 'taskDefinition.cpu' --output text)
        MEMORY=$(aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION --query 'taskDefinition.memory' --output text)
        NETWORK_MODE=$(aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION --query 'taskDefinition.networkMode' --output text)
        
        echo -e "${GREEN}  ├─ CPU: $CPU${NC}"
        echo -e "${GREEN}  ├─ Memory: $MEMORY${NC}"
        echo -e "${GREEN}  └─ Network mode: $NETWORK_MODE${NC}"
    else
        echo -e "${YELLOW}✗ Task definition '$TASK_FAMILY' does not exist${NC}"
        echo -e "${YELLOW}  Note: Task definition will be created during deployment${NC}"
    fi
}

# Function to check CloudWatch log group
check_cloudwatch_logs() {
    echo ""
    echo "Checking CloudWatch log group..."
    if aws logs describe-log-groups --log-group-name-prefix $LOG_GROUP --region $AWS_REGION --query 'logGroups[0].logGroupName' --output text 2>/dev/null | grep -q $LOG_GROUP; then
        echo -e "${GREEN}✓ CloudWatch log group '$LOG_GROUP' exists${NC}"
    else
        echo -e "${RED}✗ CloudWatch log group '$LOG_GROUP' does not exist${NC}"
        echo "  Creating log group..."
        aws logs create-log-group --log-group-name $LOG_GROUP --region $AWS_REGION
        aws logs put-retention-policy --log-group-name $LOG_GROUP --retention-in-days 7 --region $AWS_REGION
        echo -e "${GREEN}  └─ Log group created with 7-day retention${NC}"
    fi
}

# Function to check VPC and networking
check_networking() {
    echo ""
    echo "Checking VPC and networking..."
    
    # Check for VPC with oddiya tag
    VPC_ID=$(aws ec2 describe-vpcs --filters "Name=tag:Name,Values=oddiya-${ENVIRONMENT}-vpc" --region $AWS_REGION --query 'Vpcs[0].VpcId' --output text 2>/dev/null)
    
    if [ "$VPC_ID" != "None" ] && [ -n "$VPC_ID" ]; then
        echo -e "${GREEN}✓ VPC found: $VPC_ID${NC}"
        
        # Check subnets
        PRIVATE_SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=private" --region $AWS_REGION --query 'length(Subnets)' --output text)
        PUBLIC_SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=public" --region $AWS_REGION --query 'length(Subnets)' --output text)
        
        echo -e "${GREEN}  ├─ Private subnets: ${PRIVATE_SUBNETS:-0}${NC}"
        echo -e "${GREEN}  └─ Public subnets: ${PUBLIC_SUBNETS:-0}${NC}"
        
        # Check security groups
        ECS_SG=$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=*ecs*" --region $AWS_REGION --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null)
        if [ "$ECS_SG" != "None" ] && [ -n "$ECS_SG" ]; then
            echo -e "${GREEN}✓ ECS Security Group found: $ECS_SG${NC}"
        else
            echo -e "${YELLOW}⚠ No ECS security group found${NC}"
        fi
    else
        echo -e "${RED}✗ VPC not found for environment: $ENVIRONMENT${NC}"
        echo -e "${YELLOW}  Run Terraform to create VPC and networking resources${NC}"
    fi
}

# Function to check IAM roles
check_iam_roles() {
    echo ""
    echo "Checking IAM roles..."
    
    # Check task execution role
    EXEC_ROLE="oddiya-${ENVIRONMENT}-ecs-task-execution-role"
    if aws iam get-role --role-name $EXEC_ROLE &>/dev/null; then
        echo -e "${GREEN}✓ Task execution role exists: $EXEC_ROLE${NC}"
    else
        echo -e "${YELLOW}⚠ Task execution role not found: $EXEC_ROLE${NC}"
    fi
    
    # Check task role
    TASK_ROLE="oddiya-${ENVIRONMENT}-ecs-task-role"
    if aws iam get-role --role-name $TASK_ROLE &>/dev/null; then
        echo -e "${GREEN}✓ Task role exists: $TASK_ROLE${NC}"
    else
        echo -e "${YELLOW}⚠ Task role not found: $TASK_ROLE${NC}"
    fi
}

# Function to check application load balancer
check_alb() {
    echo ""
    echo "Checking Application Load Balancer..."
    
    ALB_NAME="oddiya-${ENVIRONMENT}-alb"
    ALB_ARN=$(aws elbv2 describe-load-balancers --names $ALB_NAME --region $AWS_REGION --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null)
    
    if [ "$ALB_ARN" != "None" ] && [ -n "$ALB_ARN" ]; then
        echo -e "${GREEN}✓ ALB found: $ALB_NAME${NC}"
        
        # Check target groups
        TG_COUNT=$(aws elbv2 describe-target-groups --load-balancer-arn $ALB_ARN --region $AWS_REGION --query 'length(TargetGroups)' --output text)
        echo -e "${GREEN}  └─ Target groups: $TG_COUNT${NC}"
    else
        echo -e "${YELLOW}⚠ ALB not found: $ALB_NAME${NC}"
        echo -e "${YELLOW}  Note: ALB may be created by Terraform${NC}"
    fi
}

# Function to check DynamoDB tables
check_dynamodb() {
    echo ""
    echo "Checking DynamoDB tables..."
    
    TABLES=("Users" "TravelPlans" "Places")
    for TABLE in "${TABLES[@]}"; do
        if aws dynamodb describe-table --table-name $TABLE --region $AWS_REGION &>/dev/null; then
            STATUS=$(aws dynamodb describe-table --table-name $TABLE --region $AWS_REGION --query 'Table.TableStatus' --output text)
            ITEM_COUNT=$(aws dynamodb describe-table --table-name $TABLE --region $AWS_REGION --query 'Table.ItemCount' --output text)
            echo -e "${GREEN}✓ Table '$TABLE' exists (Status: $STATUS, Items: $ITEM_COUNT)${NC}"
        else
            echo -e "${YELLOW}⚠ Table '$TABLE' does not exist${NC}"
        fi
    done
}

# Function to check S3 buckets
check_s3() {
    echo ""
    echo "Checking S3 buckets..."
    
    BUCKET_PREFIX="oddiya-${ENVIRONMENT}"
    BUCKETS=$(aws s3api list-buckets --query "Buckets[?contains(Name, '$BUCKET_PREFIX')].Name" --output text)
    
    if [ -n "$BUCKETS" ]; then
        for BUCKET in $BUCKETS; do
            echo -e "${GREEN}✓ S3 bucket found: $BUCKET${NC}"
        done
    else
        echo -e "${YELLOW}⚠ No S3 buckets found with prefix: $BUCKET_PREFIX${NC}"
    fi
}

# Function to generate summary
generate_summary() {
    echo ""
    echo "========================================="
    echo "Summary and Recommendations"
    echo "========================================="
    echo ""
    echo "To deploy successfully, ensure:"
    echo "1. All resources marked with ✓ are properly configured"
    echo "2. Resources marked with ✗ are created (run this script with --fix)"
    echo "3. Resources marked with ⚠ may need attention"
    echo ""
    echo "GitHub Actions Configuration Update:"
    echo "Update .github/workflows/deploy-ecs.yml with:"
    echo "  ECS_CLUSTER: $ECS_CLUSTER"
    echo "  ECS_SERVICE: $ECS_SERVICE"
    echo "  ECR_REPOSITORY: $ECR_REPOSITORY"
    echo ""
    echo "To fix missing resources, run:"
    echo "  $0 --fix"
    echo ""
    echo "To deploy manually, run:"
    echo "  ./scripts/manual-ecs-deploy.sh"
}

# Main execution
main() {
    check_aws_cli
    check_ecr
    check_ecs_cluster
    check_ecs_service
    check_task_definition
    check_cloudwatch_logs
    check_networking
    check_iam_roles
    check_alb
    check_dynamodb
    check_s3
    generate_summary
}

# Run main function
main

echo ""
echo "========================================="
echo "Verification complete!"
echo "========================================="