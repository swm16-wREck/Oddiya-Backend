#!/bin/bash

# AWS Cleanup Impact Analysis
# Shows what resources would be deleted and estimated savings

set -e

REGION="${AWS_REGION:-ap-northeast-2}"
PROJECT_NAME="${PROJECT_NAME:-oddiya}"

echo "========================================="
echo "AWS CLEANUP IMPACT ANALYSIS"
echo "Region: $REGION"
echo "Project: $PROJECT_NAME"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Quick analysis of resources
analyze_resources() {
    echo -e "${BLUE}Analyzing current AWS resources...${NC}"
    echo ""
    
    # EC2 Instances
    echo "📦 EC2 Instances:"
    RUNNING=$(aws ec2 describe-instances --region $REGION \
        --filters "Name=instance-state-name,Values=running" \
        --query 'length(Reservations[*].Instances[*])' --output text 2>/dev/null || echo 0)
    STOPPED=$(aws ec2 describe-instances --region $REGION \
        --filters "Name=instance-state-name,Values=stopped" \
        --query 'length(Reservations[*].Instances[*])' --output text 2>/dev/null || echo 0)
    echo "  Running: $RUNNING"
    echo "  Stopped: $STOPPED (💰 Can save ~\$30/month each)"
    echo ""
    
    # EBS Volumes
    echo "💾 EBS Volumes:"
    ATTACHED=$(aws ec2 describe-volumes --region $REGION \
        --filters "Name=status,Values=in-use" \
        --query 'length(Volumes)' --output text 2>/dev/null || echo 0)
    UNATTACHED=$(aws ec2 describe-volumes --region $REGION \
        --filters "Name=status,Values=available" \
        --query 'Volumes | sum([].Size)' --output text 2>/dev/null || echo 0)
    echo "  Attached: $ATTACHED"
    echo "  Unattached: ${UNATTACHED}GB (💰 Can save ~\$${UNATTACHED}/month)"
    echo ""
    
    # Elastic IPs
    echo "🌐 Elastic IPs:"
    ASSOCIATED=$(aws ec2 describe-addresses --region $REGION \
        --query 'length(Addresses[?AssociationId!=`null`])' --output text 2>/dev/null || echo 0)
    UNASSOCIATED=$(aws ec2 describe-addresses --region $REGION \
        --query 'length(Addresses[?AssociationId==`null`])' --output text 2>/dev/null || echo 0)
    echo "  Associated: $ASSOCIATED"
    echo "  Unassociated: $UNASSOCIATED (💰 Can save ~\$4/month each)"
    echo ""
    
    # Snapshots
    echo "📸 EBS Snapshots:"
    SNAPSHOTS=$(aws ec2 describe-snapshots --owner-ids self --region $REGION \
        --query 'length(Snapshots)' --output text 2>/dev/null || echo 0)
    OLD_SNAPSHOTS=$(aws ec2 describe-snapshots --owner-ids self --region $REGION \
        --query "length(Snapshots[?StartTime<='$(date -d '30 days ago' --iso-8601)'])" \
        --output text 2>/dev/null || echo 0)
    echo "  Total: $SNAPSHOTS"
    echo "  Older than 30 days: $OLD_SNAPSHOTS (💰 Can save ~\$1/month each)"
    echo ""
    
    # Load Balancers
    echo "⚖️ Load Balancers:"
    LBS=$(aws elbv2 describe-load-balancers --region $REGION \
        --query 'length(LoadBalancers)' --output text 2>/dev/null || echo 0)
    echo "  Total: $LBS (💰 Each costs ~\$25/month)"
    echo ""
    
    # RDS
    echo "🗄️ RDS Instances:"
    RDS=$(aws rds describe-db-instances --region $REGION \
        --query 'length(DBInstances)' --output text 2>/dev/null || echo 0)
    echo "  Total: $RDS"
    
    RDS_SNAPSHOTS=$(aws rds describe-db-snapshots --snapshot-type manual --region $REGION \
        --query 'length(DBSnapshots)' --output text 2>/dev/null || echo 0)
    echo "  Manual Snapshots: $RDS_SNAPSHOTS"
    echo ""
    
    # Lambda
    echo "⚡ Lambda Functions:"
    LAMBDAS=$(aws lambda list-functions --region $REGION \
        --query 'length(Functions)' --output text 2>/dev/null || echo 0)
    echo "  Total: $LAMBDAS"
    echo ""
    
    # S3 Buckets
    echo "🪣 S3 Buckets:"
    BUCKETS=$(aws s3api list-buckets --query 'length(Buckets)' --output text 2>/dev/null || echo 0)
    echo "  Total: $BUCKETS"
    
    # Check for empty buckets
    EMPTY_BUCKETS=0
    for bucket in $(aws s3api list-buckets --query 'Buckets[*].Name' --output text 2>/dev/null); do
        COUNT=$(aws s3api list-objects-v2 --bucket $bucket --max-items 1 \
            --query 'Contents | length(@)' --output text 2>/dev/null || echo "error")
        if [ "$COUNT" == "0" ] || [ "$COUNT" == "None" ]; then
            ((EMPTY_BUCKETS++))
        fi
    done
    echo "  Empty: $EMPTY_BUCKETS"
    echo ""
}

