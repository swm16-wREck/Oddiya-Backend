#!/bin/bash

# Deployment Status Dashboard
# Provides a comprehensive view of deployment status across environments
# Usage: ./deployment-dashboard.sh [environment] [--watch] [--json]

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
ECR_REPOSITORY="${ECR_REPOSITORY:-oddiya}"
WATCH_INTERVAL=30  # seconds

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Parse arguments
ENVIRONMENT=""
WATCH_MODE=false
JSON_OUTPUT=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --watch)
      WATCH_MODE=true
      shift
      ;;
    --json)
      JSON_OUTPUT=true
      shift
      ;;
    development|dev)
      ENVIRONMENT="development"
      shift
      ;;
    production|prod)
      ENVIRONMENT="production"
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [development|production] [--watch] [--json]"
      exit 1
      ;;
  esac
done

# Function to get environment config
get_env_config() {
  local env=$1
  if [ "$env" = "production" ]; then
    echo "oddiya-prod oddiya-prod"
  else
    echo "oddiya-dev oddiya-dev"
  fi
}

# Function to get service status
get_service_status() {
  local cluster=$1
  local service=$2
  
  aws ecs describe-services \
    --cluster "$cluster" \
    --services "$service" \
    --region "$REGION" \
    --query 'services[0]' \
    --output json 2>/dev/null || echo "null"
}

# Function to get deployment metrics
get_deployment_metrics() {
  local cluster=$1
  local service=$2
  local service_info=$3
  
  if [ "$service_info" = "null" ]; then
    echo "null"
    return
  fi
  
  # Get basic metrics from service info
  local status=$(echo "$service_info" | jq -r '.status')
  local running_count=$(echo "$service_info" | jq -r '.runningCount')
  local desired_count=$(echo "$service_info" | jq -r '.desiredCount')
  local pending_count=$(echo "$service_info" | jq -r '.pendingCount')
  local task_definition=$(echo "$service_info" | jq -r '.taskDefinition')
  
  # Get deployment info
  local deployments=$(echo "$service_info" | jq '.deployments')
  local primary_deployment=$(echo "$deployments" | jq '.[] | select(.status == "PRIMARY")')
  local deployment_status=$(echo "$primary_deployment" | jq -r '.rolloutState // "UNKNOWN"')
  local deployment_created=$(echo "$primary_deployment" | jq -r '.createdAt // "unknown"')
  
  # Count deployment types
  local deployment_count=$(echo "$deployments" | jq 'length')
  
  # Get recent events
  local events=$(echo "$service_info" | jq '.events[:5]')
  
  # Get task definition details
  local task_def_info="null"
  if [ "$task_definition" != "null" ] && [ "$task_definition" != "" ]; then
    task_def_info=$(aws ecs describe-task-definition \
      --task-definition "$task_definition" \
      --region "$REGION" \
      --query 'taskDefinition.{Family:family,Revision:revision,Cpu:cpu,Memory:memory,CreatedAt:createdAt}' \
      --output json 2>/dev/null || echo "null")
  fi
  
  # Build metrics JSON
  cat << EOF
{
  "service_status": "$status",
  "task_counts": {
    "running": $running_count,
    "desired": $desired_count,
    "pending": $pending_count
  },
  "deployment": {
    "status": "$deployment_status",
    "created_at": "$deployment_created",
    "deployment_count": $deployment_count,
    "task_definition": "$task_definition"
  },
  "task_definition_info": $task_def_info,
  "recent_events": $events,
  "health": {
    "is_active": $([ "$status" = "ACTIVE" ] && echo "true" || echo "false"),
    "at_capacity": $([ "$running_count" = "$desired_count" ] && echo "true" || echo "false"),
    "stable_deployment": $([ "$deployment_count" -eq 1 ] && [ "$deployment_status" = "COMPLETED" ] && echo "true" || echo "false")
  }
}
EOF
}

# Function to get ECR info
get_ecr_info() {
  local repo=$1
  
  # Check if repository exists
  local repo_info=$(aws ecr describe-repositories \
    --repository-names "$repo" \
    --region "$REGION" \
    --query 'repositories[0]' \
    --output json 2>/dev/null || echo "null")
  
  if [ "$repo_info" = "null" ]; then
    echo "null"
    return
  fi
  
  # Get recent images
  local images=$(aws ecr describe-images \
    --repository-name "$repo" \
    --region "$REGION" \
    --query 'imageDetails | sort_by(@, &imagePushedAt) | reverse(@) | [:5]' \
    --output json 2>/dev/null || echo "[]")
  
  cat << EOF
{
  "repository": $repo_info,
  "recent_images": $images,
  "image_count": $(echo "$images" | jq 'length')
}
EOF
}

# Function to get CloudWatch alarms
get_cloudwatch_alarms() {
  local cluster=$1
  local service=$2
  
  # Get alarms related to ECS service (if any)
  local alarms=$(aws cloudwatch describe-alarms \
    --alarm-name-prefix "$service" \
    --region "$REGION" \
    --query 'MetricAlarms[?StateValue != `OK`]' \
    --output json 2>/dev/null || echo "[]")
  
  echo "$alarms"
}

# Function to display dashboard for environment
display_environment_dashboard() {
  local env=$1
  local env_config=($(get_env_config "$env"))
  local cluster=${env_config[0]}
  local service=${env_config[1]}
  
  if [ "$JSON_OUTPUT" = "false" ]; then
    echo -e "\n${BOLD}${BLUE}=================================${NC}"
    echo -e "${BOLD}${BLUE}  ENVIRONMENT: $(echo $env | tr '[:lower:]' '[:upper:]')${NC}"
    echo -e "${BOLD}${BLUE}=================================${NC}"
    echo -e "Cluster: ${CYAN}$cluster${NC}"
    echo -e "Service: ${CYAN}$service${NC}"
    echo -e "Timestamp: ${CYAN}$(date '+%Y-%m-%d %H:%M:%S')${NC}"
    echo
  fi
  
  # Get service information
  local service_info=$(get_service_status "$cluster" "$service")
  local deployment_metrics=$(get_deployment_metrics "$cluster" "$service" "$service_info")
  local ecr_info=$(get_ecr_info "$ECR_REPOSITORY")
  local cloudwatch_alarms=$(get_cloudwatch_alarms "$cluster" "$service")
  
  if [ "$JSON_OUTPUT" = "true" ]; then
    # Output JSON format
    cat << EOF
{
  "environment": "$env",
  "cluster": "$cluster",
  "service": "$service",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "metrics": $deployment_metrics,
  "ecr": $ecr_info,
  "alarms": $cloudwatch_alarms
}
EOF
    return
  fi
  
  if [ "$service_info" = "null" ]; then
    echo -e "${RED}❌ Service not found${NC}"
    return
  fi
  
  # Parse deployment metrics
  local service_status=$(echo "$deployment_metrics" | jq -r '.service_status')
  local running_count=$(echo "$deployment_metrics" | jq -r '.task_counts.running')
  local desired_count=$(echo "$deployment_metrics" | jq -r '.task_counts.desired')
  local pending_count=$(echo "$deployment_metrics" | jq -r '.task_counts.pending')
  local deployment_status=$(echo "$deployment_metrics" | jq -r '.deployment.status')
  local deployment_count=$(echo "$deployment_metrics" | jq -r '.deployment.deployment_count')
  local task_definition=$(echo "$deployment_metrics" | jq -r '.deployment.task_definition')
  local is_healthy=$(echo "$deployment_metrics" | jq -r '.health.stable_deployment')
  
  # Service Status Section
  echo -e "${BOLD}📊 Service Status${NC}"
  if [ "$service_status" = "ACTIVE" ]; then
    echo -e "   Status: ${GREEN}✅ $service_status${NC}"
  else
    echo -e "   Status: ${RED}❌ $service_status${NC}"
  fi
  
  echo -e "   Tasks: ${CYAN}$running_count${NC}/${CYAN}$desired_count${NC} running"
  if [ "$pending_count" -gt 0 ]; then
    echo -e "   Pending: ${YELLOW}$pending_count${NC}"
  fi
  
  # Deployment Status
  echo -e "\n${BOLD}🚀 Deployment Status${NC}"
  case "$deployment_status" in
    "COMPLETED")
      echo -e "   Status: ${GREEN}✅ $deployment_status${NC}"
      ;;
    "IN_PROGRESS")
      echo -e "   Status: ${YELLOW}🔄 $deployment_status${NC}"
      ;;
    "FAILED")
      echo -e "   Status: ${RED}❌ $deployment_status${NC}"
      ;;
    *)
      echo -e "   Status: ${PURPLE}❓ $deployment_status${NC}"
      ;;
  esac
  
  echo -e "   Active Deployments: ${CYAN}$deployment_count${NC}"
  
  if [ "$task_definition" != "null" ]; then
    local task_def_short=$(echo "$task_definition" | awk -F'/' '{print $NF}')
    echo -e "   Task Definition: ${CYAN}$task_def_short${NC}"
    
    # Task definition details
    local task_def_info=$(echo "$deployment_metrics" | jq -r '.task_definition_info')
    if [ "$task_def_info" != "null" ]; then
      local cpu=$(echo "$task_def_info" | jq -r '.Cpu // "unknown"')
      local memory=$(echo "$task_def_info" | jq -r '.Memory // "unknown"')
      echo -e "   Resources: ${CYAN}$cpu${NC} CPU, ${CYAN}$memory${NC} Memory"
    fi
  fi
  
  # Health Summary
  echo -e "\n${BOLD}💊 Health Summary${NC}"
  if [ "$is_healthy" = "true" ]; then
    echo -e "   Overall: ${GREEN}✅ Healthy${NC}"
  elif [ "$service_status" = "ACTIVE" ] && [ "$running_count" = "$desired_count" ]; then
    echo -e "   Overall: ${YELLOW}⚠️ Stable but Deploying${NC}"
  else
    echo -e "   Overall: ${RED}❌ Unhealthy${NC}"
  fi
  
  # Recent Events
  echo -e "\n${BOLD}📋 Recent Events${NC}"
  echo "$deployment_metrics" | jq -r '.recent_events[]? | "   \(.createdAt): \(.message)"' | head -3
  
  # ECR Information
  echo -e "\n${BOLD}📦 Container Images${NC}"
  local image_count=$(echo "$ecr_info" | jq -r '.image_count')
  if [ "$image_count" -gt 0 ]; then
    echo -e "   Images Available: ${CYAN}$image_count${NC}"
    echo -e "   Recent Images:"
    echo "$ecr_info" | jq -r '.recent_images[]? | "     \(.imageTags[0] // "untagged") - \(.imagePushedAt) (\(.imageSizeInBytes / 1024 / 1024 | floor)MB)"' | head -3
  else
    echo -e "   ${YELLOW}⚠️ No images found${NC}"
  fi
  
  # CloudWatch Alarms
  local alarm_count=$(echo "$cloudwatch_alarms" | jq 'length')
  if [ "$alarm_count" -gt 0 ]; then
    echo -e "\n${BOLD}🚨 Active Alarms${NC}"
    echo "$cloudwatch_alarms" | jq -r '.[]? | "   ❌ \(.AlarmName): \(.StateReason)"'
  fi
  
  # Actions/Recommendations
  echo -e "\n${BOLD}🔧 Quick Actions${NC}"
  if [ "$is_healthy" != "true" ]; then
    echo -e "   ${YELLOW}💡 Run deployment validation:${NC}"
    echo -e "      ./scripts/check-ecs-deployment.sh"
    echo -e "   ${YELLOW}💡 Check logs:${NC}"
    echo -e "      aws logs tail /aws/ecs/$cluster --follow"
  fi
  
  if [ "$deployment_status" = "FAILED" ]; then
    echo -e "   ${RED}🚨 Consider emergency rollback:${NC}"
    echo -e "      Use GitHub Actions 'Emergency Rollback' workflow"
  fi
  
  echo
}

