#!/bin/bash
# Consolidated AWS Management Script for Oddiya
# Combines functionality from list-aws-compute-resources.sh, analyze-and-cleanup-aws.sh, 
# verify-aws-resources.sh, and fix-ecs-deployment.sh

# Source common utilities
SCRIPT_DIR="$(dirname "$0")"
source "$SCRIPT_DIR/common.sh" || {
    echo "Error: Cannot source common.sh"
    exit 1
}

# ═══════════════════════════════════════════════════════════════════════════════
# SCRIPT CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════════

SCRIPT_VERSION="1.0.0"
DRY_RUN=true
BACKUP_DIR=""

# Resource tracking arrays (compatible with older bash)
RESOURCES_IN_USE=""
RESOURCES_DEPLOYED=""
RESOURCES_TO_REMOVE=""

# ═══════════════════════════════════════════════════════════════════════════════
# RESOURCE TRACKING HELPER FUNCTIONS (for bash 3.x compatibility)
# ═══════════════════════════════════════════════════════════════════════════════

# Add resource to tracking list
add_resource_in_use() {
    local resource="$1"
    if ! echo "$RESOURCES_IN_USE" | grep -q "$resource"; then
        RESOURCES_IN_USE="$RESOURCES_IN_USE $resource"
    fi
}

add_resource_deployed() {
    local resource="$1"
    if ! echo "$RESOURCES_DEPLOYED" | grep -q "$resource"; then
        RESOURCES_DEPLOYED="$RESOURCES_DEPLOYED $resource"
    fi
}

add_resource_to_remove() {
    local resource="$1"
    if ! echo "$RESOURCES_TO_REMOVE" | grep -q "$resource"; then
        RESOURCES_TO_REMOVE="$RESOURCES_TO_REMOVE $resource"
    fi
}

# Check if resource exists in list
resource_in_use_exists() {
    local resource="$1"
    echo "$RESOURCES_IN_USE" | grep -q "$resource"
}

resource_deployed_exists() {
    local resource="$1"
    echo "$RESOURCES_DEPLOYED" | grep -q "$resource"
}

# Count resources
count_resources_in_use() {
    echo "$RESOURCES_IN_USE" | wc -w
}

count_resources_deployed() {
    echo "$RESOURCES_DEPLOYED" | wc -w  
}

count_resources_to_remove() {
    echo "$RESOURCES_TO_REMOVE" | wc -w
}

# ═══════════════════════════════════════════════════════════════════════════════
# HELP AND USAGE FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

show_help() {
    cat << EOF
$(print_header "AWS Management Tool - Oddiya Project")

USAGE:
    $0 COMMAND [OPTIONS]

COMMANDS:
    list        List all AWS resources for the project
    analyze     Analyze resources and identify unused ones
    cleanup     Clean up unused resources (dry-run by default)
    deploy      Fix ECS deployment issues
    fix         Alias for deploy command

OPTIONS:
    --execute              Actually delete resources (not dry-run)
    --region REGION        AWS region (default: $REGION)
    --help, -h             Show this help message
    --version, -v          Show version information

EXAMPLES:
    # List all resources
    $0 list

    # Analyze and identify unused resources
    $0 analyze

    # Clean up unused resources (dry-run first)
    $0 cleanup
    
    # Clean up unused resources (execute)
    $0 cleanup --execute

    # Fix ECS deployment issues
    $0 deploy

    # Use different region
    $0 list --region us-west-2

EOF
}

