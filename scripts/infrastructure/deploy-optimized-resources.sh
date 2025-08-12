#!/bin/bash

# Deploy Optimized ECS Resources
# This script applies the resource optimizations for better Spring Boot performance

set -e

echo "🚀 Deploying Optimized ECS Resources..."

# Check if we're in the right directory
if [ ! -f "main.tf" ]; then
    echo "❌ Error: main.tf not found. Please run from terraform/ecs-infrastructure directory"
    exit 1
fi

# Initialize Terraform if needed
if [ ! -d ".terraform" ]; then
    echo "📦 Initializing Terraform..."
    terraform init
fi

# Validate configuration
echo "🔍 Validating Terraform configuration..."
terraform validate

# Plan the changes
echo "📋 Planning changes..."
terraform plan -out=optimize.tfplan \
    -var="task_cpu=1024" \
    -var="task_memory=2048" \
    -var="health_check_timeout=15" \
    -var="health_check_interval=60" \
    -var="health_check_unhealthy_threshold=5" \
    -var="container_health_check_start_period=180" \
    -var="container_health_check_retries=5"

# Ask for confirmation
read -p "🤔 Do you want to apply these changes? (y/N): " confirm
if [[ $confirm == [yY] ]]; then
    echo "⚡ Applying optimizations..."
    terraform apply optimize.tfplan
    rm optimize.tfplan
    
    echo "✅ Optimization complete!"
    echo "📊 New configuration:"
    echo "   - CPU: 1024 units (1 vCPU)"
    echo "   - Memory: 2048 MB (2 GB)"
    echo "   - Health check timeout: 15s"
    echo "   - Health check interval: 60s"
    echo "   - Start period: 180s (3 minutes)"
    echo "   - Retries: 5"
    echo ""
    echo "🎯 Expected improvements:"
    echo "   - Faster application startup"
    echo "   - Better memory management for JPA/Hibernate"
    echo "   - Reduced health check failures during startup"
    echo "   - More stable deployments"
else
    echo "❌ Deployment cancelled"
    rm optimize.tfplan
    exit 1
fi

echo "🏁 Done! Monitor CloudWatch logs for startup time improvements."