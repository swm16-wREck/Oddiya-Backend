#!/bin/bash

# AWS Deployment Script for Oddiya Application
# This script automates the complete deployment process to AWS
# Usage: ./scripts/deploy-to-aws.sh [environment] [options]

set -e  # Exit on any error

# Default values
ENVIRONMENT="${1:-dev}"
SKIP_TERRAFORM="${2:-false}"
SKIP_DOCKER="${3:-false}"
TERRAFORM_ACTION="${4:-apply}"
DRY_RUN="${5:-false}"
FORCE_REBUILD="${6:-false}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
AWS_REGION="ap-northeast-2"
ECR_REPOSITORY="oddiya"
ECS_CLUSTER="oddiya-${ENVIRONMENT}"
ECS_SERVICE="oddiya-${ENVIRONMENT}"
CONTAINER_NAME="oddiya"

# Logging function
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}" >&2
}

warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

# Function to show usage
show_usage() {
    cat << EOF
Usage: $0 [environment] [skip_terraform] [skip_docker] [terraform_action] [dry_run] [force_rebuild]

Arguments:
  environment      Target environment (dev, staging, prod) [default: dev]
  skip_terraform   Skip Terraform deployment (true/false) [default: false]
  skip_docker      Skip Docker build and push (true/false) [default: false]
  terraform_action Terraform action (plan/apply/destroy) [default: apply]
  dry_run         Only show what would be done (true/false) [default: false]
  force_rebuild   Force rebuild even if no changes detected (true/false) [default: false]

Examples:
  $0 dev                          # Deploy to dev environment
  $0 prod false false apply       # Full deployment to prod
  $0 dev true false               # Skip Terraform, only deploy application
  $0 prod false false plan true   # Dry run - show what would be done
  
Environment Variables:
  AWS_PROFILE                     # AWS profile to use
  SKIP_CONFIRMATION              # Skip confirmation prompts (true/false)
  DOCKER_BUILD_ARGS              # Additional Docker build arguments
  TERRAFORM_WORKSPACE            # Terraform workspace to use
EOF
}

# Function to check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check required commands
    local required_commands=("aws" "docker" "terraform" "jq" "git")
    for cmd in "${required_commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            error "$cmd is required but not installed"
            exit 1
        fi
    done
    
    # Check AWS CLI configuration
    if ! aws sts get-caller-identity &> /dev/null; then
        error "AWS CLI not configured or credentials invalid"
        exit 1
    fi
    
    # Check Docker daemon
    if ! docker info &> /dev/null; then
        error "Docker daemon not running"
        exit 1
    fi
    
    # Check if we're in the right directory
    if [ ! -f "build.gradle" ] || [ ! -d "terraform" ]; then
        error "Script must be run from the project root directory"
        exit 1
    fi
    
    success "All prerequisites met"
}

# Function to validate environment
validate_environment() {
    log "Validating environment: $ENVIRONMENT"
    
    case $ENVIRONMENT in
        dev|staging|prod)
            success "Environment '$ENVIRONMENT' is valid"
            ;;
        *)
            error "Invalid environment: $ENVIRONMENT. Must be one of: dev, staging, prod"
            exit 1
            ;;
    esac
    
    # Check if Terraform variables file exists
    local tfvars_file="terraform/environments/${ENVIRONMENT}.tfvars"
    if [ ! -f "$tfvars_file" ] && [ ! -f "terraform/terraform.tfvars" ]; then
        warning "Terraform variables file not found at $tfvars_file or terraform/terraform.tfvars"
        warning "Make sure to create one before running Terraform"
    fi
}

# Function to get current Git commit hash
get_git_commit() {
    git rev-parse --short HEAD
}

# Function to check if there are uncommitted changes
check_git_status() {
    if [ "$ENVIRONMENT" = "prod" ] && [ -n "$(git status --porcelain)" ]; then
        warning "There are uncommitted changes in the repository"
        if [ "$SKIP_CONFIRMATION" != "true" ]; then
            read -p "Continue with deployment? (y/n): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                log "Deployment cancelled"
                exit 0
            fi
        fi
    fi
}

# Function to run pre-deployment checks
pre_deployment_checks() {
    log "Running pre-deployment checks..."
    
    if [ "$SKIP_TERRAFORM" = "false" ]; then
        # Check Terraform state
        cd terraform
        
        if [ "$TERRAFORM_WORKSPACE" != "" ]; then
            log "Selecting Terraform workspace: $TERRAFORM_WORKSPACE"
            terraform workspace select "$TERRAFORM_WORKSPACE" || terraform workspace new "$TERRAFORM_WORKSPACE"
        fi
        
        terraform init -upgrade
        terraform validate
        
        cd ..
    fi
    
    if [ "$SKIP_DOCKER" = "false" ]; then
        # Check ECR repository exists
        if ! aws ecr describe-repositories --repository-names "$ECR_REPOSITORY" --region "$AWS_REGION" &> /dev/null; then
            log "Creating ECR repository: $ECR_REPOSITORY"
            aws ecr create-repository --repository-name "$ECR_REPOSITORY" --region "$AWS_REGION"
        fi
    fi
    
    success "Pre-deployment checks completed"
}

# Function to build and test application
build_and_test() {
    if [ "$SKIP_DOCKER" = "true" ]; then
        log "Skipping application build"
        return 0
    fi
    
    log "Building and testing application..."
    
    # Check if we need to rebuild
    local git_commit=$(get_git_commit)
    local image_exists=false
    
    if [ "$FORCE_REBUILD" = "false" ]; then
        # Check if image already exists with this commit hash
        local ecr_registry=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
        if aws ecr describe-images --repository-name "$ECR_REPOSITORY" --image-ids imageTag="$git_commit" --region "$AWS_REGION" &> /dev/null; then
            image_exists=true
            log "Docker image already exists for commit $git_commit"
        fi
    fi
    
    if [ "$image_exists" = "false" ] || [ "$FORCE_REBUILD" = "true" ]; then
        # Clean and build application
        log "Building Java application..."
        ./gradlew clean build -x test
        
        # Run tests
        log "Running tests..."
        ./gradlew test
        
        # Generate test report
        if [ -f "build/reports/tests/test/index.html" ]; then
            log "Test report generated: build/reports/tests/test/index.html"
        fi
        
        success "Application build and test completed"
    else
        log "Skipping build - image already exists and force rebuild is disabled"
    fi
}

# Function to build and push Docker image
build_and_push_docker() {
    if [ "$SKIP_DOCKER" = "true" ]; then
        log "Skipping Docker build"
        return 0
    fi
    
    log "Building and pushing Docker image..."
    
    local git_commit=$(get_git_commit)
    local ecr_registry=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
    local image_uri="$ecr_registry/$ECR_REPOSITORY"
    
    # Login to ECR
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ecr_registry"
    
    # Check if image already exists
    if [ "$FORCE_REBUILD" = "false" ] && aws ecr describe-images --repository-name "$ECR_REPOSITORY" --image-ids imageTag="$git_commit" --region "$AWS_REGION" &> /dev/null; then
        log "Docker image already exists for commit $git_commit, skipping build"
        echo "$image_uri:$git_commit"
        return 0
    fi
    
    # Build Docker image
    log "Building Docker image with tag: $git_commit"
    if [ "$DRY_RUN" = "true" ]; then
        log "[DRY RUN] Would build: docker build ${DOCKER_BUILD_ARGS} -t $image_uri:$git_commit -t $image_uri:latest ."
    else
        docker build ${DOCKER_BUILD_ARGS} -t "$image_uri:$git_commit" -t "$image_uri:latest" .
        
        # Push images
        log "Pushing Docker images..."
        docker push "$image_uri:$git_commit"
        docker push "$image_uri:latest"
        
        success "Docker image pushed: $image_uri:$git_commit"
    fi
    
    echo "$image_uri:$git_commit"
}

# Function to deploy infrastructure with Terraform
deploy_infrastructure() {
    if [ "$SKIP_TERRAFORM" = "true" ]; then
        log "Skipping Terraform deployment"
        return 0
    fi
    
    log "Deploying infrastructure with Terraform..."
    
    cd terraform
    
    # Select appropriate tfvars file
    local tfvars_args=""
    if [ -f "environments/${ENVIRONMENT}.tfvars" ]; then
        tfvars_args="-var-file=environments/${ENVIRONMENT}.tfvars"
    elif [ -f "terraform.tfvars" ]; then
        tfvars_args="-var-file=terraform.tfvars"
    fi
    
    # Run Terraform plan
    log "Running Terraform plan..."
    if [ "$DRY_RUN" = "true" ]; then
        terraform plan $tfvars_args -var="environment=$ENVIRONMENT" -out="tfplan-$ENVIRONMENT"
        log "[DRY RUN] Terraform plan completed. Review the plan above."
        cd ..
        return 0
    fi
    
    case $TERRAFORM_ACTION in
        plan)
            terraform plan $tfvars_args -var="environment=$ENVIRONMENT" -out="tfplan-$ENVIRONMENT"
            log "Terraform plan saved as tfplan-$ENVIRONMENT"
            ;;
        apply)
            terraform plan $tfvars_args -var="environment=$ENVIRONMENT" -out="tfplan-$ENVIRONMENT"
            
            if [ "$SKIP_CONFIRMATION" != "true" ]; then
                echo
                log "Terraform plan completed. Please review the changes above."
                read -p "Do you want to apply these changes? (y/n): " -n 1 -r
                echo
                if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                    log "Terraform apply cancelled"
                    cd ..
                    return 0
                fi
            fi
            
            terraform apply "tfplan-$ENVIRONMENT"
            success "Infrastructure deployment completed"
            ;;
        destroy)
            warning "This will DESTROY all infrastructure for environment: $ENVIRONMENT"
            if [ "$SKIP_CONFIRMATION" != "true" ]; then
                read -p "Are you sure you want to destroy all resources? Type 'yes' to confirm: " confirm
                if [ "$confirm" != "yes" ]; then
                    log "Terraform destroy cancelled"
                    cd ..
                    return 0
                fi
            fi
            
            terraform destroy $tfvars_args -var="environment=$ENVIRONMENT" -auto-approve
            warning "Infrastructure destroyed"
            ;;
        *)
            error "Invalid Terraform action: $TERRAFORM_ACTION"
            cd ..
            exit 1
            ;;
    esac
    
    cd ..
}

