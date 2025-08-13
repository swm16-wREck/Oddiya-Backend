#!/bin/bash

set -e

# Configuration
AWS_REGION="ap-northeast-2"
ECS_CLUSTER="oddiya-dev-cluster"
ECS_SERVICE="oddiya-dev-service"
TASK_FAMILY="oddiya-dev"
VPC_ID="vpc-08223ce6e376b9d4a"
TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups --names oddiya-dev-tg --region $AWS_REGION --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo "")

echo "🚀 Creating ECS Service..."

# Get subnet IDs
SUBNET_1=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=private" --region $AWS_REGION --query 'Subnets[0].SubnetId' --output text)
SUBNET_2=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Type,Values=private" --region $AWS_REGION --query 'Subnets[1].SubnetId' --output text)

# Get security group
SECURITY_GROUP=$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=*ecs*" --region $AWS_REGION --query 'SecurityGroups[0].GroupId' --output text)

echo "Using VPC: $VPC_ID"
echo "Using Subnets: $SUBNET_1, $SUBNET_2"
echo "Using Security Group: $SECURITY_GROUP"

# Create the service
if [ -n "$TARGET_GROUP_ARN" ] && [ "$TARGET_GROUP_ARN" != "None" ]; then
    echo "Creating service with load balancer..."
    aws ecs create-service \
        --cluster $ECS_CLUSTER \
        --service-name $ECS_SERVICE \
        --task-definition $TASK_FAMILY \
        --desired-count 1 \
        --launch-type FARGATE \
        --platform-version LATEST \
        --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_1,$SUBNET_2],securityGroups=[$SECURITY_GROUP],assignPublicIp=ENABLED}" \
        --load-balancers "targetGroupArn=$TARGET_GROUP_ARN,containerName=oddiya,containerPort=8080" \
        --region $AWS_REGION
else
    echo "Creating service without load balancer..."
    aws ecs create-service \
        --cluster $ECS_CLUSTER \
        --service-name $ECS_SERVICE \
        --task-definition $TASK_FAMILY \
        --desired-count 1 \
        --launch-type FARGATE \
        --platform-version LATEST \
        --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_1,$SUBNET_2],securityGroups=[$SECURITY_GROUP],assignPublicIp=ENABLED}" \
        --region $AWS_REGION
fi

echo "✅ ECS Service created successfully!"
echo ""
echo "Monitor the service with:"
echo "aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION"