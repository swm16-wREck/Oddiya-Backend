#!/bin/bash

# Oddiya AWS Infrastructure Deployment Script
# This script deploys AWS resources using Terraform

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AWS_REGION="ap-northeast-2"
ENVIRONMENT="${1:-prod}"
ACTION="${2:-plan}"

echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     Oddiya AWS Infrastructure Deploy      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo ""

# Function to check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}✗ AWS CLI is not installed${NC}"
        echo "Please install AWS CLI: https://aws.amazon.com/cli/"
        exit 1
    fi
    echo -e "${GREEN}✓ AWS CLI found${NC}"
    
    # Check Terraform
    if ! command -v terraform &> /dev/null; then
        echo -e "${RED}✗ Terraform is not installed${NC}"
        echo "Please install Terraform: https://www.terraform.io/downloads"
        exit 1
    fi
    echo -e "${GREEN}✓ Terraform found${NC}"
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}✗ AWS credentials not configured${NC}"
        echo "Please run: aws configure"
        exit 1
    fi
    echo -e "${GREEN}✓ AWS credentials configured${NC}"
    
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    echo -e "${GREEN}✓ AWS Account: ${ACCOUNT_ID}${NC}"
    echo ""
}

# Function to create S3 backend
setup_backend() {
    echo -e "${YELLOW}Setting up Terraform backend...${NC}"
    
    BUCKET_NAME="oddiya-terraform-state"
    TABLE_NAME="terraform-state-lock"
    
    # Check if bucket exists
    if aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
        echo -e "${GREEN}✓ S3 bucket already exists: $BUCKET_NAME${NC}"
    else
        echo "Creating S3 bucket for Terraform state..."
        aws s3api create-bucket \
            --bucket "$BUCKET_NAME" \
            --region "$AWS_REGION" \
            --create-bucket-configuration LocationConstraint="$AWS_REGION"
        
        # Enable versioning
        aws s3api put-bucket-versioning \
            --bucket "$BUCKET_NAME" \
            --versioning-configuration Status=Enabled
        
        # Enable encryption
        aws s3api put-bucket-encryption \
            --bucket "$BUCKET_NAME" \
            --server-side-encryption-configuration '{
                "Rules": [{
                    "ApplyServerSideEncryptionByDefault": {
                        "SSEAlgorithm": "AES256"
                    }
                }]
            }'
        
        echo -e "${GREEN}✓ S3 bucket created: $BUCKET_NAME${NC}"
    fi
    
    # Check if DynamoDB table exists
    if aws dynamodb describe-table --table-name "$TABLE_NAME" --region "$AWS_REGION" &>/dev/null; then
        echo -e "${GREEN}✓ DynamoDB table already exists: $TABLE_NAME${NC}"
    else
        echo "Creating DynamoDB table for state locking..."
        aws dynamodb create-table \
            --table-name "$TABLE_NAME" \
            --attribute-definitions AttributeName=LockID,AttributeType=S \
            --key-schema AttributeName=LockID,KeyType=HASH \
            --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
            --region "$AWS_REGION"
        
        echo "Waiting for table to be active..."
        aws dynamodb wait table-exists --table-name "$TABLE_NAME" --region "$AWS_REGION"
        echo -e "${GREEN}✓ DynamoDB table created: $TABLE_NAME${NC}"
    fi
    echo ""
}

# Function to initialize Terraform
init_terraform() {
    echo -e "${YELLOW}Initializing Terraform...${NC}"
    cd terraform
    
    terraform init -backend=true
    
    echo -e "${GREEN}✓ Terraform initialized${NC}"
    echo ""
}

# Function to create Terraform workspace
setup_workspace() {
    echo -e "${YELLOW}Setting up Terraform workspace: ${ENVIRONMENT}${NC}"
    
    # Create workspace if it doesn't exist
    if ! terraform workspace select "$ENVIRONMENT" 2>/dev/null; then
        terraform workspace new "$ENVIRONMENT"
    fi
    
    echo -e "${GREEN}✓ Using workspace: ${ENVIRONMENT}${NC}"
    echo ""
}

