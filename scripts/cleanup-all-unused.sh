#!/bin/bash
# Comprehensive AWS Resource Cleanup Script
# Removes all unused AWS resources for the Oddiya project

source "$(dirname "$0")/common.sh"

# Initialize script with AWS requirements
init_script "AWS Resource Cleanup" true

# Track resources to remove
RESOURCES_TO_REMOVE=()
TOTAL_COST_SAVINGS=0

print_header "Comprehensive AWS Resource Cleanup"
log_warning "This script will identify and remove ALL unused AWS resources"

# Function to calculate monthly cost savings
calculate_savings() {
    local resource_type=$1
    local count=$2
    local cost_per_item=$3
    local savings=$(echo "$count * $cost_per_item" | bc)
    TOTAL_COST_SAVINGS=$(echo "$TOTAL_COST_SAVINGS + $savings" | bc)
    echo "$savings"
}

# 1. Cleanup Unused Elastic IPs
cleanup_elastic_ips() {
    print_section "Elastic IPs"
    log_info "Checking for unassociated Elastic IPs..."
    
    local unassociated_ips=$(aws ec2 describe-addresses --region $AWS_REGION \
        --query 'Addresses[?AssociationId==`null`].[AllocationId,PublicIp]' \
        --output text)
    
    if [ ! -z "$unassociated_ips" ]; then
        local count=0
        while IFS=$'\t' read -r allocation_id public_ip; do
            log_warning "Found unassociated Elastic IP: $public_ip"
            RESOURCES_TO_REMOVE+=("eip:$allocation_id:$public_ip")
            ((count++))
        done <<< "$unassociated_ips"
        
        local savings=$(calculate_savings "Elastic IP" $count 3.60)
        log_info "Potential monthly savings: \$$savings"
    else
        log_success "No unassociated Elastic IPs found"
    fi
}

# 2. Cleanup Unused Security Groups
cleanup_security_groups() {
    print_section "Security Groups"
    log_info "Checking for unused security groups..."
    
    # Get all oddiya-related security groups
    local all_sgs=$(aws ec2 describe-security-groups --region $AWS_REGION \
        --query 'SecurityGroups[?contains(GroupName, `oddiya`)].[GroupId,GroupName,VpcId]' \
        --output text)
    
    # Get security groups in use by network interfaces
    local used_sgs=$(aws ec2 describe-network-interfaces --region $AWS_REGION \
        --query 'NetworkInterfaces[].Groups[].GroupId' \
        --output text | sort -u)
    
    local count=0
    while IFS=$'\t' read -r sg_id sg_name vpc_id; do
        if ! echo "$used_sgs" | grep -q "$sg_id"; then
            # Check if it's a default security group
            if [[ "$sg_name" != *"default"* ]]; then
                log_warning "Found unused security group: $sg_name ($sg_id) in VPC $vpc_id"
                RESOURCES_TO_REMOVE+=("sg:$sg_id:$sg_name:$vpc_id")
                ((count++))
            fi
        fi
    done <<< "$all_sgs"
    
    if [ $count -gt 0 ]; then
        log_info "Found $count unused security groups"
    else
        log_success "No unused security groups found"
    fi
}

# 3. Cleanup Empty/Unused VPCs
cleanup_vpcs() {
    print_section "VPCs"
    log_info "Checking for unused VPCs..."
    
    # Get all oddiya VPCs
    local vpcs=$(aws ec2 describe-vpcs --region $AWS_REGION \
        --query 'Vpcs[?Tags[?Value==`Oddiya`]].[VpcId,CidrBlock]' \
        --output text)
    
    local count=0
    while IFS=$'\t' read -r vpc_id cidr; do
        # Check if VPC has any running instances
        local instances=$(aws ec2 describe-instances --region $AWS_REGION \
            --filters "Name=vpc-id,Values=$vpc_id" \
            --query 'Reservations[].Instances[?State.Name!=`terminated`].InstanceId' \
            --output text)
        
        # Check if VPC has any active ECS tasks
        local eni_count=$(aws ec2 describe-network-interfaces --region $AWS_REGION \
            --filters "Name=vpc-id,Values=$vpc_id" \
            --query 'NetworkInterfaces[?Status==`in-use`] | length(@)' \
            --output text)
        
        if [ -z "$instances" ] && [ "$eni_count" -le "3" ]; then
            log_warning "Found potentially unused VPC: $vpc_id ($cidr)"
            RESOURCES_TO_REMOVE+=("vpc:$vpc_id:$cidr")
            ((count++))
        fi
    done <<< "$vpcs"
    
    if [ $count -gt 0 ]; then
        log_info "Found $count potentially unused VPCs"
    else
        log_success "All VPCs are in use"
    fi
}