show_version() {
    echo -e "${BLUE}${BOLD}AWS Management Tool${NC}"
    echo -e "${CYAN}Version: $SCRIPT_VERSION${NC}"
    echo -e "${CYAN}Updated: $(date +'%Y-%m-%d')${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
# RESOURCE LISTING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

list_ecs_resources() {
    print_section "ECS Resources"
    
    # List ECS Clusters
    log_info "ECS Clusters:"
    local clusters=$(aws ecs list-clusters --region $REGION --query 'clusterArns[]' --output text 2>/dev/null || echo "")
    
    if [ -z "$clusters" ]; then
        echo "  - No ECS clusters found"
        return
    fi
    
    for cluster_arn in $clusters; do
        local cluster_name=$(echo $cluster_arn | awk -F'/' '{print $NF}')
        echo -e "  🐳 Cluster: ${BOLD}$cluster_name${NC}"
        
        # List services in cluster
        local services=$(aws ecs list-services --cluster $cluster_name --region $REGION --query 'serviceArns[]' --output text 2>/dev/null || echo "")
        for service_arn in $services; do
            if [ ! -z "$service_arn" ]; then
                local service_name=$(echo $service_arn | awk -F'/' '{print $NF}')
                local service_status=$(get_resource_status "ecs-service" "$service_name" "$cluster_name")
                echo -e "    └─ Service: $service_name ($service_status)"
            fi
        done
    done
}

list_ecr_repositories() {
    print_section "Container Registry (ECR)"
    
    local repos=$(aws ecr describe-repositories --region $REGION --query 'repositories[].[repositoryName,repositoryUri]' --output text 2>/dev/null || echo "")
    
    if [ -z "$repos" ]; then
        echo "  - No ECR repositories found"
        return
    fi
    
    while IFS=$'\t' read -r name uri; do
        if [ ! -z "$name" ]; then
            local image_count=$(aws ecr list-images --repository-name $name --region $REGION --query 'imageIds | length(@)' --output text 2>/dev/null || echo "0")
            echo -e "  📦 Repository: ${BOLD}$name${NC}"
            echo "     URI: $uri"
            echo "     Images: $image_count"
        fi
    done <<< "$repos"
}

list_load_balancers() {
    print_section "Load Balancers"
    
    local albs=$(aws elbv2 describe-load-balancers --region $REGION --query 'LoadBalancers[].[LoadBalancerName,State.Code,DNSName]' --output text 2>/dev/null || echo "")
    
    if [ -z "$albs" ]; then
        echo "  - No load balancers found"
        return
    fi
    
    while IFS=$'\t' read -r name state dns; do
        if [[ "$name" == *"$PROJECT_PREFIX"* ]]; then
            if [ "$state" = "active" ]; then
                echo -e "  ${GREEN}⚖️  ALB: $name (State: $state)${NC}"
            else
                echo -e "  ${YELLOW}⚖️  ALB: $name (State: $state)${NC}"
            fi
            echo "     DNS: $dns"
            
            # Get target groups
            local alb_arn=$(aws elbv2 describe-load-balancers --names $name --region $REGION --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo "")
            if [ ! -z "$alb_arn" ] && [ "$alb_arn" != "None" ]; then
                local tgs=$(aws elbv2 describe-target-groups --load-balancer-arn $alb_arn --region $REGION --query 'TargetGroups[].[TargetGroupName,HealthCheckPath]' --output text 2>/dev/null || echo "")
                while IFS=$'\t' read -r tg_name health_path; do
                    if [ ! -z "$tg_name" ]; then
                        echo "     └─ Target Group: $tg_name (Health: $health_path)"
                    fi
                done <<< "$tgs"
            fi
        fi
    done <<< "$albs"
}

list_rds_instances() {
    print_section "RDS Databases"
    
    local dbs=$(aws rds describe-db-instances --region $REGION --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus,Engine,AllocatedStorage]' --output text 2>/dev/null || echo "")
    
    if [ -z "$dbs" ]; then
        echo "  - No RDS instances found"
        return
    fi
    
    while IFS=$'\t' read -r id status engine storage; do
        if [[ "$id" == *"$PROJECT_PREFIX"* ]]; then
            local status_color=$(get_resource_status "rds-instance" "$id")
            echo -e "  🗄️  RDS: ${BOLD}$id${NC} ($status_color)"
            echo "     Engine: $engine"
            echo "     Storage: ${storage}GB"
        fi
    done <<< "$dbs"
}

list_s3_buckets() {
    print_section "S3 Buckets"
    
    local buckets=$(aws s3api list-buckets --query 'Buckets[].[Name,CreationDate]' --output text 2>/dev/null || echo "")
    
    if [ -z "$buckets" ]; then
        echo "  - No S3 buckets found"
        return
    fi
    
    while IFS=$'\t' read -r name date; do
        if [[ "$name" == *"$PROJECT_PREFIX"* ]]; then
            local size=$(aws s3 ls s3://$name --recursive --summarize 2>/dev/null | grep "Total Size" | awk '{print $3}' || echo "0")
            if [ -z "$size" ] || [ "$size" = "0" ]; then
                size="Empty"
            else
                size=$(format_bytes $size)
            fi
            echo -e "  🪣 Bucket: ${BOLD}$name${NC}"
            echo "     Created: $date"
            echo "     Size: $size"
        fi
    done <<< "$buckets"
}

list_dynamodb_tables() {
    print_section "DynamoDB Tables"
    
    local tables=$(aws dynamodb list-tables --region $REGION --query 'TableNames[]' --output text 2>/dev/null || echo "")
    
    if [ -z "$tables" ]; then
        echo "  - No DynamoDB tables found"
        return
    fi
    
    for table in $tables; do
        if [[ "$table" == ${PROJECT_PREFIX}_* ]]; then
            local status=$(aws dynamodb describe-table --table-name $table --region $REGION --query 'Table.TableStatus' --output text 2>/dev/null || echo "UNKNOWN")
            local items=$(aws dynamodb describe-table --table-name $table --region $REGION --query 'Table.ItemCount' --output text 2>/dev/null || echo "0")
            echo -e "  📊 Table: ${BOLD}$table${NC}"
            echo "     Status: $status"
            echo "     Items: $items"
        fi
    done
}

list_sqs_queues() {
    print_section "SQS Queues"
    
    local queues=$(aws sqs list-queues --region $REGION --query 'QueueUrls[]' --output text 2>/dev/null || echo "")
    
    if [ -z "$queues" ]; then
        echo "  - No SQS queues found"
        return
    fi
    
    for queue_url in $queues; do
        if [[ "$queue_url" == *"$PROJECT_PREFIX"* ]]; then
            local queue_name=$(echo $queue_url | awk -F'/' '{print $NF}')
            local messages=$(aws sqs get-queue-attributes --queue-url $queue_url --attribute-names ApproximateNumberOfMessages --region $REGION --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null || echo "0")
            echo -e "  📬 Queue: ${BOLD}$queue_name${NC}"
            echo "     Messages: $messages"
        fi
    done
}

list_cloudwatch_alarms() {
    print_section "CloudWatch Alarms"
    
    local alarms=$(aws cloudwatch describe-alarms --region $REGION --query 'MetricAlarms[].[AlarmName,StateValue]' --output text 2>/dev/null || echo "")
    
    if [ -z "$alarms" ]; then
        echo "  - No CloudWatch alarms found"
        return
    fi
    
    while IFS=$'\t' read -r name state; do
        if [[ "$name" == *"$PROJECT_PREFIX"* ]] || [[ "$name" == *"$(echo $PROJECT_PREFIX | sed 's/.*/\u&/')"* ]]; then
            if [ "$state" = "OK" ]; then
                echo -e "  ${GREEN}🔔 Alarm: $name (State: $state)${NC}"
            elif [ "$state" = "ALARM" ]; then
                echo -e "  ${RED}🚨 Alarm: $name (State: $state)${NC}"
            else
                echo -e "  ${YELLOW}🔔 Alarm: $name (State: $state)${NC}"
            fi
        fi
    done <<< "$alarms"
}

# ═══════════════════════════════════════════════════════════════════════════════
# ANALYSIS FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

analyze_yaml_configs() {
    print_section "Analyzing YAML Configuration Files"
    
    # Analyze GitHub Actions workflows
    log_info "GitHub Actions Workflows:"
    for file in .github/workflows/*.yml; do
        if [ -f "$file" ]; then
            echo "  📄 $(basename $file)"
            
            # Extract AWS resources from workflow
            if grep -q "ECS_SERVICE\|ECS_CLUSTER\|ECR_REPOSITORY" "$file"; then
                local ecs_service=$(grep "ECS_SERVICE:" "$file" | head -1 | awk '{print $2}' || echo "")
                local ecs_cluster=$(grep "ECS_CLUSTER:" "$file" | head -1 | awk '{print $2}' || echo "")
                local ecr_repo=$(grep "ECR_REPOSITORY:" "$file" | head -1 | awk '{print $2}' || echo "")
                
                [ ! -z "$ecs_service" ] && add_resource_in_use "ecs-service:$ecs_service"
                [ ! -z "$ecs_cluster" ] && add_resource_in_use "ecs-cluster:$ecs_cluster"
                [ ! -z "$ecr_repo" ] && add_resource_in_use "ecr:$ecr_repo"
            fi
        fi
    done
    
    # Analyze application.yml
    log_info "Application Configuration:"
    if [ -f "src/main/resources/application.yml" ]; then
        echo "  📄 application.yml"
        
        # Check for S3 bucket
        local s3_bucket=$(grep "bucket:" src/main/resources/application.yml | grep -v "#" | head -1 | awk '{print $2}' | tr -d '${:}' || echo "")
        [ ! -z "$s3_bucket" ] && add_resource_in_use "s3:$s3_bucket"
        
        # Check for DynamoDB
        if grep -q "dynamodb:\s*enabled:\s*true" src/main/resources/application.yml; then
            add_resource_in_use "dynamodb:${PROJECT_PREFIX}_*"
        fi
        
        # Check for SQS
        if grep -q "sqs:\s*enabled:\s*true" src/main/resources/application.yml; then
            add_resource_in_use "sqs:${PROJECT_PREFIX}-*"
        fi
        
        # Check for RDS
        if grep -q "postgresql" src/main/resources/application.yml; then
            add_resource_in_use "rds:${PROJECT_PREFIX}-db"
        fi
    fi
    
    log_info "Resources Referenced in Code:"
    for resource in $RESOURCES_IN_USE; do
        echo "  ✅ $resource"
    done
}

list_aws_resources() {
    print_section "Scanning Deployed AWS Resources"
    
    # Create backup directory if not exists
    if [ -z "$BACKUP_DIR" ]; then
        BACKUP_DIR=$(create_backup_dir "aws-resources-backup")
    fi
    
    log_info "Backing up resource lists to $BACKUP_DIR"
    
    # ECS Clusters
    log_info "ECS Clusters:"
    aws ecs list-clusters --region $REGION --output json > "$BACKUP_DIR/ecs-clusters.json"
    local clusters=$(aws ecs list-clusters --region $REGION --query 'clusterArns[]' --output text 2>/dev/null || echo "")
    for cluster in $clusters; do
        local cluster_name=$(echo $cluster | awk -F'/' '{print $NF}')
        echo "  🐳 $cluster_name"
        add_resource_deployed "ecs-cluster:$cluster_name"
        
        # List services in cluster
        local services=$(aws ecs list-services --cluster $cluster_name --region $REGION --query 'serviceArns[]' --output text 2>/dev/null || echo "")
        for service in $services; do
            local service_name=$(echo $service | awk -F'/' '{print $NF}')
            echo "    └─ Service: $service_name"
            add_resource_deployed "ecs-service:$service_name"
        done
    done
    
    # ECR Repositories
    log_info "ECR Repositories:"
    aws ecr describe-repositories --region $REGION --output json > "$BACKUP_DIR/ecr-repositories.json" 2>/dev/null || echo "{}" > "$BACKUP_DIR/ecr-repositories.json"
    local repos=$(aws ecr describe-repositories --region $REGION --query 'repositories[].repositoryName' --output text 2>/dev/null || echo "")
    for repo in $repos; do
        echo "  📦 $repo"
        add_resource_deployed "ecr:$repo"
    done
    
    # S3 Buckets
    log_info "S3 Buckets:"
    aws s3api list-buckets --output json > "$BACKUP_DIR/s3-buckets.json"
    local buckets=$(aws s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null || echo "")
    for bucket in $buckets; do
        if [[ "$bucket" == *"$PROJECT_PREFIX"* ]]; then
            echo "  🪣 $bucket"
            add_resource_deployed "s3:$bucket"
        fi
    done
    
    # RDS Instances
    log_info "RDS Instances:"
    aws rds describe-db-instances --region $REGION --output json > "$BACKUP_DIR/rds-instances.json"
    local dbs=$(aws rds describe-db-instances --region $REGION --query 'DBInstances[].DBInstanceIdentifier' --output text 2>/dev/null || echo "")
    for db in $dbs; do
        if [[ "$db" == *"$PROJECT_PREFIX"* ]]; then
            echo "  🗄️  $db"
            add_resource_deployed "rds:$db"
        fi
    done
    
    # DynamoDB Tables
    log_info "DynamoDB Tables:"
    aws dynamodb list-tables --region $REGION --output json > "$BACKUP_DIR/dynamodb-tables.json"
    local tables=$(aws dynamodb list-tables --region $REGION --query 'TableNames[]' --output text 2>/dev/null || echo "")
    for table in $tables; do
        if [[ "$table" == ${PROJECT_PREFIX}_* ]]; then
            echo "  📊 $table"
            add_resource_deployed "dynamodb:$table"
        fi
    done
    
    # SQS Queues
    log_info "SQS Queues:"
    aws sqs list-queues --region $REGION --output json > "$BACKUP_DIR/sqs-queues.json" 2>/dev/null || echo "{}" > "$BACKUP_DIR/sqs-queues.json"
    local queues=$(aws sqs list-queues --region $REGION --query 'QueueUrls[]' --output text 2>/dev/null || echo "")
    for queue in $queues; do
        local queue_name=$(echo $queue | awk -F'/' '{print $NF}')
        if [[ "$queue_name" == ${PROJECT_PREFIX}-* ]]; then
            echo "  📬 $queue_name"
            add_resource_deployed "sqs:$queue_name"
        fi
    done
    
    # Application Load Balancers
    log_info "Application Load Balancers:"
    aws elbv2 describe-load-balancers --region $REGION --output json > "$BACKUP_DIR/alb.json"
    local albs=$(aws elbv2 describe-load-balancers --region $REGION --query 'LoadBalancers[].LoadBalancerName' --output text 2>/dev/null || echo "")
    for alb in $albs; do
        if [[ "$alb" == *"$PROJECT_PREFIX"* ]]; then
            echo "  ⚖️  $alb"
            add_resource_deployed "alb:$alb"
        fi
    done
    
    # Target Groups
    log_info "Target Groups:"
    aws elbv2 describe-target-groups --region $REGION --output json > "$BACKUP_DIR/target-groups.json"
    local tgs=$(aws elbv2 describe-target-groups --region $REGION --query 'TargetGroups[].TargetGroupName' --output text 2>/dev/null || echo "")
    for tg in $tgs; do
        if [[ "$tg" == *"$PROJECT_PREFIX"* ]]; then
            echo "  🎯 $tg"
            add_resource_deployed "tg:$tg"
        fi
    done
}

identify_unused_resources() {
    print_section "Identifying Unused Resources"
    
    for resource in $RESOURCES_DEPLOYED; do
        local found=false
        local resource_type=$(echo $resource | cut -d':' -f1)
        local resource_name=$(echo $resource | cut -d':' -f2)
        
        # Check if resource is in use
        for used_resource in $RESOURCES_IN_USE; do
            local used_type=$(echo $used_resource | cut -d':' -f1)
            local used_name=$(echo $used_resource | cut -d':' -f2)
            
            if [ "$resource_type" = "$used_type" ]; then
                # Handle wildcards
                if [[ "$used_name" == *"*"* ]]; then
                    local pattern="${used_name//\*/.*}"
                    if [[ "$resource_name" =~ $pattern ]]; then
                        found=true
                        break
                    fi
                elif [ "$resource_name" = "$used_name" ]; then
                    found=true
                    break
                fi
            fi
        done
        
        if [ "$found" = false ]; then
            add_resource_to_remove "$resource"
            echo -e "  ${RED}❌ Unused: $resource${NC}"
        else
            echo -e "  ${GREEN}✅ In Use: $resource${NC}"
        fi
    done
    
    local unused_count=$(count_resources_to_remove)
    if [ "$unused_count" -eq 0 ]; then
        log_success "No unused resources found!"
    else
        log_warning "Found $unused_count unused resources"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# CLEANUP FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

remove_unused_resources() {
    local unused_count=$(count_resources_to_remove)
    if [ "$unused_count" -eq 0 ]; then
        return
    fi
    
    print_section "Removing Unused Resources"
    
    for resource in $RESOURCES_TO_REMOVE; do
        local resource_type=$(echo $resource | cut -d':' -f1)
        local resource_name=$(echo $resource | cut -d':' -f2)
        
        log_info "Removing $resource_type: $resource_name"
        
        case $resource_type in
            "ecs-service")
                local cluster_name=$(aws ecs list-services --region $REGION --query "serviceArns[?contains(@, '$resource_name')]" --output text | head -1 | awk -F'/' '{print $(NF-1)}' 2>/dev/null || echo "")
                if [ ! -z "$cluster_name" ]; then
                    if [ "$DRY_RUN" = false ]; then
                        log_info "Scaling down to 0 tasks..."
                        safe_aws_command "aws ecs update-service --cluster $cluster_name --service $resource_name --desired-count 0 --region $REGION"
                        log_info "Deleting service..."
                        safe_aws_command "aws ecs delete-service --cluster $cluster_name --service $resource_name --region $REGION"
                    else
                        echo "  [DRY-RUN] Would delete ECS service: $resource_name"
                    fi
                fi
                ;;
                
            "ecs-cluster")
                if [ "$DRY_RUN" = false ]; then
                    log_info "Deleting cluster..."
                    safe_aws_command "aws ecs delete-cluster --cluster $resource_name --region $REGION"
                else
                    echo "  [DRY-RUN] Would delete ECS cluster: $resource_name"
                fi
                ;;
                
            "ecr")
                if [ "$DRY_RUN" = false ]; then
                    log_info "Deleting ECR repository..."
                    safe_aws_command "aws ecr delete-repository --repository-name $resource_name --force --region $REGION"
                else
                    echo "  [DRY-RUN] Would delete ECR repository: $resource_name"
                fi
                ;;
                
            "s3")
                if [ "$DRY_RUN" = false ]; then
                    log_info "Emptying bucket..."
                    safe_aws_command "aws s3 rm s3://$resource_name --recursive"
                    log_info "Deleting bucket..."
                    safe_aws_command "aws s3api delete-bucket --bucket $resource_name --region $REGION"
                else
                    echo "  [DRY-RUN] Would delete S3 bucket: $resource_name"
                fi
                ;;
                
            "dynamodb")
                if [ "$DRY_RUN" = false ]; then
                    log_info "Deleting DynamoDB table..."
                    safe_aws_command "aws dynamodb delete-table --table-name $resource_name --region $REGION"
                else
                    echo "  [DRY-RUN] Would delete DynamoDB table: $resource_name"
                fi
                ;;
                
            "sqs")
                local queue_url=$(aws sqs get-queue-url --queue-name $resource_name --region $REGION --query 'QueueUrl' --output text 2>/dev/null || echo "")
                if [ ! -z "$queue_url" ]; then
                    if [ "$DRY_RUN" = false ]; then
                        log_info "Deleting SQS queue..."
                        safe_aws_command "aws sqs delete-queue --queue-url $queue_url --region $REGION"
                    else
                        echo "  [DRY-RUN] Would delete SQS queue: $resource_name"
                    fi
                fi
                ;;
                
            "alb")
                local alb_arn=$(aws elbv2 describe-load-balancers --names $resource_name --region $REGION --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo "")
                if [ ! -z "$alb_arn" ] && [ "$alb_arn" != "None" ]; then
                    if [ "$DRY_RUN" = false ]; then
                        log_info "Deleting ALB..."
                        safe_aws_command "aws elbv2 delete-load-balancer --load-balancer-arn $alb_arn --region $REGION"
                    else
                        echo "  [DRY-RUN] Would delete ALB: $resource_name"
                    fi
                fi
                ;;
                
            "tg")
                local tg_arn=$(aws elbv2 describe-target-groups --names $resource_name --region $REGION --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo "")
                if [ ! -z "$tg_arn" ] && [ "$tg_arn" != "None" ]; then
                    if [ "$DRY_RUN" = false ]; then
                        log_info "Deleting Target Group..."
                        safe_aws_command "aws elbv2 delete-target-group --target-group-arn $tg_arn --region $REGION"
                    else
                        echo "  [DRY-RUN] Would delete Target Group: $resource_name"
                    fi
                fi
                ;;
                
            *)
                log_warning "Unknown resource type: $resource_type"
                ;;
        esac
    done
}

verify_remaining_resources() {
    print_section "Verifying Remaining Resources"
    
    log_info "Resources that should remain:"
    
    # Check ECS
    local remaining_clusters=$(aws ecs list-clusters --region $REGION --query 'clusterArns[]' --output text 2>/dev/null || echo "")
    if [ ! -z "$remaining_clusters" ]; then
        echo -e "\n  ECS Clusters:"
        for cluster in $remaining_clusters; do
            local cluster_name=$(echo $cluster | awk -F'/' '{print $NF}')
            if [[ "$cluster_name" == *"$PROJECT_PREFIX"* ]]; then
                echo "    ✅ $cluster_name"
                
                # Check services
                local services=$(aws ecs list-services --cluster $cluster_name --region $REGION --query 'serviceArns[]' --output text 2>/dev/null || echo "")
                for service in $services; do
                    local service_name=$(echo $service | awk -F'/' '{print $NF}')
                    local service_status=$(get_resource_status "ecs-service" "$service_name" "$cluster_name")
                    echo "      └─ $service_name ($service_status)"
                done
            fi
        done
    fi
    
    # Check RDS
    local dbs=$(aws rds describe-db-instances --region $REGION --query 'DBInstances[].DBInstanceIdentifier' --output text 2>/dev/null || echo "")
    if [ ! -z "$dbs" ]; then
        echo -e "\n  RDS Instances:"
        for db in $dbs; do
            if [[ "$db" == *"$PROJECT_PREFIX"* ]]; then
                local status=$(aws rds describe-db-instances --db-instance-identifier $db --region $REGION --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null || echo "unknown")
                echo "    ✅ $db (Status: $status)"
            fi
        done
    fi
    
    # Check S3
    local buckets=$(aws s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null || echo "")
    local project_buckets=""
    for bucket in $buckets; do
        if [[ "$bucket" == *"$PROJECT_PREFIX"* ]]; then
            project_buckets="$project_buckets $bucket"
        fi
    done
    if [ ! -z "$project_buckets" ]; then
        echo -e "\n  S3 Buckets:"
        for bucket in $project_buckets; do
            echo "    ✅ $bucket"
        done
    fi
}