# Function to display all environments
display_all_environments() {
  if [ "$JSON_OUTPUT" = "true" ]; then
    echo "{"
    echo "  \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
    echo "  \"environments\": ["
    
    display_environment_dashboard "development"
    echo ","
    display_environment_dashboard "production"
    
    echo "  ]"
    echo "}"
  else
    display_environment_dashboard "development"
    display_environment_dashboard "production"
  fi
}

# Main execution
main() {
  # Check AWS CLI
  if ! command -v aws &> /dev/null; then
    echo -e "${RED}AWS CLI is not installed. Please install it first.${NC}"
    exit 1
  fi
  
  # Check credentials
  if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}AWS credentials not configured. Please configure AWS CLI.${NC}"
    exit 1
  fi
  
  if [ "$WATCH_MODE" = "true" ]; then
    # Watch mode - continuously refresh
    if [ "$JSON_OUTPUT" = "false" ]; then
      echo -e "${BOLD}${GREEN}🔄 Starting dashboard in watch mode (Ctrl+C to exit)${NC}"
      echo -e "Refresh interval: ${CYAN}${WATCH_INTERVAL}s${NC}"
    fi
    
    while true; do
      if [ "$JSON_OUTPUT" = "false" ]; then
        clear
        echo -e "${BOLD}${CYAN}🖥️  DEPLOYMENT STATUS DASHBOARD${NC}"
        echo -e "${BOLD}${CYAN}=================================${NC}"
      fi
      
      if [ -n "$ENVIRONMENT" ]; then
        display_environment_dashboard "$ENVIRONMENT"
      else
        display_all_environments
      fi
      
      if [ "$JSON_OUTPUT" = "false" ]; then
        echo -e "\n${PURPLE}⏰ Last updated: $(date '+%H:%M:%S') | Refreshing in ${WATCH_INTERVAL}s...${NC}"
      fi
      
      sleep $WATCH_INTERVAL
    done
  else
    # Single run
    if [ "$JSON_OUTPUT" = "false" ]; then
      echo -e "${BOLD}${CYAN}🖥️  DEPLOYMENT STATUS DASHBOARD${NC}"
      echo -e "${BOLD}${CYAN}=================================${NC}"
    fi
    
    if [ -n "$ENVIRONMENT" ]; then
      display_environment_dashboard "$ENVIRONMENT"
    else
      display_all_environments
    fi
  fi
}

# Handle Ctrl+C gracefully in watch mode
trap 'echo -e "\n${YELLOW}Dashboard stopped.${NC}"; exit 0' INT

# Run main function
main