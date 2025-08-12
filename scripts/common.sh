#!/bin/bash
# Common utilities and shared functions for Oddiya AWS scripts
# Usage: source "$(dirname "$0")/common.sh"

# Exit on any error
set -e

# ═══════════════════════════════════════════════════════════════════════════════
# COLOR DEFINITIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Text colors
export GREEN='\033[0;32m'
export YELLOW='\033[1;33m'
export RED='\033[0;31m'
export BLUE='\033[0;34m'
export CYAN='\033[0;36m'
export MAGENTA='\033[0;35m'
export WHITE='\033[0;37m'

# Text styles
export BOLD='\033[1m'
export DIM='\033[2m'
export UNDERLINE='\033[4m'

# Reset
export NC='\033[0m'

# ═══════════════════════════════════════════════════════════════════════════════
# AWS CONFIGURATION AND CONSTANTS
# ═══════════════════════════════════════════════════════════════════════════════

# Default AWS region
export DEFAULT_REGION="ap-northeast-2"
export REGION="${AWS_DEFAULT_REGION:-$DEFAULT_REGION}"

# Resource naming conventions and prefixes
export PROJECT_NAME="oddiya"
export PROJECT_PREFIX="oddiya"

# Common AWS resource names
export ECS_CLUSTER="${PROJECT_NAME}-cluster"
export ECS_SERVICE="${PROJECT_NAME}-backend-service"
export ECS_DEV_CLUSTER="${PROJECT_NAME}-dev-cluster"
export ECS_DEV_SERVICE="${PROJECT_NAME}-dev-service"
export TASK_DEFINITION="${PROJECT_NAME}-dev"
export ECR_REPOSITORY="${PROJECT_NAME}"
export RDS_INSTANCE="${PROJECT_NAME}-db"
export LOG_GROUP="/ecs/${PROJECT_NAME}-backend"
export DEV_LOG_GROUP="/ecs/${PROJECT_NAME}-dev"
export SNS_TOPIC="${PROJECT_NAME}-alerts"

# Cache variables for AWS account info
AWS_ACCOUNT_ID=""
AWS_REGION=""

# ═══════════════════════════════════════════════════════════════════════════════
# AWS CREDENTIAL AND CONNECTIVITY FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Check if AWS CLI is installed and configured
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI is not installed"
        echo -e "${YELLOW}Install AWS CLI: https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html${NC}"
        return 1
    fi
    return 0
}

# Check AWS credentials and connectivity
check_aws_credentials() {
    local quiet=${1:-false}
    
    if [ "$quiet" != "true" ]; then
        log_info "Verifying AWS credentials..."
    fi
    
    if ! aws sts get-caller-identity --region "$REGION" &>/dev/null; then
        log_error "Unable to access AWS. Please configure AWS credentials."
        echo -e "${YELLOW}Run: ${CYAN}aws configure${NC}"
        echo -e "${YELLOW}Or set environment variables:${NC}"
        echo -e "${CYAN}  export AWS_ACCESS_KEY_ID=your_key${NC}"
        echo -e "${CYAN}  export AWS_SECRET_ACCESS_KEY=your_secret${NC}"
        echo -e "${CYAN}  export AWS_DEFAULT_REGION=$REGION${NC}"
        return 1
    fi
    
    if [ "$quiet" != "true" ]; then
        local account_id=$(get_aws_account_id)
        log_success "Connected to AWS Account: $account_id"
    fi
    return 0
}

# Get AWS account ID with caching
get_aws_account_id() {
    if [ -z "$AWS_ACCOUNT_ID" ]; then
        AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || echo "UNKNOWN")
    fi
    echo "$AWS_ACCOUNT_ID"
}

# Get AWS region with fallback
get_aws_region() {
    if [ -z "$AWS_REGION" ]; then
        AWS_REGION=$(aws configure get region 2>/dev/null || echo "$REGION")
    fi
    echo "$AWS_REGION"
}

# ═══════════════════════════════════════════════════════════════════════════════
# LOGGING AND OUTPUT FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Print colored log messages
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}" >&2
}

log_debug() {
    if [ "${DEBUG:-false}" = "true" ]; then
        echo -e "${DIM}🐛 $1${NC}" >&2
    fi
}

# Print section headers
print_header() {
    local title="$1"
    local width=68
    
    echo -e "\n${BLUE}${BOLD}$(printf '═%.0s' $(seq 1 $width))${NC}"
    printf "${BLUE}${BOLD}%*s${NC}\n" $(((${#title}+$width)/2)) "$title"
    echo -e "${BLUE}${BOLD}$(printf '═%.0s' $(seq 1 $width))${NC}"
}

# Print sub-section headers
print_section() {
    local title="$1"
    echo -e "\n${GREEN}${BOLD}$title${NC}"
    echo -e "${BLUE}$(printf '─%.0s' $(seq 1 40))${NC}"
}

# Print account and region info
print_aws_info() {
    local account_id=$(get_aws_account_id)
    local region=$(get_aws_region)
    echo -e "${CYAN}Account: $account_id | Region: $region | $(date +'%Y-%m-%d %H:%M:%S')${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
# ERROR HANDLING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Generic error handler
handle_error() {
    local exit_code=$?
    local line_number=$1
    local command="$2"
    
    if [ $exit_code -ne 0 ]; then
        log_error "Command failed at line $line_number: $command"
        log_error "Exit code: $exit_code"
        exit $exit_code
    fi
}

# Set up error trapping
setup_error_handling() {
    trap 'handle_error $LINENO "$BASH_COMMAND"' ERR
}

# Cleanup function for script exit
cleanup_on_exit() {
    local cleanup_function="$1"
    trap "$cleanup_function" EXIT
}

# Safe command execution with retry
safe_aws_command() {
    local command="$1"
    local max_attempts="${2:-3}"
    local delay="${3:-2}"
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if eval "$command"; then
            return 0
        fi
        
        if [ $attempt -lt $max_attempts ]; then
            log_warning "Command failed (attempt $attempt/$max_attempts), retrying in ${delay}s..."
            sleep $delay
            delay=$((delay * 2))  # Exponential backoff
        fi
        attempt=$((attempt + 1))
    done
    
    log_error "Command failed after $max_attempts attempts: $command"
    return 1
}

# ═══════════════════════════════════════════════════════════════════════════════
# PROGRESS INDICATORS AND UTILITIES
# ═══════════════════════════════════════════════════════════════════════════════

# Show progress spinner
show_spinner() {
    local pid=$1
    local message="$2"
    local spinner='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0
    
    while kill -0 $pid 2>/dev/null; do
        printf "\r${BLUE}%s %s${NC}" "${spinner:$i:1}" "$message"
        i=$(((i + 1) % ${#spinner}))
        sleep 0.1
    done
    printf "\r"
}

# Progress bar
show_progress() {
    local current=$1
    local total=$2
    local width=${3:-50}
    local percent=$((current * 100 / total))
    local filled=$((current * width / total))
    local empty=$((width - filled))
    
    printf "\r${BLUE}["
    printf "%${filled}s" | tr ' ' '█'
    printf "%${empty}s" | tr ' ' '░'
    printf "] %d%% (%d/%d)${NC}" $percent $current $total
}

# Wait with countdown
wait_with_countdown() {
    local seconds=$1
    local message="${2:-Waiting}"
    
    for ((i=seconds; i>0; i--)); do
        printf "\r${YELLOW}%s %d seconds...${NC}" "$message" $i
        sleep 1
    done
    printf "\r%*s\r" $((${#message} + 20)) ""  # Clear line
}

# ═══════════════════════════════════════════════════════════════════════════════
# AWS RESOURCE UTILITY FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Check if AWS resource exists
resource_exists() {
    local resource_type="$1"
    local resource_name="$2"
    local region="${3:-$REGION}"
    
    case "$resource_type" in
        "ecs-cluster")
            aws ecs describe-clusters --clusters "$resource_name" --region "$region" \
                --query 'clusters[0].status' --output text 2>/dev/null | grep -q "ACTIVE"
            ;;
        "ecs-service")
            local cluster="$3"
            aws ecs describe-services --cluster "$cluster" --services "$resource_name" --region "$region" \
                --query 'services[0].status' --output text 2>/dev/null | grep -q "ACTIVE"
            ;;
        "rds-instance")
            aws rds describe-db-instances --db-instance-identifier "$resource_name" --region "$region" \
                --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null | grep -q "available"
            ;;
        "s3-bucket")
            aws s3api head-bucket --bucket "$resource_name" 2>/dev/null
            ;;
        "ecr-repository")
            aws ecr describe-repositories --repository-names "$resource_name" --region "$region" \
                --query 'repositories[0].repositoryName' --output text 2>/dev/null | grep -q "$resource_name"
            ;;
        *)
            log_error "Unknown resource type: $resource_type"
            return 1
            ;;
    esac
}

# Get resource status with proper formatting
get_resource_status() {
    local resource_type="$1"
    local resource_name="$2"
    local region="${3:-$REGION}"
    
    local status="UNKNOWN"
    local color="$YELLOW"
    
    case "$resource_type" in
        "ecs-service")
            local cluster="$3"
            local service_info=$(aws ecs describe-services \
                --cluster "$cluster" --services "$resource_name" --region "$region" \
                --query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount}' \
                --output json 2>/dev/null || echo "{}")
            
            local service_status=$(echo "$service_info" | jq -r '.Status // "NOT_FOUND"')
            local running=$(echo "$service_info" | jq -r '.Running // 0')
            local desired=$(echo "$service_info" | jq -r '.Desired // 0')
            
            if [ "$service_status" = "ACTIVE" ] && [ "$running" -eq "$desired" ] && [ "$running" -gt 0 ]; then
                status="HEALTHY ($running/$desired)"
                color="$GREEN"
            elif [ "$service_status" = "ACTIVE" ] && [ "$running" -gt 0 ]; then
                status="DEGRADED ($running/$desired)"
                color="$YELLOW"
            else
                status="DOWN ($running/$desired)"
                color="$RED"
            fi
            ;;
        "rds-instance")
            status=$(aws rds describe-db-instances --db-instance-identifier "$resource_name" --region "$region" \
                --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null || echo "NOT_FOUND")
            
            case "$status" in
                "available") color="$GREEN" ;;
                "NOT_FOUND"|"deleting") color="$RED" ;;
                *) color="$YELLOW" ;;
            esac
            ;;
        *)
            status="UNKNOWN"
            color="$YELLOW"
            ;;
    esac
    
    echo -e "${color}${status}${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
# VALIDATION AND CONFIRMATION FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Confirm action with user
confirm_action() {
    local message="$1"
    local default="${2:-n}"
    local response
    
    if [ "$default" = "y" ]; then
        echo -en "${YELLOW}$message [Y/n]: ${NC}"
    else
        echo -en "${YELLOW}$message [y/N]: ${NC}"
    fi
    
    read -n 1 response
    echo ""
    
    case "$response" in
        [Yy]) return 0 ;;
        [Nn]) return 1 ;;
        "") [ "$default" = "y" ] && return 0 || return 1 ;;
        *) return 1 ;;
    esac
}

# Validate required tools
validate_dependencies() {
    local dependencies=("$@")
    local missing=()
    
    for dep in "${dependencies[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            missing+=("$dep")
        fi
    done
    
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing required dependencies:"
        for dep in "${missing[@]}"; do
            echo -e "  ${RED}• $dep${NC}"
        done
        return 1
    fi
    
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# UTILITY FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Generate timestamp
timestamp() {
    date +'%Y-%m-%d %H:%M:%S'
}

# Generate short timestamp for filenames
timestamp_short() {
    date +'%Y%m%d-%H%M%S'
}

# Format bytes to human readable
format_bytes() {
    local bytes=$1
    if [ "$bytes" -eq 0 ]; then
        echo "0 B"
    else
        numfmt --to=iec-i --suffix=B "$bytes" 2>/dev/null || echo "$bytes bytes"
    fi
}

# Format duration in seconds to human readable
format_duration() {
    local seconds=$1
    local hours=$((seconds / 3600))
    local minutes=$(((seconds % 3600) / 60))
    local secs=$((seconds % 60))
    
    if [ $hours -gt 0 ]; then
        printf "%dh %dm %ds" $hours $minutes $secs
    elif [ $minutes -gt 0 ]; then
        printf "%dm %ds" $minutes $secs
    else
        printf "%ds" $secs
    fi
}

# Create backup directory with timestamp
create_backup_dir() {
    local base_name="${1:-backup}"
    local backup_dir="./${base_name}-$(timestamp_short)"
    mkdir -p "$backup_dir"
    echo "$backup_dir"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SCRIPT INITIALIZATION
# ═══════════════════════════════════════════════════════════════════════════════

# Initialize common script environment
init_script() {
    local script_name="$1"
    local require_aws="${2:-true}"
    
    # Enable debug mode if requested
    if [ "${DEBUG:-false}" = "true" ]; then
        set -x
    fi
    
    # Validate AWS CLI if required
    if [ "$require_aws" = "true" ]; then
        check_aws_cli || exit 1
        check_aws_credentials || exit 1
    fi
    
    # Set up error handling
    setup_error_handling
    
    log_debug "Initialized script: $script_name"
}

# Show script completion message
script_complete() {
    local script_name="$1"
    local duration="$2"
    
    print_header "SCRIPT COMPLETE"
    log_success "$script_name completed successfully!"
    
    if [ -n "$duration" ]; then
        echo -e "${CYAN}Duration: $(format_duration $duration)${NC}"
    fi
    
    echo -e "${CYAN}Completed at: $(timestamp)${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SCRIPT METADATA
# ═══════════════════════════════════════════════════════════════════════════════

# Version information
COMMON_SCRIPT_VERSION="1.0.0"
COMMON_SCRIPT_UPDATED="2024-12-12"

# Show version info if called directly
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    echo -e "${BLUE}${BOLD}Oddiya AWS Scripts Common Library${NC}"
    echo -e "${CYAN}Version: $COMMON_SCRIPT_VERSION${NC}"
    echo -e "${CYAN}Updated: $COMMON_SCRIPT_UPDATED${NC}"
    echo ""
    echo -e "${YELLOW}This file should be sourced by other scripts:${NC}"
    echo -e "${CYAN}  source \"\$(dirname \"\$0\")/common.sh\"${NC}"
    echo ""
    echo -e "${GREEN}Available functions:${NC}"
    echo "  • AWS: check_aws_cli, check_aws_credentials, get_aws_account_id"
    echo "  • Logging: log_info, log_success, log_warning, log_error"
    echo "  • Display: print_header, print_section, show_progress"
    echo "  • Resources: resource_exists, get_resource_status"
    echo "  • Utilities: confirm_action, validate_dependencies, format_bytes"
fi