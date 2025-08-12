#!/bin/bash

# Oddiya Infrastructure Deployment Script
# Usage: ./deploy-infrastructure.sh [environment] [action]
# Environments: dev, staging, prod
# Actions: plan, apply, destroy

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

# Default values
ENVIRONMENT=${1:-dev}
ACTION=${2:-plan}
AUTO_APPROVE=${3:-false}

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Validation functions
validate_environment() {
    case $ENVIRONMENT in
        dev|staging|prod)
            log "Environment: $ENVIRONMENT"
            ;;
        *)
            error "Invalid environment: $ENVIRONMENT. Must be one of: dev, staging, prod"
            ;;
    esac
}

validate_action() {
    case $ACTION in
        plan|apply|destroy)
            log "Action: $ACTION"
            ;;
        *)
            error "Invalid action: $ACTION. Must be one of: plan, apply, destroy"
            ;;
    esac
}

check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if terraform is installed
    if ! command -v terraform &> /dev/null; then
        error "Terraform is not installed or not in PATH"
    fi
    
    # Check if AWS CLI is installed
    if ! command -v aws &> /dev/null; then
        error "AWS CLI is not installed or not in PATH"
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        error "AWS credentials not configured or expired"
    fi
    
    # Check if terraform directory exists
    if [[ ! -d "$TERRAFORM_DIR" ]]; then
        error "Terraform directory not found: $TERRAFORM_DIR"
    fi
    
    # Check if environment-specific tfvars exists
    local tfvars_file="$TERRAFORM_DIR/environments/$ENVIRONMENT/terraform.tfvars"
    if [[ ! -f "$tfvars_file" ]]; then
        error "Environment-specific tfvars file not found: $tfvars_file"
    fi
    
    success "Prerequisites check passed"
}

setup_terraform_backend() {
    log "Setting up Terraform backend..."
    
    local state_bucket="oddiya-terraform-state"
    local lock_table="terraform-state-lock"
    
    # Create S3 bucket for state if it doesn't exist
    if ! aws s3 ls "s3://$state_bucket" &> /dev/null; then
        log "Creating S3 bucket for Terraform state: $state_bucket"
        aws s3 mb "s3://$state_bucket" --region ap-northeast-2
        
        # Enable versioning
        aws s3api put-bucket-versioning \
            --bucket "$state_bucket" \
            --versioning-configuration Status=Enabled
        
        # Enable encryption
        aws s3api put-bucket-encryption \
            --bucket "$state_bucket" \
            --server-side-encryption-configuration '{
                "Rules": [
                    {
                        "ApplyServerSideEncryptionByDefault": {
                            "SSEAlgorithm": "AES256"
                        }
                    }
                ]
            }'
    fi
    
    # Create DynamoDB table for state locking if it doesn't exist
    if ! aws dynamodb describe-table --table-name "$lock_table" &> /dev/null; then
        log "Creating DynamoDB table for Terraform state locking: $lock_table"
        aws dynamodb create-table \
            --table-name "$lock_table" \
            --attribute-definitions AttributeName=LockID,AttributeType=S \
            --key-schema AttributeName=LockID,KeyType=HASH \
            --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
            --region ap-northeast-2
        
        # Wait for table to be created
        aws dynamodb wait table-exists --table-name "$lock_table"
    fi
    
    success "Terraform backend setup completed"
}

init_terraform() {
    log "Initializing Terraform..."
    cd "$TERRAFORM_DIR"
    
    terraform init \
        -backend-config="bucket=oddiya-terraform-state" \
        -backend-config="key=infrastructure/$ENVIRONMENT/terraform.tfstate" \
        -backend-config="region=ap-northeast-2" \
        -backend-config="encrypt=true" \
        -backend-config="dynamodb_table=terraform-state-lock"
    
    success "Terraform initialization completed"
}

validate_terraform() {
    log "Validating Terraform configuration..."
    cd "$TERRAFORM_DIR"
    
    terraform validate
    
    success "Terraform validation completed"
}

