#!/bin/bash

# Oddiya Master Deployment Script
# Consolidated deployment functionality for AWS infrastructure and application
# Usage: ./scripts/deployment/deploy-master.sh [environment] [action] [options]

set -e

# Script metadata
SCRIPT_VERSION="2.0.0"
SCRIPT_NAME="Oddiya Master Deploy"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
DEFAULT_ENVIRONMENT="dev"
DEFAULT_ACTION="plan"
DEFAULT_REGION="ap-northeast-2"

# Parse arguments
ENVIRONMENT="${1:-$DEFAULT_ENVIRONMENT}"
ACTION="${2:-$DEFAULT_ACTION}"
SKIP_TERRAFORM="${3:-false}"
SKIP_DOCKER="${4:-false}"
DRY_RUN="${5:-false}"
FORCE_REBUILD="${6:-false}"

# Derived configuration
AWS_REGION="${AWS_REGION:-$DEFAULT_REGION}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || echo "")
ECR_REPOSITORY="oddiya"
ECS_CLUSTER="oddiya-${ENVIRONMENT}"
ECS_SERVICE="oddiya-${ENVIRONMENT}"
CONTAINER_NAME="oddiya"
TERRAFORM_DIR="terraform/ecs-infrastructure"

# Banner
echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          ${SCRIPT_NAME} v${SCRIPT_VERSION}          ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo -e "${CYAN}Environment: ${ENVIRONMENT}${NC}"
echo -e "${CYAN}Action: ${ACTION}${NC}"
echo -e "${CYAN}AWS Region: ${AWS_REGION}${NC}"
if [[ -n "$AWS_ACCOUNT_ID" ]]; then
    echo -e "${CYAN}AWS Account: ${AWS_ACCOUNT_ID}${NC}"
fi
echo ""

# Functions
print_step() {
    echo -e "\n${BLUE}🔧 $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${PURPLE}ℹ️ $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    print_step "Checking prerequisites"
    
    local missing_tools=()
    
    # Check required tools
    if ! command -v aws &> /dev/null; then
        missing_tools+=("aws-cli")
    fi
    
    if ! command -v terraform &> /dev/null; then
        missing_tools+=("terraform")
    fi
    
    if ! command -v docker &> /dev/null; then
        missing_tools+=("docker")
    fi
    
    if ! command -v java &> /dev/null; then
        missing_tools+=("java")
    fi
    
    # PostgreSQL-specific tools
    if ! command -v psql &> /dev/null; then
        print_warning "PostgreSQL client (psql) not found - database operations may be limited"
    fi
    
    if ! command -v pg_isready &> /dev/null; then
        print_warning "pg_isready not found - database health checks may be limited"
    fi
    
    if ! command -v jq &> /dev/null; then
        missing_tools+=("jq")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        print_error "Missing required tools: ${missing_tools[*]}"
        print_info "Please install missing tools and try again"
        exit 1
    fi
    
    # Check AWS credentials
    if [[ -z "$AWS_ACCOUNT_ID" ]]; then
        print_error "AWS credentials not configured or invalid"
        print_info "Run 'aws configure' to set up credentials"
        exit 1
    fi
    
    # Check Terraform directory
    if [[ ! -d "$TERRAFORM_DIR" ]]; then
        print_error "Terraform directory not found: $TERRAFORM_DIR"
        exit 1
    fi
    
    # Check deployment scripts
    if [[ ! -f "scripts/deployment/migrate-database.sh" ]]; then
        print_warning "Database migration script not found"
    fi
    
    if [[ ! -f "scripts/monitoring/postgresql-health-check.sh" ]]; then
        print_warning "PostgreSQL health check script not found"
    fi
    
    print_success "All prerequisites met"
}

# Run database migrations
run_database_migrations() {
    if [[ "$ACTION" != "apply" || "$DRY_RUN" == "true" ]]; then
        print_info "Skipping database migrations (action: $ACTION, dry_run: $DRY_RUN)"
        return 0
    fi
    
    print_step "Running database migrations"
    
    if [[ ! -f "scripts/deployment/migrate-database.sh" ]]; then
        print_warning "Migration script not found, skipping database migrations"
        return 0
    fi
    
    # Check if migration files exist
    local migration_count=0
    if [[ -d "src/main/resources/db/migration" ]]; then
        migration_count=$(find src/main/resources/db/migration -name "V*.sql" -type f | wc -l || echo "0")
    fi
    
    if [[ $migration_count -eq 0 ]]; then
        print_info "No database migration files found, skipping migrations"
        return 0
    fi
    
    print_info "Found $migration_count migration files"
    
    # Run migrations
    chmod +x scripts/deployment/migrate-database.sh
    if ./scripts/deployment/migrate-database.sh "$ENVIRONMENT"; then
        print_success "Database migrations completed successfully"
    else
        print_error "Database migrations failed"
        exit 1
    fi
}