generate_summary() {
    print_header "SUMMARY REPORT"
    
    local report_file="$BACKUP_DIR/cleanup-report.txt"
    
    {
        echo "AWS Resource Cleanup Report"
        echo "Generated: $(timestamp)"
        echo "Region: $REGION"
        echo "Mode: $([ "$DRY_RUN" = true ] && echo "DRY-RUN" || echo "EXECUTE")"
        echo ""
        echo "Resources Analyzed:"
        echo "  - Resources in Code: $(count_resources_in_use)"
        echo "  - Resources Deployed: $(count_resources_deployed)"
        echo "  - Resources to Remove: $(count_resources_to_remove)"
        echo ""
        echo "Resources Removed:"
        for resource in $RESOURCES_TO_REMOVE; do
            echo "  - $resource"
        done
        echo ""
        echo "Resources Retained:"
        for resource in $RESOURCES_IN_USE; do
            echo "  - $resource"
        done
    } > "$report_file"
    
    log_info "Summary:"
    echo -e "  📊 Resources in Code: $(count_resources_in_use)"
    echo -e "  🔍 Resources Deployed: $(count_resources_deployed)"
    echo -e "  ❌ Resources Removed: $(count_resources_to_remove)"
    
    if [ ! -z "$report_file" ]; then
        log_success "Report saved to: $report_file"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# ECS DEPLOYMENT FIX FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

fix_ecs_deployment() {
    print_header "Fix ECS Deployment Configuration"
    
    local cluster="$ECS_DEV_CLUSTER"
    local service="$ECS_DEV_SERVICE"
    local family="$TASK_DEFINITION"
    local account_id=$(get_aws_account_id)
    
    # 1. Get current task definition
    log_info "Fetching current task definition..."
    aws ecs describe-task-definition --task-definition $family --region $REGION --query taskDefinition > current-task-def.json
    
    # 2. Create new task definition with fixes
    log_info "Creating fixed task definition..."
    
    cat > new-task-def.json << EOF
{
  "family": "$family",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::$account_id:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::$account_id:role/ecsTaskRole",
  "containerDefinitions": [
    {
      "name": "$PROJECT_PREFIX",
      "image": "$account_id.dkr.ecr.$REGION.amazonaws.com/$ECR_REPOSITORY:latest",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "aws"
        },
        {
          "name": "SERVER_PORT",
          "value": "8080"
        },
        {
          "name": "AWS_REGION",
          "value": "$REGION"
        },
        {
          "name": "SPRING_DATASOURCE_URL",
          "value": "jdbc:h2:mem:testdb"
        },
        {
          "name": "SPRING_DATASOURCE_USERNAME",
          "value": "sa"
        },
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "value": ""
        },
        {
          "name": "SPRING_JPA_HIBERNATE_DDL_AUTO",
          "value": "create"
        },
        {
          "name": "AWS_S3_ENABLED",
          "value": "true"
        },
        {
          "name": "AWS_DYNAMODB_ENABLED",
          "value": "false"
        },
        {
          "name": "AWS_SQS_ENABLED",
          "value": "false"
        },
        {
          "name": "AWS_CLOUDWATCH_ENABLED",
          "value": "true"
        },
        {
          "name": "S3_BUCKET",
          "value": "$PROJECT_PREFIX-dev-media-$account_id"
        },
        {
          "name": "SPRING_MAIN_ALLOW_BEAN_DEFINITION_OVERRIDING",
          "value": "true"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "$DEV_LOG_GROUP",
          "awslogs-region": "$REGION",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
EOF
    
    log_success "Task definition prepared with fixes:"
    echo "  ✅ Changed SPRING_PROFILES_ACTIVE from 'dynamodb' to 'aws'"
    echo "  ✅ Added SPRING_MAIN_ALLOW_BEAN_DEFINITION_OVERRIDING=true"
    echo "  ✅ Disabled DynamoDB, enabled S3 and CloudWatch"
    echo "  ✅ Using H2 in-memory database for testing"
    
    # 3. Register new task definition
    log_info "Registering new task definition..."
    local new_task_def=$(aws ecs register-task-definition --cli-input-json file://new-task-def.json --region $REGION --query 'taskDefinition.taskDefinitionArn' --output text)
    
    if [ ! -z "$new_task_def" ]; then
        log_success "New task definition registered: $new_task_def"
        
        # 4. Update service to use new task definition
        log_info "Updating ECS service..."
        aws ecs update-service \
            --cluster $cluster \
            --service $service \
            --task-definition $new_task_def \
            --force-new-deployment \
            --region $REGION \
            --output json > /dev/null
        
        log_success "Service updated with new task definition"
        
        # 5. Wait for deployment
        log_info "Waiting for deployment to stabilize..."
        wait_for_ecs_deployment "$cluster" "$service"
        
        # 6. Check service health
        log_info "Checking service health..."
        check_service_health
        
    else
        log_error "Failed to register task definition"
        return 1
    fi
    
    # 7. Show recent logs
    log_info "Recent application logs:"
    aws logs tail $DEV_LOG_GROUP --since 2m --region $REGION 2>/dev/null | tail -20 || echo "No recent logs available"
    
    print_header "Deployment Fix Complete!"
    
    log_info "Monitor the deployment:"
    echo "  ./scripts/monitor-aws-production.sh"
    echo ""
    log_info "View logs:"
    echo "  aws logs tail $DEV_LOG_GROUP --follow --region $REGION"
    echo ""
    log_info "Check service status:"
    echo "  aws ecs describe-services --cluster $cluster --services $service --region $REGION"
    
    # Clean up temporary files
    rm -f current-task-def.json new-task-def.json
}

wait_for_ecs_deployment() {
    local cluster="$1"
    local service="$2"
    local timeout=300
    local elapsed=0
    local interval=10
    
    while [ $elapsed -lt $timeout ]; do
        local status=$(aws ecs describe-services \
            --cluster $cluster \
            --services $service \
            --region $REGION \
            --query 'services[0].{Running:runningCount,Desired:desiredCount,Pending:pendingCount}' \
            --output json)
        
        local running=$(echo $status | jq -r '.Running')
        local desired=$(echo $status | jq -r '.Desired')
        local pending=$(echo $status | jq -r '.Pending')
        
        echo -e "  Status: Running=$running, Desired=$desired, Pending=$pending"
        
        if [ "$running" -eq "$desired" ] && [ "$running" -gt 0 ] && [ "$pending" -eq 0 ]; then
            log_success "Deployment successful! Service is running."
            return 0
        fi
        
        sleep $interval
        elapsed=$((elapsed + interval))
        
        if [ $elapsed -ge $timeout ]; then
            log_warning "Deployment is taking longer than expected."
            echo "Check CloudWatch logs for details:"
            echo "  aws logs tail $DEV_LOG_GROUP --follow --region $REGION"
            return 1
        fi
    done
}

check_service_health() {
    # Get ALB DNS
    local alb_dns=$(aws elbv2 describe-load-balancers --names ${PROJECT_PREFIX}-dev-alb --region $REGION --query 'LoadBalancers[0].DNSName' --output text 2>/dev/null || echo "")
    
    if [ ! -z "$alb_dns" ] && [ "$alb_dns" != "None" ]; then
        echo "  ALB DNS: $alb_dns"
        log_info "Testing health endpoint..."
        
        local health_check=$(curl -s -o /dev/null -w "%{http_code}" http://$alb_dns/actuator/health 2>/dev/null || echo "000")
        
        if [ "$health_check" = "200" ]; then
            log_success "Health check passed! Application is running."
            echo -e "\nAccess your application at: ${BLUE}http://$alb_dns${NC}"
        else
            log_warning "Health check returned: $health_check"
            echo "The application may still be starting up."
        fi
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# COMMAND HANDLERS
# ═══════════════════════════════════════════════════════════════════════════════

cmd_list() {
    print_header "AWS Resource Inventory - $PROJECT_NAME Project"
    print_aws_info
    
    list_ecs_resources
    list_ecr_repositories
    list_load_balancers
    list_rds_instances
    list_s3_buckets
    list_dynamodb_tables
    list_sqs_queues
    list_cloudwatch_alarms
    
    print_header "Resource Scan Complete!"
    
    # Summary
    log_info "Summary of Issues Found:"
    echo "─────────────────────────────"
    
    # Check for critical issues
    local issues_found=false
    
    # Check ECS tasks
    local ecs_status=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $REGION --query 'services[0].runningCount' --output text 2>/dev/null || echo "0")
    if [ "$ecs_status" = "0" ] || [ "$ecs_status" = "None" ]; then
        log_error "ECS Service is DOWN (0 running tasks)"
        issues_found=true
    fi
    
    if [ "$issues_found" = false ]; then
        log_success "No critical issues found"
    fi
    
    log_info "Next Steps:"
    echo "1. Fix ECS deployment configuration with: $0 deploy"
    echo "2. Remove unused resources with: $0 cleanup --execute"
    echo "3. Analyze resource usage with: $0 analyze"
}

cmd_analyze() {
    print_header "AWS Resource Analysis and Cleanup Tool"
    print_aws_info
    
    if [ "$DRY_RUN" = true ]; then
        log_warning "Running in DRY-RUN mode. No resources will be deleted."
        echo -e "${YELLOW}Use --execute flag to actually delete resources.${NC}"
    else
        log_error "WARNING: Running in EXECUTE mode. Resources WILL be deleted!"
        if ! confirm_action "Are you sure you want to continue?" "n"; then
            echo "Aborted."
            exit 0
        fi
    fi
    
    # Create backup directory
    BACKUP_DIR=$(create_backup_dir "aws-resources-backup")
    
    # Execute analysis steps
    analyze_yaml_configs
    list_aws_resources
    identify_unused_resources
    generate_summary
    
    log_success "AWS Resource Analysis Complete!"
    log_info "Backup directory: $BACKUP_DIR"
}

cmd_cleanup() {
    cmd_analyze
    
    local unused_count=$(count_resources_to_remove)
    if [ "$unused_count" -gt 0 ]; then
        remove_unused_resources
        verify_remaining_resources
        generate_summary
        
        log_success "AWS Resource Cleanup Complete!"
    else
        log_info "No resources to clean up."
    fi
}

cmd_deploy() {
    fix_ecs_deployment
}

# ═══════════════════════════════════════════════════════════════════════════════
# ARGUMENT PARSING AND MAIN FUNCTION
# ═══════════════════════════════════════════════════════════════════════════════

parse_arguments() {
    local command=""
    
    # Handle help and version flags first
    for arg in "$@"; do
        case "$arg" in
            --help|-h)
                show_help
                exit 0
                ;;
            --version|-v)
                show_version
                exit 0
                ;;
        esac
    done
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            list|analyze|cleanup|deploy|fix)
                command="$1"
                shift
                ;;
            --execute)
                DRY_RUN=false
                shift
                ;;
            --region)
                REGION="$2"
                export AWS_DEFAULT_REGION="$REGION"
                shift 2
                ;;
            --help|-h|--version|-v)
                # Already handled above
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                echo -e "${YELLOW}Use --help for usage information${NC}"
                exit 1
                ;;
        esac
    done
    
    if [ -z "$command" ]; then
        log_error "No command specified"
        show_help
        exit 1
    fi
    
    echo "$command"
}

main() {
    local start_time=$(date +%s)
    
    # Handle help and version first, before initialization
    for arg in "$@"; do
        case "$arg" in
            --help|-h)
                show_help
                exit 0
                ;;
            --version|-v)
                show_version
                exit 0
                ;;
        esac
    done
    
    # Initialize script
    init_script "AWS Management Tool" true
    
    # Parse arguments
    local command=$(parse_arguments "$@")
    
    # Validate dependencies
    validate_dependencies "jq" "curl" || exit 1
    
    # Execute command
    case "$command" in
        list)
            cmd_list
            ;;
        analyze)
            cmd_analyze
            ;;
        cleanup)
            cmd_cleanup
            ;;
        deploy|fix)
            cmd_deploy
            ;;
        *)
            log_error "Unknown command: $command"
            exit 1
            ;;
    esac
    
    # Show completion message
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    script_complete "AWS Management Tool" "$duration"
}

# Run main function if script is executed directly
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi