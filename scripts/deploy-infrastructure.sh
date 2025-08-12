#!/bin/bash

# Deploy ECS Infrastructure
# Deploys complete ECS infrastructure using Terraform

set -e

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
TERRAFORM_DIR="terraform/ecs-infrastructure"

echo "========================================="
echo "ECS INFRASTRUCTURE DEPLOYMENT"
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

# Check prerequisites
check_prerequisites() {
    echo -e "${BLUE}Checking prerequisites...${NC}"
    
    # Check Terraform
    if ! command -v terraform &> /dev/null; then
        echo -e "${RED}✗ Terraform is not installed${NC}"
        echo "Please install Terraform: https://www.terraform.io/downloads.html"
        exit 1
    fi
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}✗ AWS CLI is not installed${NC}"
        echo "Please install AWS CLI: https://aws.amazon.com/cli/"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}✗ AWS credentials not configured${NC}"
        echo "Please configure AWS CLI: aws configure"
        exit 1
    fi
    
    # Check if terraform directory exists
    if [ ! -d "$TERRAFORM_DIR" ]; then
        echo -e "${RED}✗ Terraform directory not found: $TERRAFORM_DIR${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ All prerequisites met${NC}"
    echo ""
}

# Initialize Terraform
init_terraform() {
    echo -e "${BLUE}Initializing Terraform...${NC}"
    
    cd "$TERRAFORM_DIR"
    
    terraform init -upgrade
    
    echo -e "${GREEN}✓ Terraform initialized${NC}"
    echo ""
}

# Plan Terraform changes
plan_terraform() {
    echo -e "${BLUE}Planning Terraform changes...${NC}"
    
    terraform plan \
        -var="region=$REGION" \
        -var="project_name=$PROJECT_NAME" \
        -var="environment=$ENVIRONMENT" \
        -out=tfplan
    
    echo ""
    echo -e "${YELLOW}Review the plan above. Continue with deployment? (y/N)${NC}"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "Deployment cancelled."
        exit 0
    fi
    echo ""
}

# Apply Terraform changes
apply_terraform() {
    echo -e "${BLUE}Applying Terraform changes...${NC}"
    
    terraform apply tfplan
    
    echo -e "${GREEN}✓ Infrastructure deployed successfully${NC}"
    echo ""
}

# Get outputs
get_outputs() {
    echo -e "${BLUE}Getting deployment outputs...${NC}"
    echo ""
    
    # Get key outputs
    ALB_DNS=$(terraform output -raw alb_dns_name)
    ECR_URL=$(terraform output -raw ecr_repository_url)
    CLUSTER_NAME=$(terraform output -raw ecs_cluster_name)
    SERVICE_NAME=$(terraform output -raw ecs_service_name)
    APP_URL=$(terraform output -raw application_url)
    
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}DEPLOYMENT COMPLETE${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    echo "🌐 Application URL: $APP_URL"
    echo "🐳 ECR Repository: $ECR_URL"
    echo "📦 ECS Cluster: $CLUSTER_NAME"
    echo "🚀 ECS Service: $SERVICE_NAME"
    echo "🔗 Load Balancer: $ALB_DNS"
    echo ""
    
    # Show next steps
    echo -e "${YELLOW}NEXT STEPS:${NC}"
    echo ""
    echo "1. Build and push your Docker image:"
    echo "   ${GREEN}aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_URL${NC}"
    echo "   ${GREEN}docker build -t $PROJECT_NAME .${NC}"
    echo "   ${GREEN}docker tag $PROJECT_NAME:latest $ECR_URL:latest${NC}"
    echo "   ${GREEN}docker push $ECR_URL:latest${NC}"
    echo ""
    echo "2. Deploy using GitHub Actions:"
    echo "   ${GREEN}git push origin main${NC}"
    echo ""
    echo "3. Monitor deployment:"
    echo "   ${GREEN}./scripts/check-ecs-deployment.sh${NC}"
    echo ""
    echo "4. Access your application:"
    echo "   ${GREEN}curl $APP_URL/actuator/health${NC}"
    echo ""
}

# Cleanup on error
cleanup() {
    if [ -f "$TERRAFORM_DIR/tfplan" ]; then
        rm -f "$TERRAFORM_DIR/tfplan"
    fi
}

trap cleanup EXIT

# Main execution
main() {
    check_prerequisites
    init_terraform
    plan_terraform
    apply_terraform
    get_outputs
    
    echo -e "${GREEN}🎉 ECS infrastructure deployment completed successfully!${NC}"
}

# Run main function
main