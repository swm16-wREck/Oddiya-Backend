#!/bin/bash

set -e

# Configuration
AWS_REGION="ap-northeast-2"
ECR_REPOSITORY="oddiya"
ECS_CLUSTER="oddiya-dev-cluster"
ECS_SERVICE="oddiya-dev-service"
TASK_FAMILY="oddiya-dev"

echo "🚀 Starting manual ECS deployment..."
echo "================================="

# 1. Build and push Docker image
echo "📦 Building Docker image..."
docker build -t $ECR_REPOSITORY:latest .

# 2. Get ECR login token
echo "🔐 Logging into ECR..."
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

# 3. Tag and push image
ECR_URI="$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"
echo "🏷️  Tagging image as: $ECR_URI:latest"
docker tag $ECR_REPOSITORY:latest $ECR_URI:latest

echo "⬆️  Pushing image to ECR..."
docker push $ECR_URI:latest

# 4. Update the task definition with the new image
echo "📝 Updating task definition..."

# Get the current task definition
TASK_DEF=$(aws ecs describe-task-definition --task-definition $TASK_FAMILY --region $AWS_REGION --output json)

# Update the image in the task definition
NEW_TASK_DEF=$(echo $TASK_DEF | jq --arg IMAGE "$ECR_URI:latest" '.taskDefinition | .containerDefinitions[0].image = $IMAGE | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy)')

# Register the new task definition
REVISION=$(echo "$NEW_TASK_DEF" | aws ecs register-task-definition --cli-input-json file:///dev/stdin --region $AWS_REGION --query 'taskDefinition.revision' --output text)

echo "✅ New task definition revision: $REVISION"

# 5. Update ECS service
echo "🔄 Updating ECS service..."
aws ecs update-service \
    --cluster $ECS_CLUSTER \
    --service $ECS_SERVICE \
    --task-definition $TASK_FAMILY:$REVISION \
    --region $AWS_REGION \
    --output json | jq '.service | {serviceName, status, desiredCount, runningCount}'

echo ""
echo "✅ Deployment initiated!"
echo "================================="
echo ""
echo "Monitor deployment progress with:"
echo "  watch -n 5 'aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query \"services[0].deployments\" --output table'"
echo ""
echo "View service logs with:"
echo "  aws logs tail /ecs/oddiya-dev --follow --region $AWS_REGION"
echo ""
echo "Check service health:"
echo "  aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].events[:10]' --output table"