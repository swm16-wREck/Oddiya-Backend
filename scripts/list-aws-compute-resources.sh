#!/bin/bash
# List all AWS compute resources for Oddiya project

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

REGION="ap-northeast-2"

echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}${BOLD}         AWS Resource Inventory - Oddiya Project                 ${NC}"
echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"

# Check AWS credentials
aws sts get-caller-identity &>/dev/null || {
    echo -e "${RED}Error: AWS credentials not configured${NC}"
    exit 1
}

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo -e "${GREEN}AWS Account: $ACCOUNT_ID | Region: $REGION${NC}\n"

# Function to check and list resources
check_resource() {
    local resource_type=$1
    local command=$2
    local filter=$3
    
    echo -e "${CYAN}Checking $resource_type...${NC}"
    eval "$command" 2>/dev/null | while read -r line; do
        if [ ! -z "$line" ] && [ "$line" != "None" ]; then
            if [ -z "$filter" ] || [[ "$line" == *"$filter"* ]]; then
                echo "  ✓ $line"
            fi
        fi
    done || echo "  - None found"
    echo ""
}

echo -e "${GREEN}${BOLD}1. ECS Resources${NC}"
echo "─────────────────────────────"

# List ECS Clusters
echo -e "${CYAN}ECS Clusters:${NC}"
aws ecs list-clusters --region $REGION --query 'clusterArns[]' --output text 2>/dev/null | while read -r cluster_arn; do
    if [ ! -z "$cluster_arn" ]; then
        cluster_name=$(echo $cluster_arn | awk -F'/' '{print $NF}')
        echo "  🐳 Cluster: $cluster_name"
        
        # List services in cluster
        aws ecs list-services --cluster $cluster_name --region $REGION --query 'serviceArns[]' --output text 2>/dev/null | while read -r service_arn; do
            if [ ! -z "$service_arn" ]; then
                service_name=$(echo $service_arn | awk -F'/' '{print $NF}')
                
                # Get service details
                SERVICE_INFO=$(aws ecs describe-services \
                    --cluster $cluster_name \
                    --services $service_name \
                    --region $REGION \
                    --query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount,TaskDef:taskDefinition}' \
                    --output json 2>/dev/null || echo "{}")
                
                STATUS=$(echo $SERVICE_INFO | jq -r '.Status // "unknown"')
                RUNNING=$(echo $SERVICE_INFO | jq -r '.Running // 0')
                DESIRED=$(echo $SERVICE_INFO | jq -r '.Desired // 0')
                TASK_DEF=$(echo $SERVICE_INFO | jq -r '.TaskDef // "unknown"' | awk -F'/' '{print $NF}')
                
                if [ "$RUNNING" -eq 0 ]; then
                    echo -e "    ${RED}└─ Service: $service_name (Status: $STATUS, Tasks: $RUNNING/$DESIRED) - DOWN${NC}"
                else
                    echo -e "    ${GREEN}└─ Service: $service_name (Status: $STATUS, Tasks: $RUNNING/$DESIRED)${NC}"
                fi
                echo "       Task Definition: $TASK_DEF"
            fi
        done
    fi
done || echo "  - No ECS clusters found"

echo -e "\n${GREEN}${BOLD}2. Container Registry (ECR)${NC}"
echo "─────────────────────────────"
aws ecr describe-repositories --region $REGION --query 'repositories[].[repositoryName,repositoryUri]' --output text 2>/dev/null | while IFS=$'\t' read -r name uri; do
    if [ ! -z "$name" ]; then
        # Get image count
        IMAGE_COUNT=$(aws ecr list-images --repository-name $name --region $REGION --query 'imageIds | length(@)' --output text 2>/dev/null || echo "0")
        echo "  📦 Repository: $name"
        echo "     URI: $uri"
        echo "     Images: $IMAGE_COUNT"
    fi
done || echo "  - No ECR repositories found"

echo -e "\n${GREEN}${BOLD}3. Load Balancers${NC}"
echo "─────────────────────────────"
aws elbv2 describe-load-balancers --region $REGION --query 'LoadBalancers[].[LoadBalancerName,State.Code,DNSName]' --output text 2>/dev/null | while IFS=$'\t' read -r name state dns; do
    if [[ "$name" == *"oddiya"* ]]; then
        if [ "$state" = "active" ]; then
            echo -e "  ${GREEN}⚖️  ALB: $name (State: $state)${NC}"
        else
            echo -e "  ${YELLOW}⚖️  ALB: $name (State: $state)${NC}"
        fi
        echo "     DNS: $dns"
        
        # Get target groups
        ALB_ARN=$(aws elbv2 describe-load-balancers --names $name --region $REGION --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null)
        if [ ! -z "$ALB_ARN" ] && [ "$ALB_ARN" != "None" ]; then
            aws elbv2 describe-target-groups --load-balancer-arn $ALB_ARN --region $REGION --query 'TargetGroups[].[TargetGroupName,HealthCheckPath]' --output text 2>/dev/null | while IFS=$'\t' read -r tg_name health_path; do
                if [ ! -z "$tg_name" ]; then
                    echo "     └─ Target Group: $tg_name (Health: $health_path)"
                fi
            done
        fi
    fi
done || echo "  - No load balancers found"

echo -e "\n${GREEN}${BOLD}4. RDS Databases${NC}"
echo "─────────────────────────────"
aws rds describe-db-instances --region $REGION --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus,Engine,AllocatedStorage]' --output text 2>/dev/null | while IFS=$'\t' read -r id status engine storage; do
    if [[ "$id" == *"oddiya"* ]]; then
        if [ "$status" = "available" ]; then
            echo -e "  ${GREEN}🗄️  RDS: $id (Status: $status)${NC}"
        else
            echo -e "  ${YELLOW}🗄️  RDS: $id (Status: $status)${NC}"
        fi
        echo "     Engine: $engine"
        echo "     Storage: ${storage}GB"
    fi
done || echo "  - No RDS instances found"

echo -e "\n${GREEN}${BOLD}5. S3 Buckets${NC}"
echo "─────────────────────────────"
aws s3api list-buckets --query 'Buckets[].[Name,CreationDate]' --output text | while IFS=$'\t' read -r name date; do
    if [[ "$name" == *"oddiya"* ]]; then
        # Get bucket size
        SIZE=$(aws s3 ls s3://$name --recursive --summarize 2>/dev/null | grep "Total Size" | awk '{print $3}' || echo "0")
        if [ -z "$SIZE" ] || [ "$SIZE" = "0" ]; then
            SIZE="Empty"
        else
            SIZE=$(numfmt --to=iec-i --suffix=B $SIZE 2>/dev/null || echo "${SIZE} bytes")
        fi
        echo "  🪣 Bucket: $name"
        echo "     Created: $date"
        echo "     Size: $SIZE"
    fi
done || echo "  - No S3 buckets found"

echo -e "\n${GREEN}${BOLD}6. DynamoDB Tables${NC}"
echo "─────────────────────────────"
aws dynamodb list-tables --region $REGION --query 'TableNames[]' --output text 2>/dev/null | while read -r table; do
    if [[ "$table" == oddiya* ]]; then
        # Get table status
        STATUS=$(aws dynamodb describe-table --table-name $table --region $REGION --query 'Table.TableStatus' --output text 2>/dev/null || echo "UNKNOWN")
        ITEMS=$(aws dynamodb describe-table --table-name $table --region $REGION --query 'Table.ItemCount' --output text 2>/dev/null || echo "0")
        echo "  📊 Table: $table"
        echo "     Status: $STATUS"
        echo "     Items: $ITEMS"
    fi
done || echo "  - No DynamoDB tables found"

echo -e "\n${GREEN}${BOLD}7. SQS Queues${NC}"
echo "─────────────────────────────"
aws sqs list-queues --region $REGION --query 'QueueUrls[]' --output text 2>/dev/null | while read -r queue_url; do
    if [[ "$queue_url" == *"oddiya"* ]]; then
        queue_name=$(echo $queue_url | awk -F'/' '{print $NF}')
        # Get queue attributes
        MESSAGES=$(aws sqs get-queue-attributes --queue-url $queue_url --attribute-names ApproximateNumberOfMessages --region $REGION --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null || echo "0")
        echo "  📬 Queue: $queue_name"
        echo "     Messages: $MESSAGES"
    fi
done || echo "  - No SQS queues found"

echo -e "\n${GREEN}${BOLD}8. CloudWatch Alarms${NC}"
echo "─────────────────────────────"
aws cloudwatch describe-alarms --region $REGION --query 'MetricAlarms[].[AlarmName,StateValue]' --output text 2>/dev/null | while IFS=$'\t' read -r name state; do
    if [[ "$name" == *oddiya* ]] || [[ "$name" == *Oddiya* ]]; then
        if [ "$state" = "OK" ]; then
            echo -e "  ${GREEN}🔔 Alarm: $name (State: $state)${NC}"
        elif [ "$state" = "ALARM" ]; then
            echo -e "  ${RED}🚨 Alarm: $name (State: $state)${NC}"
        else
            echo -e "  ${YELLOW}🔔 Alarm: $name (State: $state)${NC}"
        fi
    fi
done || echo "  - No CloudWatch alarms found"

echo -e "\n${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}Resource Scan Complete!${NC}"
echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"

# Summary
echo -e "\n${CYAN}${BOLD}Summary of Issues Found:${NC}"
echo "─────────────────────────────"

# Check for critical issues
ISSUES_FOUND=false

# Check ECS tasks
ECS_STATUS=$(aws ecs describe-services --cluster oddiya-cluster --services oddiya-backend-service --region $REGION --query 'services[0].runningCount' --output text 2>/dev/null || echo "0")
if [ "$ECS_STATUS" = "0" ] || [ "$ECS_STATUS" = "None" ]; then
    echo -e "${RED}❌ ECS Service is DOWN (0 running tasks)${NC}"
    ISSUES_FOUND=true
fi

# Check for unused resources
echo -e "\n${YELLOW}Potential unused resources to investigate:${NC}"
echo "  - Check if DynamoDB tables are needed (app uses PostgreSQL)"
echo "  - Verify if all S3 buckets are in use"
echo "  - Review CloudWatch alarms configuration"

if [ "$ISSUES_FOUND" = false ]; then
    echo -e "${GREEN}✅ No critical issues found${NC}"
fi

echo -e "\n${CYAN}Next Steps:${NC}"
echo "1. Fix ECS deployment configuration"
echo "2. Remove unused DynamoDB tables if not needed"
echo "3. Clean up any orphaned resources"
echo "4. Run: ./scripts/analyze-and-cleanup-aws.sh --execute (to remove unused resources)"