plan_infrastructure() {
    log "Planning infrastructure changes..."
    cd "$TERRAFORM_DIR"
    
    terraform plan \
        -var-file="environments/$ENVIRONMENT/terraform.tfvars" \
        -out="tfplan-$ENVIRONMENT" \
        -detailed-exitcode
    
    local exit_code=$?
    
    case $exit_code in
        0)
            success "No changes required"
            return 0
            ;;
        1)
            error "Terraform plan failed"
            ;;
        2)
            success "Changes planned successfully"
            return 2
            ;;
    esac
}

apply_infrastructure() {
    log "Applying infrastructure changes..."
    cd "$TERRAFORM_DIR"
    
    if [[ "$AUTO_APPROVE" == "true" ]]; then
        terraform apply -auto-approve "tfplan-$ENVIRONMENT"
    else
        terraform apply "tfplan-$ENVIRONMENT"
    fi
    
    success "Infrastructure deployment completed"
}

destroy_infrastructure() {
    warn "DESTRUCTIVE ACTION: This will destroy all infrastructure for environment: $ENVIRONMENT"
    
    if [[ "$AUTO_APPROVE" != "true" ]]; then
        echo -n "Are you sure you want to destroy the infrastructure? (type 'yes' to confirm): "
        read -r confirmation
        if [[ "$confirmation" != "yes" ]]; then
            log "Destruction cancelled"
            exit 0
        fi
    fi
    
    cd "$TERRAFORM_DIR"
    
    if [[ "$AUTO_APPROVE" == "true" ]]; then
        terraform destroy \
            -var-file="environments/$ENVIRONMENT/terraform.tfvars" \
            -auto-approve
    else
        terraform destroy \
            -var-file="environments/$ENVIRONMENT/terraform.tfvars"
    fi
    
    success "Infrastructure destruction completed"
}

show_terraform_output() {
    log "Retrieving Terraform outputs..."
    cd "$TERRAFORM_DIR"
    
    terraform output
}

# Production safety checks
production_safety_check() {
    if [[ "$ENVIRONMENT" == "prod" && "$ACTION" == "destroy" ]]; then
        error "Production destruction requires manual confirmation. Use AUTO_APPROVE=true if you really want to destroy production."
    fi
    
    if [[ "$ENVIRONMENT" == "prod" && "$ACTION" == "apply" ]]; then
        warn "Deploying to production environment"
        
        if [[ "$AUTO_APPROVE" != "true" ]]; then
            echo -n "Are you sure you want to deploy to production? (type 'DEPLOY_PROD' to confirm): "
            read -r confirmation
            if [[ "$confirmation" != "DEPLOY_PROD" ]]; then
                log "Production deployment cancelled"
                exit 0
            fi
        fi
    fi
}

# Main execution
main() {
    log "Starting Oddiya infrastructure deployment"
    log "Environment: $ENVIRONMENT"
    log "Action: $ACTION"
    
    # Validations
    validate_environment
    validate_action
    check_prerequisites
    production_safety_check
    
    # Setup
    setup_terraform_backend
    init_terraform
    validate_terraform
    
    # Execute action
    case $ACTION in
        plan)
            plan_infrastructure
            ;;
        apply)
            plan_infrastructure
            if [[ $? -eq 2 ]]; then  # Changes detected
                apply_infrastructure
                show_terraform_output
            else
                log "No changes to apply"
            fi
            ;;
        destroy)
            destroy_infrastructure
            ;;
    esac
    
    success "Infrastructure deployment script completed successfully"
}

# Script usage information
usage() {
    cat << EOF
Usage: $0 [ENVIRONMENT] [ACTION] [AUTO_APPROVE]

ENVIRONMENT:
  dev      - Development environment
  staging  - Staging environment  
  prod     - Production environment

ACTION:
  plan     - Show what changes will be made
  apply    - Apply the changes
  destroy  - Destroy the infrastructure

AUTO_APPROVE:
  true     - Auto-approve changes (use with caution)
  false    - Require manual approval (default)

Examples:
  $0 dev plan                    # Plan changes for dev environment
  $0 staging apply               # Apply changes for staging environment
  $0 prod plan                   # Plan changes for production environment
  $0 dev destroy true            # Destroy dev infrastructure with auto-approve

EOF
}

# Handle script arguments
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

# Run main function
main "$@"