 #!/bin/bash

# AWS Compute Resources Inventory Script
# Lists all computing power currently in use across AWS services

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
OUTPUT_FORMAT="${OUTPUT_FORMAT:-table}"  # table, json, or csv

echo "========================================="
echo "AWS Compute Resources Inventory"
echo "Region: $REGION"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Cost tracking
TOTAL_MONTHLY_COST=0

# Function to print section header
print_section() {
    echo ""
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}=========================================${NC}"
}

# 1. EC2 Instances
check_ec2_instances() {
    print_section "EC2 INSTANCES"
    
    INSTANCES=$(aws ec2 describe-instances \
        --region $REGION \
        --query 'Reservations[*].Instances[?State.Name!=`terminated`].[InstanceId,InstanceType,State.Name,PrivateIpAddress,PublicIpAddress,Tags[?Key==`Name`].Value|[0],LaunchTime,PlatformDetails]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$INSTANCES" ]; then
        echo "No EC2 instances found."
    else
        echo -e "${YELLOW}ID\t\tType\t\tState\t\tPrivate IP\tPublic IP\tName\t\tLaunch Time${NC}"
        echo "$INSTANCES" | while IFS=$'\t' read -r id type state private_ip public_ip name launch platform; do
            # Estimate monthly cost
            case "$type" in
                t2.micro) cost=8.5 ;;
                t3.micro) cost=7.5 ;;
                t3.small) cost=15 ;;
                t3.medium) cost=30 ;;
                t3.large) cost=60 ;;
                m5.large) cost=70 ;;
                m5.xlarge) cost=140 ;;
                *) cost=50 ;;  # Default estimate
            esac
            
            if [ "$state" == "running" ]; then
                TOTAL_MONTHLY_COST=$((TOTAL_MONTHLY_COST + cost))
                echo -e "${GREEN}$id\t$type\t$state\t$private_ip\t${public_ip:-none}\t${name:-unnamed}\t$launch${NC}"
            else
                echo -e "${YELLOW}$id\t$type\t$state\t$private_ip\t${public_ip:-none}\t${name:-unnamed}\t$launch${NC}"
            fi
        done
        
        # Count by state
        RUNNING=$(echo "$INSTANCES" | grep -c "running" || true)
        STOPPED=$(echo "$INSTANCES" | grep -c "stopped" || true)
        echo ""
        echo "Summary: $RUNNING running, $STOPPED stopped"
    fi
}