# Function to deploy application to ECS
deploy_application() {
    if [ "$SKIP_DOCKER" = "true" ] || [ "$TERRAFORM_ACTION" = "destroy" ]; then
        log "Skipping application deployment"
        return 0
    fi
    
    log "Deploying application to ECS..."
    
    local git_commit=$(get_git_commit)
    local ecr_registry=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
    local image_uri="$ecr_registry/$ECR_REPOSITORY:$git_commit"
    
    if [ "$DRY_RUN" = "true" ]; then
        log "[DRY RUN] Would deploy image: $image_uri"
        log "[DRY RUN] Would update ECS service: $ECS_SERVICE in cluster: $ECS_CLUSTER"
        return 0
    fi
    
    # Check if ECS cluster and service exist
    if ! aws ecs describe-clusters --clusters "$ECS_CLUSTER" --region "$AWS_REGION" &> /dev/null; then
        error "ECS cluster $ECS_CLUSTER not found. Make sure Terraform deployment completed successfully."
        exit 1
    fi
    
    if ! aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" --region "$AWS_REGION" &> /dev/null; then
        error "ECS service $ECS_SERVICE not found in cluster $ECS_CLUSTER"
        exit 1
    fi
    
    # Get current task definition
    log "Getting current task definition..."
    aws ecs describe-task-definition \
        --task-definition "$ECS_SERVICE" \
        --region "$AWS_REGION" \
        --query 'taskDefinition' > task-definition.json
    
    # Update task definition with new image
    log "Updating task definition with new image: $image_uri"
    jq --arg IMAGE "$image_uri" \
       '.containerDefinitions[0].image = $IMAGE | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .placementConstraints, .compatibilities, .registeredAt, .registeredBy)' \
       task-definition.json > new-task-definition.json
    
    # Register new task definition
    log "Registering new task definition..."
    aws ecs register-task-definition \
        --region "$AWS_REGION" \
        --cli-input-json file://new-task-definition.json > /dev/null
    
    # Update ECS service
    log "Updating ECS service..."
    aws ecs update-service \
        --cluster "$ECS_CLUSTER" \
        --service "$ECS_SERVICE" \
        --task-definition "$ECS_SERVICE" \
        --region "$AWS_REGION" > /dev/null
    
    # Wait for deployment to complete
    log "Waiting for deployment to complete..."
    local max_wait=1800  # 30 minutes
    local wait_interval=30
    local elapsed=0
    
    while [ $elapsed -lt $max_wait ]; do
        local deployment_status=$(aws ecs describe-services \
            --cluster "$ECS_CLUSTER" \
            --services "$ECS_SERVICE" \
            --region "$AWS_REGION" \
            --query 'services[0].deployments[?status==`PRIMARY`] | [0].rolloutState' \
            --output text)
        
        local running_count=$(aws ecs describe-services \
            --cluster "$ECS_CLUSTER" \
            --services "$ECS_SERVICE" \
            --region "$AWS_REGION" \
            --query 'services[0].runningCount' \
            --output text)
        
        local desired_count=$(aws ecs describe-services \
            --cluster "$ECS_CLUSTER" \
            --services "$ECS_SERVICE" \
            --region "$AWS_REGION" \
            --query 'services[0].desiredCount' \
            --output text)
        
        log "Deployment status: $deployment_status (Running: $running_count/$desired_count)"
        
        if [ "$deployment_status" = "COMPLETED" ]; then
            success "Deployment completed successfully!"
            break
        elif [ "$deployment_status" = "FAILED" ]; then
            error "Deployment failed!"
            
            # Show recent service events
            aws ecs describe-services \
                --cluster "$ECS_CLUSTER" \
                --services "$ECS_SERVICE" \
                --region "$AWS_REGION" \
                --query 'services[0].events[:5].[createdAt,message]' \
                --output table
            
            exit 1
        fi
        
        sleep $wait_interval
        elapsed=$((elapsed + wait_interval))
    done
    
    if [ $elapsed -ge $max_wait ]; then
        error "Deployment timed out after $((max_wait / 60)) minutes"
        exit 1
    fi
    
    # Cleanup temporary files
    rm -f task-definition.json new-task-definition.json
    
    success "Application deployment completed"
}

