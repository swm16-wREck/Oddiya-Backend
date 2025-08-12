#!/bin/bash
# Oddiya Project Setup and Initialization Script
# Consolidates all setup functionality for local development and cloud deployment

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# ═══════════════════════════════════════════════════════════════════════════════
# SETUP CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════════

# Project configuration
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_ROOT/.env"
DOCKER_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
GRADLE_BUILD_FILE="$PROJECT_ROOT/build.gradle"

# Default configuration values
DEFAULT_DB_PASSWORD="oddiya123"
DEFAULT_SPRING_PROFILE="local"
DEFAULT_MONITORING_EMAIL="team@oddiya.com"

# Setup modes
SETUP_MODE=""
SKIP_CONFIRMATIONS=${SKIP_CONFIRMATIONS:-false}
FORCE_REINSTALL=${FORCE_REINSTALL:-false}

# ═══════════════════════════════════════════════════════════════════════════════
# DEPENDENCY VALIDATION FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Validate Java installation and version
validate_java() {
    log_info "Validating Java installation..."
    
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed"
        echo -e "${YELLOW}Install Java 21 LTS from: https://adoptium.net/${NC}"
        return 1
    fi
    
    local java_version_output=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    local java_major_version=$(echo "$java_version_output" | cut -d'.' -f1 | sed 's/[^0-9]//g')
    
    # Handle cases where version format is like "23-valhalla"
    if [[ "$java_version_output" =~ ^[0-9]+ ]]; then
        java_major_version=$(echo "$java_version_output" | grep -o '^[0-9]\+')
    fi
    
    if [ -z "$java_major_version" ] || [ "$java_major_version" -lt 21 ]; then
        log_error "Java version must be 21 or higher (found: $java_version_output)"
        return 1
    fi
    
    log_success "Java $java_version_output detected"
    return 0
}

# Validate Docker installation and status
validate_docker() {
    log_info "Validating Docker installation..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        echo -e "${YELLOW}Install Docker Desktop from: https://www.docker.com/products/docker-desktop${NC}"
        return 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        echo -e "${YELLOW}Please start Docker Desktop${NC}"
        return 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not available"
        return 1
    fi
    
    log_success "Docker is running"
    return 0
}

# Validate Gradle wrapper
validate_gradle() {
    log_info "Validating Gradle setup..."
    
    if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
        log_error "Gradle wrapper not found"
        return 1
    fi
    
    if [ ! -x "$PROJECT_ROOT/gradlew" ]; then
        chmod +x "$PROJECT_ROOT/gradlew"
        log_info "Made gradlew executable"
    fi
    
    log_success "Gradle wrapper ready"
    return 0
}