# Check database connectivity
check_database_connectivity() {
    if [[ "$ACTION" != "apply" || "$DRY_RUN" == "true" ]]; then
        print_info "Skipping database connectivity check"
        return 0
    fi
    
    print_step "Checking database connectivity"
    
    if [[ ! -f "scripts/monitoring/postgresql-health-check.sh" ]]; then
        print_warning "PostgreSQL health check script not found, skipping connectivity check"
        return 0
    fi
    
    # Run health check
    chmod +x scripts/monitoring/postgresql-health-check.sh
    if ./scripts/monitoring/postgresql-health-check.sh "$ENVIRONMENT"; then
        print_success "Database connectivity check passed"
    else
        print_warning "Database connectivity check completed with warnings"
        # Don't fail deployment for warnings
    fi
}

# Build application
build_application() {
    if [[ "$SKIP_DOCKER" == "true" ]]; then
        print_warning "Skipping application build (SKIP_DOCKER=true)"
        return 0
    fi
    
    print_step "Building application"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY RUN] Would run: ./gradlew clean bootJar"
        return 0
    fi
    
    # Clean and build
    if [[ "$FORCE_REBUILD" == "true" ]]; then
        ./gradlew clean
    fi
    
    ./gradlew bootJar
    
    print_success "Application built successfully"
}

# Setup ECR repository
setup_ecr() {
    if [[ "$SKIP_DOCKER" == "true" ]]; then
        print_warning "Skipping ECR setup (SKIP_DOCKER=true)"
        return 0
    fi
    
    print_step "Setting up ECR repository"
    
    # Check if repository exists
    if aws ecr describe-repositories --repository-names "$ECR_REPOSITORY" --region "$AWS_REGION" &>/dev/null; then
        print_info "ECR repository '$ECR_REPOSITORY' already exists"
    else
        if [[ "$DRY_RUN" == "true" ]]; then
            print_info "[DRY RUN] Would create ECR repository: $ECR_REPOSITORY"
        else
            print_info "Creating ECR repository: $ECR_REPOSITORY"
            aws ecr create-repository \
                --repository-name "$ECR_REPOSITORY" \
                --region "$AWS_REGION" \
                --image-scanning-configuration scanOnPush=true
        fi
    fi
    
    print_success "ECR repository ready"
}

# Build and push Docker image
build_and_push_image() {
    if [[ "$SKIP_DOCKER" == "true" ]]; then
        print_warning "Skipping Docker build and push (SKIP_DOCKER=true)"
        return 0
    fi
    
    print_step "Building and pushing Docker image"
    
    local ecr_uri="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    local image_tag="${ecr_uri}/${ECR_REPOSITORY}:latest"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY RUN] Would build and push Docker image to: $image_tag"
        return 0
    fi
    
    # Login to ECR
    aws ecr get-login-password --region "$AWS_REGION" | \
        docker login --username AWS --password-stdin "$ecr_uri"
    
    # Build image
    docker build -t "$ECR_REPOSITORY:latest" .
    
    # Tag for ECR
    docker tag "$ECR_REPOSITORY:latest" "$image_tag"
    
    # Push to ECR
    docker push "$image_tag"
    
    print_success "Docker image built and pushed successfully"
}

# Deploy infrastructure
deploy_infrastructure() {
    if [[ "$SKIP_TERRAFORM" == "true" ]]; then
        print_warning "Skipping Terraform deployment (SKIP_TERRAFORM=true)"
        return 0
    fi
    
    print_step "Deploying infrastructure with Terraform"
    
    cd "$TERRAFORM_DIR"
    
    # Initialize Terraform
    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY RUN] Would run: terraform init"
        print_info "[DRY RUN] Would run: terraform $ACTION"
    else
        terraform init
        
        case "$ACTION" in
            "plan")
                terraform plan -var="environment=$ENVIRONMENT"
                ;;
            "apply")
                terraform apply -var="environment=$ENVIRONMENT" -auto-approve
                ;;
            "destroy")
                terraform destroy -var="environment=$ENVIRONMENT" -auto-approve
                ;;
            *)
                print_error "Invalid Terraform action: $ACTION"
                print_info "Valid actions: plan, apply, destroy"
                cd - > /dev/null
                exit 1
                ;;
        esac
    fi
    
    cd - > /dev/null
    print_success "Infrastructure deployment completed"
}

# Update ECS service
update_ecs_service() {
    if [[ "$SKIP_DOCKER" == "true" || "$ACTION" != "apply" ]]; then
        print_warning "Skipping ECS service update"
        return 0
    fi
    
    print_step "Updating ECS service"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY RUN] Would update ECS service: $ECS_SERVICE"
        return 0
    fi
    
    # Check if service exists
    if aws ecs describe-services \
        --cluster "$ECS_CLUSTER" \
        --services "$ECS_SERVICE" \
        --region "$AWS_REGION" &>/dev/null; then
        
        # Force new deployment
        aws ecs update-service \
            --cluster "$ECS_CLUSTER" \
            --service "$ECS_SERVICE" \
            --force-new-deployment \
            --region "$AWS_REGION" > /dev/null
        
        print_success "ECS service updated successfully"
    else
        print_warning "ECS service not found, will be created by Terraform"
    fi
}

# Verify deployment
verify_deployment() {
    if [[ "$ACTION" != "apply" || "$DRY_RUN" == "true" ]]; then
        print_info "Skipping deployment verification"
        return 0
    fi
    
    print_step "Verifying deployment"
    
    # Check ECS service status
    print_info "Checking ECS service status..."
    local service_status=$(aws ecs describe-services \
        --cluster "$ECS_CLUSTER" \
        --services "$ECS_SERVICE" \
        --region "$AWS_REGION" \
        --query "services[0].status" \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [[ "$service_status" == "ACTIVE" ]]; then
        print_success "ECS service is active"
    else
        print_warning "ECS service status: $service_status"
    fi
    
    # Get load balancer DNS
    local alb_dns=$(aws elbv2 describe-load-balancers \
        --region "$AWS_REGION" \
        --query "LoadBalancers[?contains(LoadBalancerName, 'oddiya-${ENVIRONMENT}')].DNSName" \
        --output text 2>/dev/null || echo "")
    
    if [[ -n "$alb_dns" ]]; then
        print_success "Application endpoint: http://$alb_dns"
    fi
}

# Cleanup function
cleanup() {
    if [[ $? -ne 0 ]]; then
        print_error "Deployment failed!"
        print_info "Check the logs above for details"
    fi
}

# Show help
show_help() {
    echo -e "${YELLOW}Usage:${NC}"
    echo "  $0 [environment] [action] [skip_terraform] [skip_docker] [dry_run] [force_rebuild]"
    echo ""
    echo -e "${YELLOW}Arguments:${NC}"
    echo "  environment      Environment to deploy (dev|staging|prod) [default: dev]"
    echo "  action          Terraform action (plan|apply|destroy) [default: plan]"
    echo "  skip_terraform  Skip Terraform operations (true|false) [default: false]"
    echo "  skip_docker     Skip Docker operations (true|false) [default: false]"
    echo "  dry_run         Show what would be done (true|false) [default: false]"
    echo "  force_rebuild   Force clean rebuild (true|false) [default: false]"
    echo ""
    echo -e "${YELLOW}Features:${NC}"
    echo "  • Automated PostgreSQL database migrations"
    echo "  • PostgreSQL health checks and connectivity validation"
    echo "  • Multi-environment deployment support"
    echo "  • ECR image management with security scanning"
    echo "  • ECS service deployment and monitoring"
    echo "  • Infrastructure as Code with Terraform"
    echo ""
    echo -e "${YELLOW}Examples:${NC}"
    echo "  $0                                    # Plan deployment to dev"
    echo "  $0 prod apply                        # Deploy to production (includes DB migrations)"
    echo "  $0 staging plan true false           # Plan staging, skip Terraform"
    echo "  $0 dev apply false false true        # Dry run dev deployment"
    echo "  $0 dev destroy                       # Destroy dev environment"
    echo ""
    echo -e "${YELLOW}Prerequisites:${NC}"
    echo "  • AWS CLI configured with appropriate credentials"
    echo "  • Terraform installed and configured"
    echo "  • Docker runtime available"
    echo "  • Java 21+ installed"
    echo "  • PostgreSQL client tools (psql, pg_isready) - recommended"
    echo "  • jq for JSON processing"
}

# Main execution
main() {
    # Handle help request
    if [[ "$1" == "-h" || "$1" == "--help" || "$1" == "help" ]]; then
        show_help
        exit 0
    fi
    
    # Setup cleanup trap
    trap cleanup EXIT
    
    # Execute deployment steps
    check_prerequisites
    run_database_migrations
    build_application
    setup_ecr
    build_and_push_image
    deploy_infrastructure
    update_ecs_service
    verify_deployment
    check_database_connectivity
    
    # Success message
    echo ""
    print_success "🎉 Deployment completed successfully!"
    
    if [[ "$ACTION" == "apply" && "$DRY_RUN" != "true" ]]; then
        print_info "Your application should be available shortly"
        print_info "Check the AWS Console for detailed status"
    fi
}

# Run main function
main "$@"