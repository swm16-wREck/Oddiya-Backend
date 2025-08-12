#!/bin/bash

set -e

# Configuration
AWS_REGION="ap-northeast-2"
ECS_CLUSTER="oddiya-dev-cluster"
ECS_SERVICE="oddiya-dev-service"
VPC_ID="vpc-08223ce6e376b9d4a"

echo "🔧 Fixing ECS Network Configuration..."
echo "================================="

# Get public subnet IDs
PUBLIC_SUBNET_1=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=public" --region $AWS_REGION --query 'Subnets[0].SubnetId' --output text)
PUBLIC_SUBNET_2=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=public" --region $AWS_REGION --query 'Subnets[1].SubnetId' --output text)

# Get security group
SECURITY_GROUP=$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=*ecs*" --region $AWS_REGION --query 'SecurityGroups[0].GroupId' --output text)

echo "📍 Network Configuration:"
echo "  VPC: $VPC_ID"
echo "  Public Subnet 1: $PUBLIC_SUBNET_1"
echo "  Public Subnet 2: $PUBLIC_SUBNET_2"
echo "  Security Group: $SECURITY_GROUP"
echo ""

# Update the service with public subnets
echo "🔄 Updating ECS service network configuration..."
aws ecs update-service \
  --cluster $ECS_CLUSTER \
  --service $ECS_SERVICE \
  --network-configuration "awsvpcConfiguration={subnets=[$PUBLIC_SUBNET_1,$PUBLIC_SUBNET_2],securityGroups=[$SECURITY_GROUP],assignPublicIp=ENABLED}" \
  --force-new-deployment \
  --region $AWS_REGION \
  --output json | jq '.service | {serviceName, status, desiredCount, runningCount}'

echo ""
echo "✅ Network configuration updated!"
echo "================================="
echo ""
echo "The service will now:"
echo "1. Use public subnets with internet access"
echo "2. Assign public IPs to tasks"
echo "3. Be able to pull images from ECR"
echo ""
echo "Monitor the deployment with:"
echo "  aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].events[:5]' --output table"
echo ""
echo "Check task status with:"
echo "  aws ecs list-tasks --cluster $ECS_CLUSTER --service-name $ECS_SERVICE --region $AWS_REGION --query 'taskArns[0]' --output text | xargs -I {} aws ecs describe-tasks --cluster $ECS_CLUSTER --tasks {} --region $AWS_REGION --query 'tasks[0].{Status:lastStatus,Health:healthStatus}' --output json"