# Validate all core dependencies
validate_core_dependencies() {
    print_section "Validating Core Dependencies"
    
    local dependencies=("git" "curl" "jq")
    validate_dependencies "${dependencies[@]}" || return 1
    
    validate_java || return 1
    validate_docker || return 1
    validate_gradle || return 1
    
    log_success "All core dependencies validated"
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# ENVIRONMENT SETUP FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Create environment file from template
setup_environment_file() {
    print_section "Setting up Environment Configuration"
    
    if [ -f "$ENV_FILE" ] && [ "$FORCE_REINSTALL" != "true" ]; then
        log_warning "Environment file already exists: $ENV_FILE"
        if [ "$SKIP_CONFIRMATIONS" != "true" ]; then
            if ! confirm_action "Overwrite existing .env file?" "n"; then
                log_info "Keeping existing environment file"
                return 0
            fi
        else
            log_info "Keeping existing environment file"
            return 0
        fi
    fi
    
    log_info "Creating environment configuration file..."
    
    # Create .env file with default values
    cat > "$ENV_FILE" << 'EOF'
# ═══════════════════════════════════════════════════════════════════════════════
# Oddiya Environment Configuration
# ═══════════════════════════════════════════════════════════════════════════════

# Spring Profile
SPRING_PROFILES_ACTIVE=local

# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=oddiya
DB_USER=oddiya
DB_PASSWORD=oddiya123

# AWS Configuration
AWS_REGION=ap-northeast-2
# AWS_ACCESS_KEY_ID=your_access_key_here
# AWS_SECRET_ACCESS_KEY=your_secret_key_here

# Supabase Configuration (Optional)
# SUPABASE_URL=https://your-project.supabase.co
# SUPABASE_ANON_KEY=your_anon_key_here
# SUPABASE_SERVICE_KEY=your_service_key_here

# Google Maps API (Optional)
# GOOGLE_MAPS_API_KEY=your_google_maps_key_here

# Naver Maps API (Optional)
# NAVER_MAPS_CLIENT_ID=your_naver_client_id
# NAVER_MAPS_CLIENT_SECRET=your_naver_client_secret

# AWS Bedrock (Optional)
# BEDROCK_MODEL_ID=anthropic.claude-3-sonnet-20240229-v1:0

# Monitoring Configuration
MONITORING_EMAIL=team@oddiya.com

# Development Settings
DEBUG=false
LOG_LEVEL=INFO

# ═══════════════════════════════════════════════════════════════════════════════
# Generated on: $(date)
# ═══════════════════════════════════════════════════════════════════════════════
EOF
    
    log_success "Environment file created: $ENV_FILE"
    
    # Make the file readable only by owner for security
    chmod 600 "$ENV_FILE"
    
    echo -e "${YELLOW}📝 Please edit $ENV_FILE and configure your API keys and credentials${NC}"
    return 0
}

# Setup local development environment
setup_local_environment() {
    print_section "Setting up Local Development Environment"
    
    # Create necessary directories
    local directories=("logs" "temp" "data/postgres" "data/redis")
    for dir in "${directories[@]}"; do
        if [ ! -d "$PROJECT_ROOT/$dir" ]; then
            mkdir -p "$PROJECT_ROOT/$dir"
            log_info "Created directory: $dir"
        fi
    done
    
    # Set appropriate permissions
    chmod 755 "$PROJECT_ROOT/logs" 2>/dev/null || true
    chmod 755 "$PROJECT_ROOT/temp" 2>/dev/null || true
    
    log_success "Local environment directories ready"
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# DATABASE SETUP FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Setup PostgreSQL database
setup_database() {
    print_section "Setting up Database"
    
    # Load environment variables
    if [ -f "$ENV_FILE" ]; then
        set -a
        source "$ENV_FILE"
        set +a
    fi
    
    local db_password="${DB_PASSWORD:-$DEFAULT_DB_PASSWORD}"
    
    log_info "Starting PostgreSQL container..."
    
    # Check if PostgreSQL container is already running
    if docker ps --filter "name=oddiya-postgres" --filter "status=running" | grep -q oddiya-postgres; then
        log_info "PostgreSQL container is already running"
    else
        # Start PostgreSQL service
        cd "$PROJECT_ROOT"
        if docker-compose up -d postgres; then
            log_success "PostgreSQL container started"
            
            # Wait for database to be ready
            log_info "Waiting for database to be ready..."
            wait_with_countdown 15 "Database initialization"
            
            # Verify database connection
            local retries=5
            while [ $retries -gt 0 ]; do
                if docker exec oddiya-postgres pg_isready -U oddiya -d oddiya &>/dev/null; then
                    log_success "Database is ready"
                    break
                fi
                retries=$((retries - 1))
                if [ $retries -gt 0 ]; then
                    log_info "Database not ready yet, retrying..."
                    sleep 3
                fi
            done
            
            if [ $retries -eq 0 ]; then
                log_error "Database failed to start properly"
                return 1
            fi
        else
            log_error "Failed to start PostgreSQL container"
            return 1
        fi
    fi
    
    # Initialize database schema if needed
    if [ -f "$PROJECT_ROOT/docs/04-database/schema.sql" ]; then
        log_info "Initializing database schema..."
        if docker exec -i oddiya-postgres psql -U oddiya -d oddiya < "$PROJECT_ROOT/docs/04-database/schema.sql" 2>/dev/null; then
            log_success "Database schema initialized"
        else
            log_warning "Schema initialization skipped (may already exist)"
        fi
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# PROJECT DEPENDENCIES SETUP
# ═══════════════════════════════════════════════════════════════════════════════

# Download and setup project dependencies
setup_project_dependencies() {
    print_section "Setting up Project Dependencies"
    
    cd "$PROJECT_ROOT"
    
    log_info "Downloading Gradle dependencies..."
    if ./gradlew dependencies --quiet; then
        log_success "Gradle dependencies downloaded"
    else
        log_error "Failed to download dependencies"
        return 1
    fi
    
    log_info "Building project..."
    if ./gradlew build -x test --quiet; then
        log_success "Project built successfully"
    else
        log_warning "Build completed with warnings (check for compilation issues)"
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# AWS RESOURCES SETUP
# ═══════════════════════════════════════════════════════════════════════════════

# Setup AWS resources
setup_aws_resources() {
    print_section "Setting up AWS Resources"
    
    # Check if AWS CLI is configured
    if ! check_aws_cli || ! check_aws_credentials "true"; then
        log_warning "AWS CLI not configured - skipping AWS setup"
        echo -e "${YELLOW}To setup AWS resources later, run: ./scripts/setup.sh aws${NC}"
        return 0
    fi
    
    log_info "Verifying required AWS resources..."
    
    # Check if ECR repository exists
    if ! resource_exists "ecr-repository" "$ECR_REPOSITORY"; then
        log_info "Creating ECR repository: $ECR_REPOSITORY"
        if aws ecr create-repository --repository-name "$ECR_REPOSITORY" --region "$REGION" &>/dev/null; then
            log_success "ECR repository created"
        else
            log_warning "ECR repository creation failed (may already exist)"
        fi
    else
        log_success "ECR repository exists: $ECR_REPOSITORY"
    fi
    
    # Check basic ECS cluster
    if ! resource_exists "ecs-cluster" "$ECS_CLUSTER"; then
        log_info "ECS cluster not found - consider running deployment scripts"
        echo -e "${YELLOW}Run deployment setup: ./scripts/deployment/deploy-infrastructure.sh${NC}"
    else
        log_success "ECS cluster exists: $ECS_CLUSTER"
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# MONITORING SETUP
# ═══════════════════════════════════════════════════════════════════════════════

# Setup monitoring and alarms
setup_monitoring() {
    print_section "Setting up Monitoring"
    
    # Check if AWS is configured
    if ! check_aws_cli || ! check_aws_credentials "true"; then
        log_warning "AWS CLI not configured - skipping monitoring setup"
        echo -e "${YELLOW}Configure AWS credentials to enable CloudWatch monitoring${NC}"
        return 0
    fi
    
    # Load environment variables for email
    if [ -f "$ENV_FILE" ]; then
        set -a
        source "$ENV_FILE"
        set +a
    fi
    
    local monitoring_email="${MONITORING_EMAIL:-$DEFAULT_MONITORING_EMAIL}"
    
    # Run CloudWatch alarms setup
    if [ -x "$SCRIPT_DIR/setup-cloudwatch-alarms.sh" ]; then
        log_info "Setting up CloudWatch alarms..."
        if "$SCRIPT_DIR/setup-cloudwatch-alarms.sh" "$monitoring_email"; then
            log_success "CloudWatch alarms configured"
        else
            log_warning "CloudWatch alarms setup failed"
        fi
    else
        log_warning "CloudWatch alarms script not found"
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# CI/CD SETUP
# ═══════════════════════════════════════════════════════════════════════════════

# Setup CI/CD configuration
setup_ci() {
    print_section "Setting up CI/CD Configuration"
    
    # Create GitHub Actions workflow directory if it doesn't exist
    local github_dir="$PROJECT_ROOT/.github/workflows"
    if [ ! -d "$github_dir" ]; then
        mkdir -p "$github_dir"
        log_info "Created GitHub Actions directory"
    fi
    
    # Check if GitHub Actions workflow exists
    if [ ! -f "$github_dir/ci.yml" ] && [ ! -f "$github_dir/main.yml" ]; then
        log_info "No GitHub Actions workflow found"
        echo -e "${YELLOW}Consider creating CI/CD workflow files in $github_dir${NC}"
    else
        log_success "GitHub Actions workflow exists"
    fi
    
    # Setup GitHub rulesets if script exists
    if [ -x "$SCRIPT_DIR/maintenance/setup-github-rulesets.sh" ]; then
        log_info "GitHub rulesets script available"
        echo -e "${YELLOW}To setup branch protection, run: ./scripts/maintenance/setup-github-rulesets.sh${NC}"
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN SETUP ORCHESTRATION
# ═══════════════════════════════════════════════════════════════════════════════

# Show usage information
show_usage() {
    echo -e "${BLUE}${BOLD}Oddiya Setup Script${NC}"
    echo -e "${CYAN}Consolidates all setup and initialization functionality${NC}"
    echo ""
    echo -e "${GREEN}Usage:${NC}"
    echo -e "  $0 [COMMAND] [OPTIONS]"
    echo ""
    echo -e "${GREEN}Commands:${NC}"
    echo -e "  ${CYAN}all${NC}              Complete setup (environment + database + dependencies)"
    echo -e "  ${CYAN}environment${NC}      Setup environment variables and local directories"
    echo -e "  ${CYAN}database${NC}         Setup PostgreSQL database"
    echo -e "  ${CYAN}dependencies${NC}     Download and build project dependencies"
    echo -e "  ${CYAN}aws${NC}              Setup AWS resources (requires AWS credentials)"
    echo -e "  ${CYAN}monitoring${NC}       Setup CloudWatch alarms and monitoring"
    echo -e "  ${CYAN}ci${NC}               Setup CI/CD configuration"
    echo ""
    echo -e "${GREEN}Options:${NC}"
    echo -e "  ${CYAN}--skip-confirmations${NC}   Skip interactive confirmations"
    echo -e "  ${CYAN}--force-reinstall${NC}      Force reinstallation of existing components"
    echo -e "  ${CYAN}--help${NC}                 Show this help message"
    echo ""
    echo -e "${GREEN}Examples:${NC}"
    echo -e "  $0 all                    # Complete setup"
    echo -e "  $0 environment            # Setup environment only"
    echo -e "  $0 database               # Setup database only"
    echo -e "  $0 aws --force-reinstall  # Force AWS setup"
    echo ""
    echo -e "${GREEN}Environment Variables:${NC}"
    echo -e "  ${CYAN}SKIP_CONFIRMATIONS${NC}=true    Skip confirmations"
    echo -e "  ${CYAN}FORCE_REINSTALL${NC}=true       Force reinstallation"
    echo -e "  ${CYAN}DEBUG${NC}=true                 Enable debug output"
}

# Run complete setup
setup_all() {
    print_header "ODDIYA PROJECT SETUP"
    print_aws_info
    
    log_info "Starting complete project setup..."
    
    # Run all setup phases
    validate_core_dependencies || return 1
    setup_environment_file || return 1
    setup_local_environment || return 1
    setup_database || return 1
    setup_project_dependencies || return 1
    setup_aws_resources || return 1
    setup_monitoring || return 1
    setup_ci || return 1
    
    print_header "SETUP COMPLETE"
    log_success "Oddiya project setup completed successfully!"
    
    echo -e "\n${GREEN}Next Steps:${NC}"
    echo -e "1. ${YELLOW}Configure API keys${NC} in $ENV_FILE"
    echo -e "2. ${YELLOW}Start development${NC}: ./gradlew bootRun"
    echo -e "3. ${YELLOW}Run tests${NC}: ./gradlew test"
    echo -e "4. ${YELLOW}Access application${NC}: http://localhost:8080"
    echo -e "5. ${YELLOW}Monitor resources${NC}: ./scripts/monitor-aws-production.sh"
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# CLEANUP AND ERROR HANDLING
# ═══════════════════════════════════════════════════════════════════════════════

# Cleanup function
cleanup_setup() {
    log_debug "Cleaning up setup resources..."
    # Add any cleanup logic here
}

# Main execution function
main() {
    local start_time=$(date +%s)
    
    # Set up cleanup on exit
    cleanup_on_exit cleanup_setup
    
    # Initialize script
    init_script "setup.sh" false
    
    # Parse command line arguments
    SETUP_MODE="all"  # Default mode
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            all|environment|database|dependencies|aws|monitoring|ci)
                SETUP_MODE="$1"
                shift
                ;;
            --skip-confirmations)
                SKIP_CONFIRMATIONS=true
                shift
                ;;
            --force-reinstall)
                FORCE_REINSTALL=true
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Execute based on setup mode
    case "$SETUP_MODE" in
        "all")
            setup_all
            ;;
        "environment")
            validate_core_dependencies || exit 1
            setup_environment_file
            setup_local_environment
            ;;
        "database")
            validate_core_dependencies || exit 1
            setup_database
            ;;
        "dependencies")
            validate_core_dependencies || exit 1
            setup_project_dependencies
            ;;
        "aws")
            setup_aws_resources
            ;;
        "monitoring")
            setup_monitoring
            ;;
        "ci")
            setup_ci
            ;;
        *)
            log_error "Invalid setup mode: $SETUP_MODE"
            show_usage
            exit 1
            ;;
    esac
    
    local exit_code=$?
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    if [ $exit_code -eq 0 ]; then
        script_complete "setup.sh ($SETUP_MODE)" "$duration"
    else
        log_error "Setup failed with exit code: $exit_code"
    fi
    
    exit $exit_code
}

# ═══════════════════════════════════════════════════════════════════════════════
# SCRIPT EXECUTION
# ═══════════════════════════════════════════════════════════════════════════════

# Only run main function if script is executed directly (not sourced)
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi