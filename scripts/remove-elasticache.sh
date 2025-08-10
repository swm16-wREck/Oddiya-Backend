#!/bin/bash

# AWS ElastiCache Removal Script
# This script identifies and removes all ElastiCache resources in the specified region

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
DRY_RUN="${DRY_RUN:-true}"

echo "========================================="
echo "AWS ElastiCache Removal Script"
echo "Region: $REGION"
echo "Dry Run: $DRY_RUN"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to delete Redis clusters
delete_redis_clusters() {
    echo -e "${YELLOW}Checking for Redis clusters...${NC}"
    
    # Get all Redis replication groups
    REDIS_GROUPS=$(aws elasticache describe-replication-groups \
        --region $REGION \
        --query 'ReplicationGroups[*].ReplicationGroupId' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$REDIS_GROUPS" ]; then
        echo "No Redis replication groups found."
    else
        for GROUP in $REDIS_GROUPS; do
            echo -e "${RED}Found Redis replication group: $GROUP${NC}"
            
            # Get cluster details
            DETAILS=$(aws elasticache describe-replication-groups \
                --replication-group-id $GROUP \
                --region $REGION \
                --query 'ReplicationGroups[0].[Status,MemberClusters[0]]' \
                --output text)
            
            echo "  Status: $(echo $DETAILS | awk '{print $1}')"
            echo "  Primary cluster: $(echo $DETAILS | awk '{print $2}')"
            
            if [ "$DRY_RUN" = "false" ]; then
                echo -e "${RED}  Deleting replication group $GROUP...${NC}"
                aws elasticache delete-replication-group \
                    --replication-group-id $GROUP \
                    --region $REGION \
                    --no-retain-primary-cluster \
                    2>/dev/null || echo "  Failed to delete $GROUP"
            else
                echo -e "${GREEN}  [DRY RUN] Would delete replication group $GROUP${NC}"
            fi
        done
    fi
    
    # Get standalone cache clusters
    CLUSTERS=$(aws elasticache describe-cache-clusters \
        --region $REGION \
        --query 'CacheClusters[?ReplicationGroupId==`null`].CacheClusterId' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$CLUSTERS" ]; then
        echo "No standalone cache clusters found."
    else
        for CLUSTER in $CLUSTERS; do
            echo -e "${RED}Found cache cluster: $CLUSTER${NC}"
            
            # Get cluster details
            DETAILS=$(aws elasticache describe-cache-clusters \
                --cache-cluster-id $CLUSTER \
                --region $REGION \
                --query 'CacheClusters[0].[CacheClusterStatus,Engine,CacheNodeType]' \
                --output text)
            
            echo "  Status: $(echo $DETAILS | awk '{print $1}')"
            echo "  Engine: $(echo $DETAILS | awk '{print $2}')"
            echo "  Node type: $(echo $DETAILS | awk '{print $3}')"
            
            if [ "$DRY_RUN" = "false" ]; then
                echo -e "${RED}  Deleting cache cluster $CLUSTER...${NC}"
                aws elasticache delete-cache-cluster \
                    --cache-cluster-id $CLUSTER \
                    --region $REGION \
                    2>/dev/null || echo "  Failed to delete $CLUSTER"
            else
                echo -e "${GREEN}  [DRY RUN] Would delete cache cluster $CLUSTER${NC}"
            fi
        done
    fi
}

# Function to delete snapshots
delete_snapshots() {
    echo -e "${YELLOW}Checking for ElastiCache snapshots...${NC}"
    
    SNAPSHOTS=$(aws elasticache describe-snapshots \
        --region $REGION \
        --query 'Snapshots[*].SnapshotName' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$SNAPSHOTS" ]; then
        echo "No snapshots found."
    else
        for SNAPSHOT in $SNAPSHOTS; do
            echo -e "${RED}Found snapshot: $SNAPSHOT${NC}"
            
            # Get snapshot details
            DETAILS=$(aws elasticache describe-snapshots \
                --snapshot-name $SNAPSHOT \
                --region $REGION \
                --query 'Snapshots[0].[SnapshotStatus,NodeSnapshots[0].CacheSize]' \
                --output text 2>/dev/null || echo "unknown unknown")
            
            echo "  Status: $(echo $DETAILS | awk '{print $1}')"
            echo "  Size: $(echo $DETAILS | awk '{print $2}') MB"
            
            if [ "$DRY_RUN" = "false" ]; then
                echo -e "${RED}  Deleting snapshot $SNAPSHOT...${NC}"
                aws elasticache delete-snapshot \
                    --snapshot-name $SNAPSHOT \
                    --region $REGION \
                    2>/dev/null || echo "  Failed to delete $SNAPSHOT"
            else
                echo -e "${GREEN}  [DRY RUN] Would delete snapshot $SNAPSHOT${NC}"
            fi
        done
    fi
}

# Function to delete subnet groups
delete_subnet_groups() {
    echo -e "${YELLOW}Checking for ElastiCache subnet groups...${NC}"
    
    SUBNET_GROUPS=$(aws elasticache describe-cache-subnet-groups \
        --region $REGION \
        --query 'CacheSubnetGroups[?CacheSubnetGroupName!=`default`].CacheSubnetGroupName' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$SUBNET_GROUPS" ]; then
        echo "No custom subnet groups found."
    else
        for SUBNET_GROUP in $SUBNET_GROUPS; do
            echo -e "${RED}Found subnet group: $SUBNET_GROUP${NC}"
            
            if [ "$DRY_RUN" = "false" ]; then
                echo -e "${RED}  Deleting subnet group $SUBNET_GROUP...${NC}"
                aws elasticache delete-cache-subnet-group \
                    --cache-subnet-group-name $SUBNET_GROUP \
                    --region $REGION \
                    2>/dev/null || echo "  Failed to delete $SUBNET_GROUP"
            else
                echo -e "${GREEN}  [DRY RUN] Would delete subnet group $SUBNET_GROUP${NC}"
            fi
        done
    fi
}

# Function to delete parameter groups
delete_parameter_groups() {
    echo -e "${YELLOW}Checking for ElastiCache parameter groups...${NC}"
    
    PARAM_GROUPS=$(aws elasticache describe-cache-parameter-groups \
        --region $REGION \
        --query 'CacheParameterGroups[?!starts_with(CacheParameterGroupName, `default`)].CacheParameterGroupName' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$PARAM_GROUPS" ]; then
        echo "No custom parameter groups found."
    else
        for PARAM_GROUP in $PARAM_GROUPS; do
            echo -e "${RED}Found parameter group: $PARAM_GROUP${NC}"
            
            if [ "$DRY_RUN" = "false" ]; then
                echo -e "${RED}  Deleting parameter group $PARAM_GROUP...${NC}"
                aws elasticache delete-cache-parameter-group \
                    --cache-parameter-group-name $PARAM_GROUP \
                    --region $REGION \
                    2>/dev/null || echo "  Failed to delete $PARAM_GROUP"
            else
                echo -e "${GREEN}  [DRY RUN] Would delete parameter group $PARAM_GROUP${NC}"
            fi
        done
    fi
}

# Function to delete security groups (VPC)
delete_security_groups() {
    echo -e "${YELLOW}Checking for ElastiCache security groups...${NC}"
    
    # Only for EC2-Classic (deprecated in most regions)
    if aws elasticache describe-cache-security-groups --region $REGION &>/dev/null; then
        SEC_GROUPS=$(aws elasticache describe-cache-security-groups \
            --region $REGION \
            --query 'CacheSecurityGroups[?CacheSecurityGroupName!=`default`].CacheSecurityGroupName' \
            --output text 2>/dev/null || echo "")
        
        if [ -z "$SEC_GROUPS" ]; then
            echo "No custom security groups found."
        else
            for SEC_GROUP in $SEC_GROUPS; do
                echo -e "${RED}Found security group: $SEC_GROUP${NC}"
                
                if [ "$DRY_RUN" = "false" ]; then
                    echo -e "${RED}  Deleting security group $SEC_GROUP...${NC}"
                    aws elasticache delete-cache-security-group \
                        --cache-security-group-name $SEC_GROUP \
                        --region $REGION \
                        2>/dev/null || echo "  Failed to delete $SEC_GROUP"
                else
                    echo -e "${GREEN}  [DRY RUN] Would delete security group $SEC_GROUP${NC}"
                fi
            done
        fi
    else
        echo "ElastiCache security groups not supported in this region (VPC-only)."
    fi
}

# Function to estimate cost savings
estimate_savings() {
    echo ""
    echo -e "${YELLOW}=========================================${NC}"
    echo -e "${YELLOW}Estimated Monthly Cost Savings${NC}"
    echo -e "${YELLOW}=========================================${NC}"
    
    # Count resources
    REDIS_COUNT=$(aws elasticache describe-replication-groups \
        --region $REGION \
        --query 'length(ReplicationGroups)' \
        --output text 2>/dev/null || echo "0")
    
    CLUSTER_COUNT=$(aws elasticache describe-cache-clusters \
        --region $REGION \
        --query 'length(CacheClusters)' \
        --output text 2>/dev/null || echo "0")
    
    # Estimate costs (rough approximation)
    # cache.t3.micro ~$0.017/hour = ~$12.24/month
    # cache.t3.small ~$0.034/hour = ~$24.48/month
    # cache.t3.medium ~$0.068/hour = ~$48.96/month
    
    ESTIMATED_SAVINGS=$((REDIS_COUNT * 25 + CLUSTER_COUNT * 25))
    
    echo "Redis Replication Groups: $REDIS_COUNT"
    echo "Cache Clusters: $CLUSTER_COUNT"
    echo -e "${GREEN}Estimated Monthly Savings: \$${ESTIMATED_SAVINGS} - \$${ESTIMATED_SAVINGS}0${NC}"
    echo ""
    echo "Note: Actual savings depend on instance types and usage."
}

# Main execution
main() {
    echo "Starting ElastiCache resource scan..."
    echo ""
    
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
    
    # Delete resources in order (dependencies matter)
    delete_redis_clusters
    echo ""
    delete_snapshots
    echo ""
    delete_subnet_groups
    echo ""
    delete_parameter_groups
    echo ""
    delete_security_groups
    echo ""
    
    # Estimate savings
    estimate_savings
    
    if [ "$DRY_RUN" = "true" ]; then
        echo -e "${YELLOW}=========================================${NC}"
        echo -e "${YELLOW}DRY RUN COMPLETE${NC}"
        echo -e "${YELLOW}To actually delete resources, run:${NC}"
        echo -e "${GREEN}DRY_RUN=false $0${NC}"
        echo -e "${YELLOW}=========================================${NC}"
    else
        echo -e "${GREEN}=========================================${NC}"
        echo -e "${GREEN}ElastiCache cleanup complete!${NC}"
        echo -e "${GREEN}Resources are being deleted.${NC}"
        echo -e "${GREEN}This may take several minutes to complete.${NC}"
        echo -e "${GREEN}=========================================${NC}"
    fi
}

# Run main function
main