# 4. Cleanup Unused ECS Resources
cleanup_ecs_resources() {
    print_section "ECS Resources"
    log_info "Checking for unused ECS clusters and services..."
    
    # Check ECS services with 0 running tasks
    local clusters=$(aws ecs list-clusters --region $AWS_REGION --query 'clusterArns[]' --output text)
    
    for cluster_arn in $clusters; do
        local cluster_name=$(echo $cluster_arn | awk -F'/' '{print $NF}')
        
        # Check if cluster has any services
        local services=$(aws ecs list-services --cluster $cluster_name --region $AWS_REGION \
            --query 'serviceArns[]' --output text)
        
        if [ -z "$services" ]; then
            log_warning "Found empty ECS cluster: $cluster_name"
            RESOURCES_TO_REMOVE+=("ecs-cluster:$cluster_name")
        else
            # Check each service
            for service_arn in $services; do
                local service_name=$(echo $service_arn | awk -F'/' '{print $NF}')
                local running_count=$(aws ecs describe-services --cluster $cluster_name \
                    --services $service_name --region $AWS_REGION \
                    --query 'services[0].runningCount' --output text)
                
                if [ "$running_count" = "0" ] || [ "$running_count" = "None" ]; then
                    log_warning "Found inactive ECS service: $service_name in cluster $cluster_name"
                    RESOURCES_TO_REMOVE+=("ecs-service:$cluster_name:$service_name")
                fi
            done
        fi
    done
}

# 5. Cleanup DynamoDB Tables (not used - app uses PostgreSQL)
cleanup_dynamodb() {
    print_section "DynamoDB Tables"
    log_info "Checking DynamoDB tables (app uses PostgreSQL, these are unused)..."
    
    local tables=$(aws dynamodb list-tables --region $AWS_REGION \
        --query 'TableNames[?starts_with(@, `oddiya`)]' --output text)
    
    local count=0
    for table in $tables; do
        log_warning "Found DynamoDB table to remove: $table"
        RESOURCES_TO_REMOVE+=("dynamodb:$table")
        ((count++))
    done
    
    if [ $count -gt 0 ]; then
        local savings=$(calculate_savings "DynamoDB" $count 25)
        log_info "Potential monthly savings: \$$savings"
    else
        log_success "No DynamoDB tables found"
    fi
}

# 6. Cleanup old ECR images
cleanup_ecr_images() {
    print_section "ECR Images"
    log_info "Checking for old ECR images..."
    
    local repos=$(aws ecr describe-repositories --region $AWS_REGION \
        --query 'repositories[].repositoryName' --output text)
    
    for repo in $repos; do
        # Keep only last 5 images
        local old_images=$(aws ecr list-images --repository-name $repo --region $AWS_REGION \
            --query 'imageIds[5:].imageDigest' --output text)
        
        if [ ! -z "$old_images" ]; then
            local count=$(echo "$old_images" | wc -w)
            log_warning "Found $count old images in ECR repository: $repo"
            for digest in $old_images; do
                RESOURCES_TO_REMOVE+=("ecr-image:$repo:$digest")
            done
        fi
    done
}

# 7. Cleanup unused Load Balancers and Target Groups
cleanup_load_balancers() {
    print_section "Load Balancers"
    log_info "Checking for unused load balancers and target groups..."
    
    # Check target groups without healthy targets
    local tgs=$(aws elbv2 describe-target-groups --region $AWS_REGION \
        --query 'TargetGroups[?contains(TargetGroupName, `oddiya`)].[TargetGroupArn,TargetGroupName]' \
        --output text)
    
    while IFS=$'\t' read -r tg_arn tg_name; do
        local health=$(aws elbv2 describe-target-health --target-group-arn $tg_arn \
            --region $AWS_REGION --query 'TargetHealthDescriptions[?TargetHealth.State==`healthy`] | length(@)' \
            --output text)
        
        if [ "$health" = "0" ]; then
            log_warning "Found target group with no healthy targets: $tg_name"
            RESOURCES_TO_REMOVE+=("tg:$tg_arn:$tg_name")
        fi
    done <<< "$tgs"
}

# Execute cleanup
execute_cleanup() {
    if [ ${#RESOURCES_TO_REMOVE[@]} -eq 0 ]; then
        log_success "No unused resources found to remove!"
        return
    fi
    
    print_header "Resources to Remove"
    
    echo -e "\n${YELLOW}The following resources will be removed:${NC}"
    for resource in "${RESOURCES_TO_REMOVE[@]}"; do
        echo "  - $resource"
    done
    
    echo -e "\n${CYAN}Estimated monthly savings: \$$TOTAL_COST_SAVINGS${NC}"
    
    if ! confirm_action "Do you want to proceed with cleanup?"; then
        log_warning "Cleanup cancelled"
        return
    fi
    
    print_section "Executing Cleanup"
    
    for resource in "${RESOURCES_TO_REMOVE[@]}"; do
        IFS=':' read -r type id extra1 extra2 <<< "$resource"
        
        case $type in
            "eip")
                log_info "Releasing Elastic IP: $extra1"
                aws ec2 release-address --allocation-id $id --region $AWS_REGION
                ;;
            "sg")
                log_info "Deleting security group: $extra1"
                aws ec2 delete-security-group --group-id $id --region $AWS_REGION 2>/dev/null || \
                    log_warning "Could not delete $extra1 (may be in use)"
                ;;
            "dynamodb")
                log_info "Deleting DynamoDB table: $id"
                aws dynamodb delete-table --table-name $id --region $AWS_REGION
                ;;
            "ecr-image")
                log_info "Deleting ECR image from $id"
                aws ecr batch-delete-image --repository-name $id --region $AWS_REGION \
                    --image-ids imageDigest=$extra1 >/dev/null
                ;;
            "ecs-service")
                log_info "Deleting ECS service: $extra1"
                aws ecs update-service --cluster $id --service $extra1 --desired-count 0 \
                    --region $AWS_REGION >/dev/null
                aws ecs delete-service --cluster $id --service $extra1 --region $AWS_REGION
                ;;
            "ecs-cluster")
                log_info "Deleting empty ECS cluster: $id"
                aws ecs delete-cluster --cluster $id --region $AWS_REGION
                ;;
            "tg")
                log_info "Deleting unused target group: $extra1"
                aws elbv2 delete-target-group --target-group-arn $id --region $AWS_REGION
                ;;
            "vpc")
                log_warning "VPC deletion requires manual cleanup: $id"
                ;;
        esac
    done
    
    log_success "Cleanup completed!"
}

# Main execution
main() {
    # Run all cleanup checks
    cleanup_elastic_ips
    cleanup_security_groups
    cleanup_vpcs
    cleanup_ecs_resources
    cleanup_dynamodb
    cleanup_ecr_images
    cleanup_load_balancers
    
    # Execute cleanup
    execute_cleanup
    
    # Generate report
    print_header "Cleanup Summary"
    log_info "Total resources identified: ${#RESOURCES_TO_REMOVE[@]}"
    log_info "Estimated monthly savings: \$$TOTAL_COST_SAVINGS"
    
    script_complete
}

# Run main function
main "$@"