# Function to validate Terraform configuration
validate_terraform() {
    echo -e "${YELLOW}Validating Terraform configuration...${NC}"
    
    terraform validate
    
    echo -e "${GREEN}✓ Configuration is valid${NC}"
    echo ""
}

# Function to plan Terraform deployment
plan_terraform() {
    echo -e "${YELLOW}Planning infrastructure changes...${NC}"
    
    terraform plan \
        -var="environment=${ENVIRONMENT}" \
        -out="tfplan-${ENVIRONMENT}"
    
    echo -e "${GREEN}✓ Plan saved to: tfplan-${ENVIRONMENT}${NC}"
    echo ""
}

# Function to apply Terraform deployment
apply_terraform() {
    echo -e "${YELLOW}Applying infrastructure changes...${NC}"
    
    # Confirm before applying
    echo -e "${RED}WARNING: This will create AWS resources and incur costs!${NC}"
    read -p "Are you sure you want to proceed? (yes/no): " confirm
    
    if [[ "$confirm" != "yes" ]]; then
        echo "Deployment cancelled."
        exit 0
    fi
    
    if [[ -f "tfplan-${ENVIRONMENT}" ]]; then
        terraform apply "tfplan-${ENVIRONMENT}"
    else
        terraform apply \
            -var="environment=${ENVIRONMENT}" \
            -auto-approve
    fi
    
    echo -e "${GREEN}✓ Infrastructure deployed successfully!${NC}"
    echo ""
}

# Function to destroy infrastructure
destroy_terraform() {
    echo -e "${YELLOW}Destroying infrastructure...${NC}"
    
    # Confirm before destroying
    echo -e "${RED}WARNING: This will DESTROY all AWS resources!${NC}"
    echo -e "${RED}This action cannot be undone!${NC}"
    read -p "Type 'destroy-${ENVIRONMENT}' to confirm: " confirm
    
    if [[ "$confirm" != "destroy-${ENVIRONMENT}" ]]; then
        echo "Destruction cancelled."
        exit 0
    fi
    
    terraform destroy \
        -var="environment=${ENVIRONMENT}" \
        -auto-approve
    
    echo -e "${GREEN}✓ Infrastructure destroyed${NC}"
    echo ""
}

# Function to show outputs
show_outputs() {
    echo -e "${YELLOW}Infrastructure Outputs:${NC}"
    terraform output -json | jq '.'
    echo ""
}

# Main execution
main() {
    check_prerequisites
    
    case "$ACTION" in
        setup)
            setup_backend
            init_terraform
            setup_workspace
            validate_terraform
            echo -e "${GREEN}Setup complete! Run './deploy.sh ${ENVIRONMENT} plan' to preview changes.${NC}"
            ;;
        plan)
            init_terraform
            setup_workspace
            validate_terraform
            plan_terraform
            echo -e "${GREEN}Plan complete! Run './deploy.sh ${ENVIRONMENT} apply' to deploy.${NC}"
            ;;
        apply)
            init_terraform
            setup_workspace
            validate_terraform
            apply_terraform
            show_outputs
            ;;
        destroy)
            init_terraform
            setup_workspace
            destroy_terraform
            ;;
        output)
            init_terraform
            setup_workspace
            show_outputs
            ;;
        *)
            echo "Usage: $0 <environment> <action>"
            echo "  environment: dev, staging, prod (default: prod)"
            echo "  action: setup, plan, apply, destroy, output (default: plan)"
            echo ""
            echo "Examples:"
            echo "  $0 prod setup    # Initial setup for production"
            echo "  $0 prod plan     # Plan production deployment"
            echo "  $0 prod apply    # Deploy to production"
            echo "  $0 dev destroy   # Destroy dev environment"
            exit 1
            ;;
    esac
}

# Run main function
main