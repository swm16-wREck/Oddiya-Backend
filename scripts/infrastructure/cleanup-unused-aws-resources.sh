#!/bin/bash

# AWS Comprehensive Resource Cleanup Script
# Removes all unused AWS resources to minimize costs
# SAFETY: Runs in dry-run mode by default

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
DRY_RUN="${DRY_RUN:-true}"
FORCE="${FORCE:-false}"

# Project tags to preserve (modify based on your project)
PROJECT_NAME="${PROJECT_NAME:-oddiya}"
PRESERVE_TAGS="${PRESERVE_TAGS:-Environment:prod,Project:$PROJECT_NAME,Protected:true}"

echo "========================================="
echo "AWS COMPREHENSIVE RESOURCE CLEANUP"
echo "Region: $REGION"
echo "Dry Run: $DRY_RUN"
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

# Counters
TOTAL_RESOURCES_FOUND=0
TOTAL_RESOURCES_DELETED=0
ESTIMATED_MONTHLY_SAVINGS=0

# Log file
LOG_FILE="cleanup-log-$(date +%Y%m%d-%H%M%S).txt"

# Function to log actions
log_action() {
    echo "$1" | tee -a "$LOG_FILE"
}

# Function to check if resource should be preserved
should_preserve() {
    local resource_tags="$1"
    local resource_name="$2"
    
    # Check if resource name contains project name
    if [[ "$resource_name" == *"$PROJECT_NAME"* ]]; then
        return 0  # Preserve
    fi
    
    # Check for preserve tags
    for tag in ${PRESERVE_TAGS//,/ }; do
        if [[ "$resource_tags" == *"$tag"* ]]; then
            return 0  # Preserve
        fi
    done
    
    return 1  # Don't preserve
}

# 1. Clean up EC2 Instances
cleanup_ec2_instances() {
    log_action ""
    log_action "${BLUE}=== Checking EC2 Instances ===${NC}"
    
    # Get all instances
    INSTANCES=$(aws ec2 describe-instances \
        --region $REGION \
        --query 'Reservations[*].Instances[?State.Name!=`terminated`].[InstanceId,State.Name,Tags]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$INSTANCES" != "[]" ]; then
        echo "$INSTANCES" | jq -r '.[][] | @json' | while read -r instance; do
            INSTANCE_ID=$(echo "$instance" | jq -r '.[0]')
            STATE=$(echo "$instance" | jq -r '.[1]')
            TAGS=$(echo "$instance" | jq -r '.[2] | tostring')
            
            # Get instance name
            NAME=$(echo "$instance" | jq -r '.[2][] | select(.Key=="Name") | .Value' 2>/dev/null || echo "unnamed")
            
            # Check if should preserve
            if should_preserve "$TAGS" "$NAME"; then
                log_action "  ${GREEN}[KEEP]${NC} Instance $INSTANCE_ID ($NAME) - Project resource"
                continue
            fi
            
            # Check if instance is stopped for more than 7 days
            if [ "$STATE" == "stopped" ]; then
                LAUNCH_TIME=$(aws ec2 describe-instances \
                    --instance-ids $INSTANCE_ID \
                    --region $REGION \
                    --query 'Reservations[0].Instances[0].LaunchTime' \
                    --output text 2>/dev/null || echo "")
                
                if [ ! -z "$LAUNCH_TIME" ]; then
                    DAYS_OLD=$(( ($(date +%s) - $(date -d "$LAUNCH_TIME" +%s)) / 86400 ))
                    if [ $DAYS_OLD -gt 7 ]; then
                        log_action "  ${RED}[DELETE]${NC} Instance $INSTANCE_ID ($NAME) - Stopped for $DAYS_OLD days"
                        ((TOTAL_RESOURCES_FOUND++))
                        ((ESTIMATED_MONTHLY_SAVINGS+=30))
                        
                        if [ "$DRY_RUN" == "false" ]; then
                            aws ec2 terminate-instances --instance-ids $INSTANCE_ID --region $REGION 2>/dev/null || true
                            ((TOTAL_RESOURCES_DELETED++))
                        fi
                    fi
                fi
            elif [ "$STATE" == "running" ] && [ "$NAME" == "unnamed" ]; then
                log_action "  ${YELLOW}[WARNING]${NC} Instance $INSTANCE_ID running but unnamed - Review manually"
            fi
        done
    else
        log_action "  No EC2 instances found"
    fi
}

# 2. Clean up EBS Volumes
cleanup_ebs_volumes() {
    log_action ""
    log_action "${BLUE}=== Checking EBS Volumes ===${NC}"
    
    VOLUMES=$(aws ec2 describe-volumes \
        --region $REGION \
        --filters "Name=status,Values=available" \
        --query 'Volumes[*].[VolumeId,Size,CreateTime,Tags]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$VOLUMES" != "[]" ]; then
        echo "$VOLUMES" | jq -r '.[] | @json' | while read -r volume; do
            VOLUME_ID=$(echo "$volume" | jq -r '.[0]')
            SIZE=$(echo "$volume" | jq -r '.[1]')
            CREATE_TIME=$(echo "$volume" | jq -r '.[2]')
            TAGS=$(echo "$volume" | jq -r '.[3] | tostring')
            
            # Calculate age
            DAYS_OLD=$(( ($(date +%s) - $(date -d "$CREATE_TIME" +%s)) / 86400 ))
            
            if [ $DAYS_OLD -gt 7 ]; then
                log_action "  ${RED}[DELETE]${NC} Volume $VOLUME_ID (${SIZE}GB) - Unattached for $DAYS_OLD days"
                ((TOTAL_RESOURCES_FOUND++))
                ((ESTIMATED_MONTHLY_SAVINGS+=SIZE))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws ec2 delete-volume --volume-id $VOLUME_ID --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No unattached EBS volumes found"
    fi
}

# 3. Clean up Elastic IPs
cleanup_elastic_ips() {
    log_action ""
    log_action "${BLUE}=== Checking Elastic IPs ===${NC}"
    
    EIPS=$(aws ec2 describe-addresses \
        --region $REGION \
        --query 'Addresses[?AssociationId==`null`].[AllocationId,PublicIp]' \
        --output text 2>/dev/null || echo "")
    
    if [ ! -z "$EIPS" ]; then
        echo "$EIPS" | while read -r allocation_id public_ip; do
            log_action "  ${RED}[DELETE]${NC} Elastic IP $public_ip - Not associated"
            ((TOTAL_RESOURCES_FOUND++))
            ((ESTIMATED_MONTHLY_SAVINGS+=4))
            
            if [ "$DRY_RUN" == "false" ]; then
                aws ec2 release-address --allocation-id $allocation_id --region $REGION 2>/dev/null || true
                ((TOTAL_RESOURCES_DELETED++))
            fi
        done
    else
        log_action "  No unassociated Elastic IPs found"
    fi
}

# 4. Clean up EBS Snapshots
cleanup_ebs_snapshots() {
    log_action ""
    log_action "${BLUE}=== Checking EBS Snapshots ===${NC}"
    
    SNAPSHOTS=$(aws ec2 describe-snapshots \
        --owner-ids self \
        --region $REGION \
        --query 'Snapshots[*].[SnapshotId,StartTime,VolumeSize,Description]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$SNAPSHOTS" != "[]" ]; then
        echo "$SNAPSHOTS" | jq -r '.[] | @json' | while read -r snapshot; do
            SNAPSHOT_ID=$(echo "$snapshot" | jq -r '.[0]')
            START_TIME=$(echo "$snapshot" | jq -r '.[1]')
            SIZE=$(echo "$snapshot" | jq -r '.[2]')
            DESCRIPTION=$(echo "$snapshot" | jq -r '.[3]')
            
            # Skip AMI snapshots
            if [[ "$DESCRIPTION" == *"Created by CreateImage"* ]]; then
                continue
            fi
            
            # Calculate age
            DAYS_OLD=$(( ($(date +%s) - $(date -d "$START_TIME" +%s)) / 86400 ))
            
            if [ $DAYS_OLD -gt 30 ]; then
                log_action "  ${RED}[DELETE]${NC} Snapshot $SNAPSHOT_ID (${SIZE}GB) - $DAYS_OLD days old"
                ((TOTAL_RESOURCES_FOUND++))
                ((ESTIMATED_MONTHLY_SAVINGS+=1))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws ec2 delete-snapshot --snapshot-id $SNAPSHOT_ID --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No snapshots found"
    fi
}

# 5. Clean up AMIs
cleanup_amis() {
    log_action ""
    log_action "${BLUE}=== Checking AMI Images ===${NC}"
    
    AMIS=$(aws ec2 describe-images \
        --owners self \
        --region $REGION \
        --query 'Images[*].[ImageId,Name,CreationDate]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$AMIS" != "[]" ]; then
        echo "$AMIS" | jq -r '.[] | @json' | while read -r ami; do
            AMI_ID=$(echo "$ami" | jq -r '.[0]')
            AMI_NAME=$(echo "$ami" | jq -r '.[1]')
            CREATION_DATE=$(echo "$ami" | jq -r '.[2]')
            
            # Skip if project related
            if should_preserve "" "$AMI_NAME"; then
                continue
            fi
            
            # Calculate age
            DAYS_OLD=$(( ($(date +%s) - $(date -d "$CREATION_DATE" +%s)) / 86400 ))
            
            if [ $DAYS_OLD -gt 30 ]; then
                log_action "  ${RED}[DELETE]${NC} AMI $AMI_ID ($AMI_NAME) - $DAYS_OLD days old"
                ((TOTAL_RESOURCES_FOUND++))
                
                if [ "$DRY_RUN" == "false" ]; then
                    # Deregister AMI
                    aws ec2 deregister-image --image-id $AMI_ID --region $REGION 2>/dev/null || true
                    
                    # Delete associated snapshots
                    SNAP_IDS=$(aws ec2 describe-images --image-ids $AMI_ID --region $REGION \
                        --query 'Images[0].BlockDeviceMappings[*].Ebs.SnapshotId' --output text 2>/dev/null || echo "")
                    
                    for SNAP_ID in $SNAP_IDS; do
                        aws ec2 delete-snapshot --snapshot-id $SNAP_ID --region $REGION 2>/dev/null || true
                    done
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No AMIs found"
    fi
}

# 6. Clean up Load Balancers
cleanup_load_balancers() {
    log_action ""
    log_action "${BLUE}=== Checking Load Balancers ===${NC}"
    
    # ALB/NLB
    ALBS=$(aws elbv2 describe-load-balancers \
        --region $REGION \
        --query 'LoadBalancers[*].[LoadBalancerArn,LoadBalancerName,State.Code,CreatedTime]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$ALBS" != "[]" ]; then
        echo "$ALBS" | jq -r '.[] | @json' | while read -r alb; do
            ALB_ARN=$(echo "$alb" | jq -r '.[0]')
            ALB_NAME=$(echo "$alb" | jq -r '.[1]')
            STATE=$(echo "$alb" | jq -r '.[2]')
            
            # Skip if project related
            if should_preserve "" "$ALB_NAME"; then
                continue
            fi
            
            # Check if has targets
            TARGET_GROUPS=$(aws elbv2 describe-target-groups \
                --load-balancer-arn $ALB_ARN \
                --region $REGION \
                --query 'TargetGroups[*].TargetGroupArn' \
                --output text 2>/dev/null || echo "")
            
            HAS_TARGETS=false
            for TG_ARN in $TARGET_GROUPS; do
                TARGETS=$(aws elbv2 describe-target-health \
                    --target-group-arn $TG_ARN \
                    --region $REGION \
                    --query 'TargetHealthDescriptions' \
                    --output json 2>/dev/null || echo "[]")
                
                if [ "$TARGETS" != "[]" ]; then
                    HAS_TARGETS=true
                    break
                fi
            done
            
            if [ "$HAS_TARGETS" == "false" ]; then
                log_action "  ${RED}[DELETE]${NC} Load Balancer $ALB_NAME - No targets"
                ((TOTAL_RESOURCES_FOUND++))
                ((ESTIMATED_MONTHLY_SAVINGS+=25))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No load balancers found"
    fi
}

# 7. Clean up RDS Snapshots
cleanup_rds_snapshots() {
    log_action ""
    log_action "${BLUE}=== Checking RDS Snapshots ===${NC}"
    
    SNAPSHOTS=$(aws rds describe-db-snapshots \
        --snapshot-type manual \
        --region $REGION \
        --query 'DBSnapshots[*].[DBSnapshotIdentifier,SnapshotCreateTime,AllocatedStorage]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$SNAPSHOTS" != "[]" ]; then
        echo "$SNAPSHOTS" | jq -r '.[] | @json' | while read -r snapshot; do
            SNAPSHOT_ID=$(echo "$snapshot" | jq -r '.[0]')
            CREATE_TIME=$(echo "$snapshot" | jq -r '.[1]')
            SIZE=$(echo "$snapshot" | jq -r '.[2]')
            
            # Calculate age
            DAYS_OLD=$(( ($(date +%s) - $(date -d "$CREATE_TIME" +%s)) / 86400 ))
            
            if [ $DAYS_OLD -gt 30 ]; then
                log_action "  ${RED}[DELETE]${NC} RDS Snapshot $SNAPSHOT_ID (${SIZE}GB) - $DAYS_OLD days old"
                ((TOTAL_RESOURCES_FOUND++))
                ((ESTIMATED_MONTHLY_SAVINGS+=2))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws rds delete-db-snapshot --db-snapshot-identifier $SNAPSHOT_ID --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No RDS snapshots found"
    fi
}

# 8. Clean up Lambda Functions
cleanup_lambda_functions() {
    log_action ""
    log_action "${BLUE}=== Checking Lambda Functions ===${NC}"
    
    FUNCTIONS=$(aws lambda list-functions \
        --region $REGION \
        --query 'Functions[*].[FunctionName,LastModified]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$FUNCTIONS" != "[]" ]; then
        echo "$FUNCTIONS" | jq -r '.[] | @json' | while read -r function; do
            FUNCTION_NAME=$(echo "$function" | jq -r '.[0]')
            LAST_MODIFIED=$(echo "$function" | jq -r '.[1]')
            
            # Skip if project related
            if should_preserve "" "$FUNCTION_NAME"; then
                continue
            fi
            
            # Check if function has been invoked recently
            INVOCATIONS=$(aws cloudwatch get-metric-statistics \
                --namespace AWS/Lambda \
                --metric-name Invocations \
                --dimensions Name=FunctionName,Value=$FUNCTION_NAME \
                --start-time $(date -u -d '30 days ago' +%Y-%m-%dT%H:%M:%S) \
                --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
                --period 2592000 \
                --statistics Sum \
                --region $REGION \
                --query 'Datapoints[0].Sum' \
                --output text 2>/dev/null || echo "0")
            
            if [ "$INVOCATIONS" == "0" ] || [ "$INVOCATIONS" == "None" ]; then
                log_action "  ${RED}[DELETE]${NC} Lambda $FUNCTION_NAME - Not invoked in 30 days"
                ((TOTAL_RESOURCES_FOUND++))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws lambda delete-function --function-name $FUNCTION_NAME --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No Lambda functions found"
    fi
}

# 9. Clean up CloudWatch Log Groups
cleanup_cloudwatch_logs() {
    log_action ""
    log_action "${BLUE}=== Checking CloudWatch Log Groups ===${NC}"
    
    LOG_GROUPS=$(aws logs describe-log-groups \
        --region $REGION \
        --query 'logGroups[*].[logGroupName,creationTime,storedBytes]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$LOG_GROUPS" != "[]" ]; then
        echo "$LOG_GROUPS" | jq -r '.[] | @json' | while read -r log_group; do
            LOG_GROUP_NAME=$(echo "$log_group" | jq -r '.[0]')
            CREATION_TIME=$(echo "$log_group" | jq -r '.[1]')
            STORED_BYTES=$(echo "$log_group" | jq -r '.[2]')
            
            # Skip if project related
            if should_preserve "" "$LOG_GROUP_NAME"; then
                continue
            fi
            
            # Check last event time
            LAST_EVENT=$(aws logs describe-log-streams \
                --log-group-name "$LOG_GROUP_NAME" \
                --order-by LastEventTime \
                --descending \
                --limit 1 \
                --region $REGION \
                --query 'logStreams[0].lastEventTimestamp' \
                --output text 2>/dev/null || echo "")
            
            if [ -z "$LAST_EVENT" ] || [ "$LAST_EVENT" == "None" ]; then
                # Empty log group
                log_action "  ${RED}[DELETE]${NC} Log Group $LOG_GROUP_NAME - Empty"
                ((TOTAL_RESOURCES_FOUND++))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws logs delete-log-group --log-group-name "$LOG_GROUP_NAME" --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            else
                # Check age of last event
                DAYS_OLD=$(( ($(date +%s) - $((LAST_EVENT/1000))) / 86400 ))
                if [ $DAYS_OLD -gt 90 ]; then
                    log_action "  ${RED}[DELETE]${NC} Log Group $LOG_GROUP_NAME - No logs for $DAYS_OLD days"
                    ((TOTAL_RESOURCES_FOUND++))
                    
                    if [ "$DRY_RUN" == "false" ]; then
                        aws logs delete-log-group --log-group-name "$LOG_GROUP_NAME" --region $REGION 2>/dev/null || true
                        ((TOTAL_RESOURCES_DELETED++))
                    fi
                fi
            fi
        done
    else
        log_action "  No log groups found"
    fi
}

# 10. Clean up S3 Buckets
cleanup_s3_buckets() {
    log_action ""
    log_action "${BLUE}=== Checking S3 Buckets ===${NC}"
    
    BUCKETS=$(aws s3api list-buckets --query 'Buckets[*].[Name,CreationDate]' --output json 2>/dev/null || echo "[]")
    
    if [ "$BUCKETS" != "[]" ]; then
        echo "$BUCKETS" | jq -r '.[] | @json' | while read -r bucket; do
            BUCKET_NAME=$(echo "$bucket" | jq -r '.[0]')
            CREATION_DATE=$(echo "$bucket" | jq -r '.[1]')
            
            # Skip if project related
            if should_preserve "" "$BUCKET_NAME"; then
                continue
            fi
            
            # Check if bucket is empty
            OBJECT_COUNT=$(aws s3api list-objects-v2 \
                --bucket $BUCKET_NAME \
                --max-items 1 \
                --query 'Contents | length(@)' \
                --output text 2>/dev/null || echo "error")
            
            if [ "$OBJECT_COUNT" == "0" ] || [ "$OBJECT_COUNT" == "None" ]; then
                # Calculate age
                DAYS_OLD=$(( ($(date +%s) - $(date -d "$CREATION_DATE" +%s)) / 86400 ))
                
                if [ $DAYS_OLD -gt 7 ]; then
                    log_action "  ${RED}[DELETE]${NC} S3 Bucket $BUCKET_NAME - Empty and $DAYS_OLD days old"
                    ((TOTAL_RESOURCES_FOUND++))
                    
                    if [ "$DRY_RUN" == "false" ]; then
                        aws s3api delete-bucket --bucket $BUCKET_NAME 2>/dev/null || true
                        ((TOTAL_RESOURCES_DELETED++))
                    fi
                fi
            fi
        done
    else
        log_action "  No S3 buckets found"
    fi
}

# 11. Clean up NAT Gateways
cleanup_nat_gateways() {
    log_action ""
    log_action "${BLUE}=== Checking NAT Gateways ===${NC}"
    
    NAT_GATEWAYS=$(aws ec2 describe-nat-gateways \
        --region $REGION \
        --filter "Name=state,Values=available" \
        --query 'NatGateways[*].[NatGatewayId,Tags,CreateTime]' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$NAT_GATEWAYS" != "[]" ]; then
        echo "$NAT_GATEWAYS" | jq -r '.[] | @json' | while read -r nat; do
            NAT_ID=$(echo "$nat" | jq -r '.[0]')
            TAGS=$(echo "$nat" | jq -r '.[1] | tostring')
            
            # Check if should preserve
            if should_preserve "$TAGS" "$NAT_ID"; then
                continue
            fi
            
            log_action "  ${YELLOW}[WARNING]${NC} NAT Gateway $NAT_ID found - Review manually (costs $45/month)"
            ((ESTIMATED_MONTHLY_SAVINGS+=45))
        done
    else
        log_action "  No NAT Gateways found"
    fi
}

# 12. Clean up Security Groups
cleanup_security_groups() {
    log_action ""
    log_action "${BLUE}=== Checking Security Groups ===${NC}"
    
    # Get all security groups
    SG_LIST=$(aws ec2 describe-security-groups \
        --region $REGION \
        --query 'SecurityGroups[?GroupName!=`default`].[GroupId,GroupName,Tags]' \
        --output json 2>/dev/null || echo "[]")
    
    # Get all network interfaces to check SG usage
    USED_SGS=$(aws ec2 describe-network-interfaces \
        --region $REGION \
        --query 'NetworkInterfaces[*].Groups[*].GroupId' \
        --output text 2>/dev/null | tr '\t' '\n' | sort | uniq)
    
    if [ "$SG_LIST" != "[]" ]; then
        echo "$SG_LIST" | jq -r '.[] | @json' | while read -r sg; do
            SG_ID=$(echo "$sg" | jq -r '.[0]')
            SG_NAME=$(echo "$sg" | jq -r '.[1]')
            TAGS=$(echo "$sg" | jq -r '.[2] | tostring')
            
            # Skip if project related
            if should_preserve "$TAGS" "$SG_NAME"; then
                continue
            fi
            
            # Check if in use
            if ! echo "$USED_SGS" | grep -q "$SG_ID"; then
                log_action "  ${RED}[DELETE]${NC} Security Group $SG_NAME ($SG_ID) - Not in use"
                ((TOTAL_RESOURCES_FOUND++))
                
                if [ "$DRY_RUN" == "false" ]; then
                    aws ec2 delete-security-group --group-id $SG_ID --region $REGION 2>/dev/null || true
                    ((TOTAL_RESOURCES_DELETED++))
                fi
            fi
        done
    else
        log_action "  No security groups found"
    fi
}

# Summary function
print_summary() {
    log_action ""
    log_action "${GREEN}=========================================${NC}"
    log_action "${GREEN}CLEANUP SUMMARY${NC}"
    log_action "${GREEN}=========================================${NC}"
    log_action ""
    log_action "Resources Found for Cleanup: $TOTAL_RESOURCES_FOUND"
    
    if [ "$DRY_RUN" == "false" ]; then
        log_action "Resources Actually Deleted: $TOTAL_RESOURCES_DELETED"
    else
        log_action "Resources That Would Be Deleted: $TOTAL_RESOURCES_FOUND"
    fi
    
    log_action "Estimated Monthly Savings: \$$ESTIMATED_MONTHLY_SAVINGS"
    log_action "Estimated Annual Savings: \$$((ESTIMATED_MONTHLY_SAVINGS * 12))"
    log_action ""
    log_action "Log file saved to: $LOG_FILE"
    
    if [ "$DRY_RUN" == "true" ]; then
        log_action ""
        log_action "${YELLOW}=========================================${NC}"
        log_action "${YELLOW}DRY RUN COMPLETE${NC}"
        log_action "${YELLOW}To actually delete resources, run:${NC}"
        log_action "${GREEN}DRY_RUN=false $0${NC}"
        log_action "${YELLOW}=========================================${NC}"
    else
        log_action ""
        log_action "${GREEN}=========================================${NC}"
        log_action "${GREEN}CLEANUP COMPLETE!${NC}"
        log_action "${GREEN}Resources have been deleted.${NC}"
        log_action "${GREEN}=========================================${NC}"
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
    
    # Confirm if not dry run
    if [ "$DRY_RUN" == "false" ] && [ "$FORCE" != "true" ]; then
        echo -e "${RED}WARNING: This will DELETE resources permanently!${NC}"
        echo -e "Resources with these tags will be preserved: $PRESERVE_TAGS"
        echo -e "Project name pattern to preserve: $PROJECT_NAME"
        read -p "Are you sure you want to continue? Type 'yes' to confirm: " confirmation
        if [ "$confirmation" != "yes" ]; then
            echo "Aborted."
            exit 1
        fi
    fi
    
    log_action "Starting cleanup scan..."
    
    # Run all cleanup functions
    cleanup_ec2_instances
    cleanup_ebs_volumes
    cleanup_elastic_ips
    cleanup_ebs_snapshots
    cleanup_amis
    cleanup_load_balancers
    cleanup_rds_snapshots
    cleanup_lambda_functions
    cleanup_cloudwatch_logs
    cleanup_s3_buckets
    cleanup_nat_gateways
    cleanup_security_groups
    
    # Print summary
    print_summary
}

# Run main function
main