# Function to run post-deployment verification
post_deployment_verification() {
    if [ "$DRY_RUN" = "true" ] || [ "$TERRAFORM_ACTION" = "destroy" ]; then
        log "Skipping post-deployment verification"
        return 0
    fi
    
    log "Running post-deployment verification..."
    
    # Get ALB endpoint
    local alb_dns=$(aws elbv2 describe-load-balancers \
        --names "oddiya-${ENVIRONMENT}-alb" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text 2>/dev/null || echo "")
    
    if [ -n "$alb_dns" ] && [ "$alb_dns" != "None" ]; then
        log "Testing health endpoint: https://$alb_dns/actuator/health"
        
        local max_attempts=10
        local attempt=1
        
        while [ $attempt -le $max_attempts ]; do
            if curl -f -s "https://$alb_dns/actuator/health" > /dev/null; then
                success "Health check passed!"
                break
            else
                log "Health check attempt $attempt/$max_attempts failed, waiting..."
                sleep 30
                attempt=$((attempt + 1))
            fi
        done
        
        if [ $attempt -gt $max_attempts ]; then
            warning "Health check failed after $max_attempts attempts"
        fi
    else
        log "ALB DNS not found, skipping health check"
    fi
    
    # Show service information
    log "Service information:"
    aws ecs describe-services \
        --cluster "$ECS_CLUSTER" \
        --services "$ECS_SERVICE" \
        --region "$AWS_REGION" \
        --query 'services[0].{ServiceName:serviceName,Status:status,RunningCount:runningCount,DesiredCount:desiredCount,TaskDefinition:taskDefinition}' \
        --output table
    
    success "Post-deployment verification completed"
}

# Function to cleanup temporary files
cleanup() {
    log "Cleaning up temporary files..."
    rm -f task-definition.json new-task-definition.json
    rm -f terraform/tfplan-*
}

# Function to show deployment summary
show_deployment_summary() {
    log "Deployment Summary:"
    echo "==================="
    echo "Environment: $ENVIRONMENT"
    echo "Git Commit: $(get_git_commit)"
    echo "AWS Region: $AWS_REGION"
    echo "ECS Cluster: $ECS_CLUSTER"
    echo "ECS Service: $ECS_SERVICE"
    echo "Terraform Action: $TERRAFORM_ACTION"
    echo "Skip Terraform: $SKIP_TERRAFORM"
    echo "Skip Docker: $SKIP_DOCKER"
    echo "Dry Run: $DRY_RUN"
    echo "==================="
}

# Main execution function
main() {
    # Show help if requested
    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        show_usage
        exit 0
    fi
    
    log "Starting AWS deployment process..."
    
    # Trap cleanup function on exit
    trap cleanup EXIT
    
    # Run deployment steps
    show_deployment_summary
    check_prerequisites
    validate_environment
    check_git_status
    pre_deployment_checks
    
    if [ "$SKIP_CONFIRMATION" != "true" ] && [ "$DRY_RUN" = "false" ] && [ "$ENVIRONMENT" = "prod" ]; then
        warning "You are about to deploy to PRODUCTION environment!"
        read -p "Are you sure you want to continue? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log "Deployment cancelled"
            exit 0
        fi
    fi
    
    build_and_test
    local docker_image=$(build_and_push_docker)
    deploy_infrastructure
    deploy_application
    post_deployment_verification
    
    if [ "$DRY_RUN" = "true" ]; then
        success "Dry run completed successfully!"
        log "No actual changes were made to the infrastructure or application."
    else
        success "Deployment completed successfully!"
        
        if [ "$TERRAFORM_ACTION" != "destroy" ]; then
            log "Access your application at:"
            if [ -n "$docker_image" ]; then
                log "Docker Image: $docker_image"
            fi
            
            # Try to get CloudFront URL
            local cloudfront_domain=$(aws cloudfront list-distributions \
                --query "DistributionList.Items[?Comment=='Oddiya ${ENVIRONMENT} CDN'].DomainName" \
                --output text 2>/dev/null || echo "")
            
            if [ -n "$cloudfront_domain" ] && [ "$cloudfront_domain" != "None" ]; then
                log "Application URL: https://$cloudfront_domain"
            fi
        fi
    fi
}

# Run main function with all arguments
main "$@"