# Estimate cleanup savings
estimate_savings() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}ESTIMATED CLEANUP SAVINGS${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    
    MONTHLY_SAVINGS=0
    
    # Stopped EC2 instances
    if [ $STOPPED -gt 0 ]; then
        SAVINGS=$((STOPPED * 30))
        MONTHLY_SAVINGS=$((MONTHLY_SAVINGS + SAVINGS))
        echo "Stopped EC2 Instances: ~\$$SAVINGS/month"
    fi
    
    # Unattached EBS volumes
    if [ $UNATTACHED -gt 0 ]; then
        MONTHLY_SAVINGS=$((MONTHLY_SAVINGS + UNATTACHED))
        echo "Unattached EBS Volumes: ~\$$UNATTACHED/month"
    fi
    
    # Unassociated Elastic IPs
    if [ $UNASSOCIATED -gt 0 ]; then
        SAVINGS=$((UNASSOCIATED * 4))
        MONTHLY_SAVINGS=$((MONTHLY_SAVINGS + SAVINGS))
        echo "Unassociated Elastic IPs: ~\$$SAVINGS/month"
    fi
    
    # Old snapshots
    if [ $OLD_SNAPSHOTS -gt 0 ]; then
        MONTHLY_SAVINGS=$((MONTHLY_SAVINGS + OLD_SNAPSHOTS))
        echo "Old Snapshots: ~\$$OLD_SNAPSHOTS/month"
    fi
    
    # Empty S3 buckets (minimal cost but good to clean)
    if [ $EMPTY_BUCKETS -gt 0 ]; then
        echo "Empty S3 Buckets: ~\$1/month"
        MONTHLY_SAVINGS=$((MONTHLY_SAVINGS + 1))
    fi
    
    echo ""
    echo -e "${YELLOW}Total Potential Monthly Savings: ~\$$MONTHLY_SAVINGS${NC}"
    echo -e "${YELLOW}Total Potential Annual Savings: ~\$$((MONTHLY_SAVINGS * 12))${NC}"
    echo ""
}

# Resources that will be preserved
show_preserved() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}RESOURCES TO BE PRESERVED${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    echo "Resources with these characteristics will be kept:"
    echo "  ✅ Name contains: '$PROJECT_NAME'"
    echo "  ✅ Tagged with: Environment:prod, Project:$PROJECT_NAME, Protected, DoNotDelete"
    echo "  ✅ Default security groups"
    echo "  ✅ AWS service roles"
    echo "  ✅ Active/running resources"
    echo "  ✅ Resources created within threshold periods"
    echo ""
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
    
    # Get account info
    ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
    echo "AWS Account: $ACCOUNT_ID"
    echo ""
    
    # Run analysis
    analyze_resources
    estimate_savings
    show_preserved
    
    echo -e "${YELLOW}=========================================${NC}"
    echo -e "${YELLOW}NEXT STEPS${NC}"
    echo -e "${YELLOW}=========================================${NC}"
    echo ""
    echo "1. Review the analysis above"
    echo "2. Run dry-run to see what would be deleted:"
    echo "   ${GREEN}./scripts/cleanup-unused-aws-resources.sh${NC}"
    echo ""
    echo "3. If satisfied, run actual cleanup:"
    echo "   ${GREEN}DRY_RUN=false ./scripts/cleanup-unused-aws-resources.sh${NC}"
    echo ""
    echo "4. For immediate ElastiCache removal:"
    echo "   ${GREEN}DRY_RUN=false ./scripts/remove-elasticache.sh${NC}"
    echo ""
}

# Run main function
main