# 2. Lambda Functions
check_lambda_functions() {
    print_section "LAMBDA FUNCTIONS"
    
    FUNCTIONS=$(aws lambda list-functions \
        --region $REGION \
        --query 'Functions[*].[FunctionName,Runtime,CodeSize,MemorySize,Timeout,LastModified]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$FUNCTIONS" ]; then
        echo "No Lambda functions found."
    else
        echo -e "${YELLOW}Function Name\t\t\tRuntime\t\tMemory\tTimeout\tLast Modified${NC}"
        echo "$FUNCTIONS" | while IFS=$'\t' read -r name runtime size memory timeout modified; do
            printf "%-30s\t%-15s\t%sMB\t%ss\t%s\n" "$name" "$runtime" "$memory" "$timeout" "$modified"
        done
        
        TOTAL=$(echo "$FUNCTIONS" | wc -l)
        echo ""
        echo "Total Lambda functions: $TOTAL"
    fi
}

# 3. ECS/Fargate Services
check_ecs_services() {
    print_section "ECS/FARGATE SERVICES"
    
    CLUSTERS=$(aws ecs list-clusters --region $REGION --query 'clusterArns[*]' --output text 2>/dev/null || echo "")
    
    if [ -z "$CLUSTERS" ]; then
        echo "No ECS clusters found."
    else
        for CLUSTER in $CLUSTERS; do
            CLUSTER_NAME=$(echo $CLUSTER | awk -F'/' '{print $NF}')
            echo -e "${GREEN}Cluster: $CLUSTER_NAME${NC}"
            
            # Get services
            SERVICES=$(aws ecs list-services \
                --cluster $CLUSTER \
                --region $REGION \
                --query 'serviceArns[*]' \
                --output text 2>/dev/null || echo "")
            
            if [ ! -z "$SERVICES" ]; then
                for SERVICE in $SERVICES; do
                    SERVICE_NAME=$(echo $SERVICE | awk -F'/' '{print $NF}')
                    
                    # Get service details
                    DETAILS=$(aws ecs describe-services \
                        --cluster $CLUSTER \
                        --services $SERVICE \
                        --region $REGION \
                        --query 'services[0].[desiredCount,runningCount,launchType]' \
                        --output text 2>/dev/null || echo "0 0 unknown")
                    
                    echo "  Service: $SERVICE_NAME - Desired: $(echo $DETAILS | awk '{print $1}'), Running: $(echo $DETAILS | awk '{print $2}'), Type: $(echo $DETAILS | awk '{print $3}')"
                done
            fi
            
            # Get tasks
            TASKS=$(aws ecs list-tasks \
                --cluster $CLUSTER \
                --region $REGION \
                --query 'taskArns[*]' \
                --output text 2>/dev/null || echo "")
            
            TASK_COUNT=$(echo "$TASKS" | wc -w)
            echo "  Total running tasks: $TASK_COUNT"
            echo ""
        done
    fi
}

# 4. RDS Instances
check_rds_instances() {
    print_section "RDS DATABASE INSTANCES"
    
    INSTANCES=$(aws rds describe-db-instances \
        --region $REGION \
        --query 'DBInstances[*].[DBInstanceIdentifier,DBInstanceClass,Engine,DBInstanceStatus,AllocatedStorage,MultiAZ]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$INSTANCES" ]; then
        echo "No RDS instances found."
    else
        echo -e "${YELLOW}Instance ID\t\t\tClass\t\tEngine\t\tStatus\t\tStorage\tMulti-AZ${NC}"
        echo "$INSTANCES" | while IFS=$'\t' read -r id class engine status storage multiaz; do
            printf "%-30s\t%-15s\t%-10s\t%-15s\t%sGB\t%s\n" "$id" "$class" "$engine" "$status" "$storage" "$multiaz"
        done
        
        TOTAL=$(echo "$INSTANCES" | wc -l)
        echo ""
        echo "Total RDS instances: $TOTAL"
    fi
    
    # Check Aurora Serverless
    CLUSTERS=$(aws rds describe-db-clusters \
        --region $REGION \
        --query 'DBClusters[?EngineMode==`serverless` || ServerlessV2ScalingConfiguration!=null].[DBClusterIdentifier,Engine,Status,ServerlessV2ScalingConfiguration.MinCapacity,ServerlessV2ScalingConfiguration.MaxCapacity]' \
        --output text 2>/dev/null || echo "")
    
    if [ ! -z "$CLUSTERS" ]; then
        echo ""
        echo -e "${GREEN}Aurora Serverless Clusters:${NC}"
        echo "$CLUSTERS"
    fi
}

# 5. ElastiCache Clusters
check_elasticache() {
    print_section "ELASTICACHE CLUSTERS"
    
    # Redis clusters
    REDIS=$(aws elasticache describe-cache-clusters \
        --region $REGION \
        --query 'CacheClusters[*].[CacheClusterId,CacheNodeType,Engine,CacheClusterStatus,NumCacheNodes]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$REDIS" ]; then
        echo "No ElastiCache clusters found."
    else
        echo -e "${YELLOW}Cluster ID\t\t\tNode Type\tEngine\tStatus\t\tNodes${NC}"
        echo "$REDIS" | while IFS=$'\t' read -r id type engine status nodes; do
            printf "%-30s\t%-15s\t%-10s\t%-15s\t%s\n" "$id" "$type" "$engine" "$status" "$nodes"
        done
        
        TOTAL=$(echo "$REDIS" | wc -l)
        echo ""
        echo "Total ElastiCache clusters: $TOTAL"
    fi
}

# 6. Auto Scaling Groups
check_auto_scaling() {
    print_section "AUTO SCALING GROUPS"
    
    ASGS=$(aws autoscaling describe-auto-scaling-groups \
        --region $REGION \
        --query 'AutoScalingGroups[*].[AutoScalingGroupName,MinSize,MaxSize,DesiredCapacity,Instances[*].InstanceId|length(@)]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$ASGS" ]; then
        echo "No Auto Scaling Groups found."
    else
        echo -e "${YELLOW}ASG Name\t\t\tMin\tMax\tDesired\tCurrent${NC}"
        echo "$ASGS" | while IFS=$'\t' read -r name min max desired current; do
            printf "%-30s\t%s\t%s\t%s\t%s\n" "$name" "$min" "$max" "$desired" "$current"
        done
        
        TOTAL=$(echo "$ASGS" | wc -l)
        echo ""
        echo "Total Auto Scaling Groups: $TOTAL"
    fi
}

# 7. EKS Clusters
check_eks_clusters() {
    print_section "EKS KUBERNETES CLUSTERS"
    
    CLUSTERS=$(aws eks list-clusters --region $REGION --query 'clusters[*]' --output text 2>/dev/null || echo "")
    
    if [ -z "$CLUSTERS" ]; then
        echo "No EKS clusters found."
    else
        for CLUSTER in $CLUSTERS; do
            echo -e "${GREEN}Cluster: $CLUSTER${NC}"
            
            # Get cluster details
            DETAILS=$(aws eks describe-cluster \
                --name $CLUSTER \
                --region $REGION \
                --query 'cluster.[status,version,platformVersion]' \
                --output text 2>/dev/null || echo "unknown unknown unknown")
            
            echo "  Status: $(echo $DETAILS | awk '{print $1}')"
            echo "  K8s Version: $(echo $DETAILS | awk '{print $2}')"
            
            # Get node groups
            NODEGROUPS=$(aws eks list-nodegroups \
                --cluster-name $CLUSTER \
                --region $REGION \
                --query 'nodegroups[*]' \
                --output text 2>/dev/null || echo "")
            
            if [ ! -z "$NODEGROUPS" ]; then
                for NODEGROUP in $NODEGROUPS; do
                    NG_DETAILS=$(aws eks describe-nodegroup \
                        --cluster-name $CLUSTER \
                        --nodegroup-name $NODEGROUP \
                        --region $REGION \
                        --query 'nodegroup.[status,scalingConfig.desiredSize,scalingConfig.minSize,scalingConfig.maxSize]' \
                        --output text 2>/dev/null || echo "unknown 0 0 0")
                    
                    echo "  Node Group: $NODEGROUP - Desired: $(echo $NG_DETAILS | awk '{print $2}'), Min: $(echo $NG_DETAILS | awk '{print $3}'), Max: $(echo $NG_DETAILS | awk '{print $4}')"
                done
            fi
            echo ""
        done
    fi
}

# 8. Elastic Beanstalk
check_elastic_beanstalk() {
    print_section "ELASTIC BEANSTALK ENVIRONMENTS"
    
    ENVIRONMENTS=$(aws elasticbeanstalk describe-environments \
        --region $REGION \
        --query 'Environments[?Status!=`Terminated`].[EnvironmentName,ApplicationName,Status,Health,PlatformArn]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$ENVIRONMENTS" ]; then
        echo "No Elastic Beanstalk environments found."
    else
        echo -e "${YELLOW}Environment\t\tApplication\t\tStatus\t\tHealth${NC}"
        echo "$ENVIRONMENTS" | while IFS=$'\t' read -r env app status health platform; do
            printf "%-20s\t%-20s\t%-15s\t%s\n" "$env" "$app" "$status" "$health"
        done
        
        TOTAL=$(echo "$ENVIRONMENTS" | wc -l)
        echo ""
        echo "Total environments: $TOTAL"
    fi
}

# 9. Batch Compute Environments
check_batch() {
    print_section "AWS BATCH COMPUTE ENVIRONMENTS"
    
    ENVIRONMENTS=$(aws batch describe-compute-environments \
        --region $REGION \
        --query 'computeEnvironments[?state==`ENABLED`].[computeEnvironmentName,type,state,computeResources.type]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$ENVIRONMENTS" ]; then
        echo "No Batch compute environments found."
    else
        echo -e "${YELLOW}Environment Name\t\tType\t\tState\t\tCompute Type${NC}"
        echo "$ENVIRONMENTS"
        
        TOTAL=$(echo "$ENVIRONMENTS" | wc -l)
        echo ""
        echo "Total Batch environments: $TOTAL"
    fi
}

# 10. Lightsail Instances
check_lightsail() {
    print_section "LIGHTSAIL INSTANCES"
    
    INSTANCES=$(aws lightsail get-instances \
        --region $REGION \
        --query 'instances[*].[name,blueprintId,bundleId,state.name,publicIpAddress]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$INSTANCES" ]; then
        echo "No Lightsail instances found."
    else
        echo -e "${YELLOW}Name\t\tBlueprint\t\tBundle\t\tState\t\tPublic IP${NC}"
        echo "$INSTANCES"
        
        TOTAL=$(echo "$INSTANCES" | wc -l)
        echo ""
        echo "Total Lightsail instances: $TOTAL"
    fi
}

# 11. EMR Clusters
check_emr() {
    print_section "EMR CLUSTERS"
    
    CLUSTERS=$(aws emr list-clusters \
        --region $REGION \
        --active \
        --query 'Clusters[*].[Id,Name,Status.State]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$CLUSTERS" ]; then
        echo "No active EMR clusters found."
    else
        echo -e "${YELLOW}Cluster ID\t\t\tName\t\t\tState${NC}"
        echo "$CLUSTERS"
        
        TOTAL=$(echo "$CLUSTERS" | wc -l)
        echo ""
        echo "Total active EMR clusters: $TOTAL"
    fi
}

# 12. AppRunner Services
check_apprunner() {
    print_section "APP RUNNER SERVICES"
    
    SERVICES=$(aws apprunner list-services \
        --region $REGION \
        --query 'ServiceSummaryList[*].[ServiceName,Status,ServiceUrl]' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$SERVICES" ]; then
        echo "No App Runner services found."
    else
        echo -e "${YELLOW}Service Name\t\tStatus\t\tURL${NC}"
        echo "$SERVICES"
        
        TOTAL=$(echo "$SERVICES" | wc -l)
        echo ""
        echo "Total App Runner services: $TOTAL"
    fi
}

# Summary function
print_summary() {
    print_section "SUMMARY"
    
    echo "Compute Resources Overview:"
    echo ""
    
    # Quick counts
    EC2_COUNT=$(aws ec2 describe-instances --region $REGION --query 'Reservations[*].Instances[?State.Name==`running`]' --output json 2>/dev/null | jq 'flatten | length' || echo 0)
    LAMBDA_COUNT=$(aws lambda list-functions --region $REGION --query 'Functions' --output json 2>/dev/null | jq 'length' || echo 0)
    RDS_COUNT=$(aws rds describe-db-instances --region $REGION --query 'DBInstances' --output json 2>/dev/null | jq 'length' || echo 0)
    
    echo "  EC2 Instances (running): $EC2_COUNT"
    echo "  Lambda Functions: $LAMBDA_COUNT"
    echo "  RDS Instances: $RDS_COUNT"
    echo ""
    
    # Cost estimate
    echo -e "${GREEN}Estimated Monthly Costs:${NC}"
    echo "  Note: These are rough estimates. Check AWS Cost Explorer for accurate costs."
    echo ""
    
    # Resource health
    echo -e "${YELLOW}Action Items:${NC}"
    echo "  1. Review stopped EC2 instances for potential termination"
    echo "  2. Check for unused Auto Scaling Groups"
    echo "  3. Review Lambda functions for optimization opportunities"
    echo "  4. Consider Reserved Instances for long-running resources"
}

# Export to file function
export_to_file() {
    FILENAME="aws-compute-inventory-$(date +%Y%m%d-%H%M%S).txt"
    echo "Exporting to $FILENAME..."
    
    {
        echo "AWS Compute Resources Inventory"
        echo "Generated: $(date)"
        echo "Region: $REGION"
        echo "========================================"
        check_ec2_instances
        check_lambda_functions
        check_ecs_services
        check_rds_instances
        check_elasticache
        check_auto_scaling
        check_eks_clusters
        check_elastic_beanstalk
        check_batch
        check_lightsail
        check_emr
        check_apprunner
        print_summary
    } > "$FILENAME"
    
    echo "Report saved to: $FILENAME"
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
    
    # Account info
    ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
    echo "AWS Account: $ACCOUNT_ID"
    echo ""
    
    # Run all checks
    check_ec2_instances
    check_lambda_functions
    check_ecs_services
    check_rds_instances
    check_elasticache
    check_auto_scaling
    check_eks_clusters
    check_elastic_beanstalk
    check_batch
    check_lightsail
    check_emr
    check_apprunner
    
    # Print summary
    print_summary
    
    # Ask if user wants to export
    echo ""
    read -p "Export report to file? (y/n): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        export_to_file
    fi
}

# Run main function
main