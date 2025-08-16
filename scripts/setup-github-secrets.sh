#!/bin/bash

# GitHub Secrets Setup Script for Oddiya Project
# This script helps you quickly set up all required GitHub secrets

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}GitHub Secrets Setup for Oddiya${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed.${NC}"
    echo "Please install it first:"
    echo "  macOS: brew install gh"
    echo "  Linux: https://github.com/cli/cli/blob/trunk/docs/install_linux.md"
    exit 1
fi

# Check if logged in to GitHub
if ! gh auth status &> /dev/null; then
    echo -e "${YELLOW}Not logged in to GitHub. Starting authentication...${NC}"
    gh auth login
fi

echo -e "${GREEN}✓ GitHub CLI is ready${NC}"
echo ""

# Function to set a secret
set_secret() {
    local secret_name=$1
    local prompt_text=$2
    local is_password=$3
    local default_value=$4
    
    echo -e "${YELLOW}Setting up: ${secret_name}${NC}"
    echo "$prompt_text"
    
    if [ "$is_password" == "true" ]; then
        read -s -p "Enter value (hidden): " secret_value
        echo ""
    else
        if [ -n "$default_value" ]; then
            read -p "Enter value [$default_value]: " secret_value
            secret_value=${secret_value:-$default_value}
        else
            read -p "Enter value: " secret_value
        fi
    fi
    
    if [ -n "$secret_value" ]; then
        echo "$secret_value" | gh secret set "$secret_name"
        echo -e "${GREEN}✓ $secret_name set successfully${NC}"
    else
        echo -e "${RED}✗ Skipped $secret_name (no value provided)${NC}"
    fi
    echo ""
}

# Function to set environment-specific secret
set_env_secret() {
    local secret_name=$1
    local environment=$2
    local prompt_text=$3
    local is_password=$4
    
    echo -e "${YELLOW}Setting up: ${secret_name} for environment: ${environment}${NC}"
    echo "$prompt_text"
    
    if [ "$is_password" == "true" ]; then
        read -s -p "Enter value (hidden): " secret_value
        echo ""
    else
        read -p "Enter value: " secret_value
    fi
    
    if [ -n "$secret_value" ]; then
        echo "$secret_value" | gh secret set "$secret_name" --env "$environment"
        echo -e "${GREEN}✓ $secret_name set successfully for $environment${NC}"
    else
        echo -e "${RED}✗ Skipped $secret_name (no value provided)${NC}"
    fi
    echo ""
}

# Main setup
echo -e "${GREEN}Step 1: Database Secrets${NC}"
echo "----------------------------------------"
set_secret "TEST_DB_PASSWORD" "Password for test database (used in CI/CD)" true "oddiya123"

echo -e "${GREEN}Step 2: AWS Credentials${NC}"
echo "----------------------------------------"
echo -e "${YELLOW}Note: Use IAM user with minimal required permissions${NC}"
set_secret "AWS_ACCESS_KEY_ID" "AWS Access Key ID (starts with AKIA...)" false
set_secret "AWS_SECRET_ACCESS_KEY" "AWS Secret Access Key" true
set_secret "AWS_ACCOUNT_ID" "AWS Account ID (12 digits)" false
set_secret "AWS_REGION" "AWS Region" false "ap-northeast-2"

echo -e "${GREEN}Step 3: Docker Registry (Optional)${NC}"
echo "----------------------------------------"
echo "Skip if using AWS ECR instead of Docker Hub"
read -p "Setup Docker Hub credentials? (y/n): " setup_docker
if [ "$setup_docker" == "y" ]; then
    set_secret "DOCKER_USERNAME" "Docker Hub username" false
    set_secret "DOCKER_PASSWORD" "Docker Hub password or access token" true
fi

echo -e "${GREEN}Step 4: Application Secrets${NC}"
echo "----------------------------------------"
set_secret "JWT_SECRET" "JWT signing secret (min 256-bit, e.g., 32+ characters)" true
set_secret "ENCRYPTION_KEY" "Data encryption key (32 characters recommended)" true

echo -e "${GREEN}Step 5: Monitoring (Optional)${NC}"
echo "----------------------------------------"
read -p "Setup monitoring secrets? (y/n): " setup_monitoring
if [ "$setup_monitoring" == "y" ]; then
    set_secret "SONAR_TOKEN" "SonarQube token (if using SonarCloud)" false
    set_secret "CODECOV_TOKEN" "Codecov token (if using Codecov)" false
    set_secret "SLACK_WEBHOOK_URL" "Slack webhook URL for notifications" false
fi

echo -e "${GREEN}Step 6: Production Environment (Optional)${NC}"
echo "----------------------------------------"
read -p "Setup production environment secrets? (y/n): " setup_prod
if [ "$setup_prod" == "y" ]; then
    # First, create the environment if it doesn't exist
    gh api repos/:owner/:repo/environments/production -X PUT 2>/dev/null || true
    
    set_env_secret "PROD_DB_PASSWORD" "production" "Production database password" true
    set_env_secret "AWS_ACCESS_KEY_ID" "production" "Production AWS Access Key ID" false
    set_env_secret "AWS_SECRET_ACCESS_KEY" "production" "Production AWS Secret Key" true
    set_env_secret "DATABASE_HOST" "production" "Production database host (RDS endpoint)" false
fi

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}Setup Complete!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""

# List all secrets
echo "Current secrets in repository:"
gh secret list

echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Test your workflows in a feature branch"
echo "2. Set up environment protection rules in GitHub Settings"
echo "3. Document any additional secrets in docs/GITHUB_SECRETS_SETUP.md"
echo "4. Consider setting up secret rotation reminders"
echo ""
echo -e "${GREEN}Security Reminders:${NC}"
echo "• Never commit secrets to the repository"
echo "• Rotate secrets regularly (every 60-90 days)"
echo "• Use different secrets for different environments"
echo "• Enable GitHub secret scanning in repository settings"