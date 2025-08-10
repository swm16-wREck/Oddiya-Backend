#!/bin/bash

# Oddiya AWS Deployment Script
# This script deploys the Oddiya application to AWS using Terraform and Docker

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AWS_REGION=${AWS_REGION:-"ap-northeast-2"}
ENVIRONMENT=${ENVIRONMENT:-"dev"}
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPOSITORY="oddiya"

echo -e "${GREEN}🚀 Starting Oddiya AWS Deployment${NC}"
echo "Environment: $ENVIRONMENT"
echo "AWS Region: $AWS_REGION"
echo "AWS Account: $AWS_ACCOUNT_ID"

# Step 1: Build the application
echo -e "\n${YELLOW}📦 Building application...${NC}"
./gradlew clean bootJar

# Step 2: Create ECR repository if it doesn't exist
echo -e "\n${YELLOW}🐳 Setting up ECR repository...${NC}"
aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION 2>/dev/null || \
aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION

# Step 3: Build and push Docker image
echo -e "\n${YELLOW}🐳 Building Docker image...${NC}"
ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URI

# Build and tag image
docker build -t $ECR_REPOSITORY:latest .
docker tag $ECR_REPOSITORY:latest $ECR_URI:latest
docker tag $ECR_REPOSITORY:latest $ECR_URI:$ENVIRONMENT

# Push image
echo -e "\n${YELLOW}📤 Pushing image to ECR...${NC}"
docker push $ECR_URI:latest
docker push $ECR_URI:$ENVIRONMENT

# Step 4: Deploy infrastructure with Terraform
echo -e "\n${YELLOW}🏗️  Deploying infrastructure with Terraform...${NC}"
cd terraform/simple-deploy

# Initialize Terraform
terraform init

# Create terraform.tfvars if it doesn't exist
if [ ! -f terraform.tfvars ]; then
    echo -e "${YELLOW}Creating terraform.tfvars...${NC}"
    cat > terraform.tfvars <<EOF
environment = "$ENVIRONMENT"
aws_region = "$AWS_REGION"
enable_nat_gateway = false
container_image = "$ECR_URI:$ENVIRONMENT"
ecs_task_cpu = "256"
ecs_task_memory = "512"
ecs_desired_count = 1
db_name = "oddiya"
db_username = "oddiya_admin"
db_password = "$(openssl rand -base64 32)"
db_instance_class = "db.t3.micro"
backup_retention_period = 7
EOF
    echo -e "${RED}⚠️  Generated random database password. Please save it securely!${NC}"
fi

# Plan Terraform changes
echo -e "\n${YELLOW}📋 Planning infrastructure changes...${NC}"
terraform plan -out=tfplan

# Apply Terraform changes
read -p "Do you want to apply these changes? (yes/no): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    terraform apply tfplan
    
    # Get outputs
    ALB_DNS=$(terraform output -raw alb_dns_name)
    
    echo -e "\n${GREEN}✅ Deployment complete!${NC}"
    echo -e "Application URL: ${GREEN}http://$ALB_DNS${NC}"
    echo -e "\n${YELLOW}📝 Next steps:${NC}"
    echo "1. Wait 2-3 minutes for the application to start"
    echo "2. Check health: curl http://$ALB_DNS/api/v1/health"
    echo "3. Configure your domain to point to: $ALB_DNS"
    echo "4. Set up SSL certificate in AWS Certificate Manager"
else
    echo -e "${RED}❌ Deployment cancelled${NC}"
    exit 1
fi

cd ../..

echo -e "\n${GREEN}🎉 Oddiya deployment script completed!${NC}"