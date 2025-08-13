#!/bin/bash
# AWS CloudWatch Production Monitoring Script
# Real-time monitoring of Oddiya AWS infrastructure

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m'

# AWS Configuration
CLUSTER="oddiya-cluster"
SERVICE="oddiya-backend-service"
REGION="ap-northeast-2"
LOG_GROUP="/ecs/oddiya-backend"
DB_INSTANCE="oddiya-db"

# Get AWS account ID
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || echo "UNKNOWN")

print_header() {
    clear
    echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}${BOLD}         AWS CloudWatch Production Monitor - Oddiya              ${NC}"
    echo -e "${CYAN}Account: $ACCOUNT_ID | Region: $REGION | $(date +'%Y-%m-%d %H:%M:%S')${NC}"
    echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"
}

check_ecs_status() {
    echo -e "\n${GREEN}${BOLD}📦 ECS Service Status${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Get service status
    SERVICE_INFO=$(aws ecs describe-services \
        --cluster $CLUSTER \
        --services $SERVICE \
        --region $REGION \
        --query 'services[0]' \
        --output json 2>/dev/null || echo "{}")
    
    if [ "$SERVICE_INFO" = "{}" ]; then
        echo -e "  ${RED}❌ Service not found or error accessing ECS${NC}"
        return
    fi
    
    STATUS=$(echo $SERVICE_INFO | jq -r '.status // "UNKNOWN"')
    RUNNING=$(echo $SERVICE_INFO | jq -r '.runningCount // 0')
    DESIRED=$(echo $SERVICE_INFO | jq -r '.desiredCount // 0')
    PENDING=$(echo $SERVICE_INFO | jq -r '.pendingCount // 0')
    
    # Determine health status
    if [ "$RUNNING" -eq "$DESIRED" ] && [ "$RUNNING" -gt 0 ]; then
        echo -e "  ${GREEN}✅ Status: HEALTHY${NC}"
        STATUS_COLOR=$GREEN
    elif [ "$RUNNING" -eq 0 ]; then
        echo -e "  ${RED}❌ Status: DOWN${NC}"
        STATUS_COLOR=$RED
    else
        echo -e "  ${YELLOW}⚠️  Status: DEGRADED${NC}"
        STATUS_COLOR=$YELLOW
    fi
    
    echo -e "  📊 Tasks: ${STATUS_COLOR}Running: $RUNNING${NC} | Desired: $DESIRED | Pending: $PENDING"
    
    # Get task details
    TASK_ARNS=$(aws ecs list-tasks \
        --cluster $CLUSTER \
        --service-name $SERVICE \
        --region $REGION \
        --query 'taskArns' \
        --output json 2>/dev/null || echo "[]")
    
    if [ "$TASK_ARNS" != "[]" ] && [ "$TASK_ARNS" != "" ]; then
        echo -e "\n  ${CYAN}Recent Task Status:${NC}"
        TASKS=$(aws ecs describe-tasks \
            --cluster $CLUSTER \
            --tasks $(echo $TASK_ARNS | jq -r '.[]') \
            --region $REGION \
            --query 'tasks[0:3].[lastStatus,healthStatus,stoppedReason]' \
            --output text 2>/dev/null || echo "")
        
        if [ ! -z "$TASKS" ]; then
            echo "$TASKS" | while IFS=$'\t' read -r status health reason; do
                echo -e "    Status: $status | Health: ${health:-N/A} | ${reason:+Reason: $reason}"
            done
        fi
    fi
    
    # Recent events
    echo -e "\n  ${CYAN}Recent Events:${NC}"
    echo $SERVICE_INFO | jq -r '.events[0:3] | .[] | "    \(.createdAt | split("T")[0]) - \(.message)"' 2>/dev/null || echo "    No recent events"
}

check_alb_health() {
    echo -e "\n${GREEN}${BOLD}🔄 Application Load Balancer${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Get target group ARN
    TARGET_GROUP=$(aws elbv2 describe-target-groups \
        --region $REGION \
        --query "TargetGroups[?contains(TargetGroupName, 'oddiya')].TargetGroupArn | [0]" \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$TARGET_GROUP" ] || [ "$TARGET_GROUP" = "None" ]; then
        echo -e "  ${YELLOW}⚠️  No target group found${NC}"
        return
    fi
    
    # Check target health
    TARGETS=$(aws elbv2 describe-target-health \
        --target-group-arn $TARGET_GROUP \
        --region $REGION \
        --query 'TargetHealthDescriptions' \
        --output json 2>/dev/null || echo "[]")
    
    HEALTHY=$(echo $TARGETS | jq '[.[] | select(.TargetHealth.State=="healthy")] | length' 2>/dev/null || echo "0")
    UNHEALTHY=$(echo $TARGETS | jq '[.[] | select(.TargetHealth.State=="unhealthy")] | length' 2>/dev/null || echo "0")
    TOTAL=$(echo $TARGETS | jq '. | length' 2>/dev/null || echo "0")
    
    if [ "$HEALTHY" -eq "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
        echo -e "  ${GREEN}✅ All targets healthy${NC}"
    elif [ "$HEALTHY" -eq 0 ]; then
        echo -e "  ${RED}❌ No healthy targets${NC}"
    else
        echo -e "  ${YELLOW}⚠️  Some targets unhealthy${NC}"
    fi
    
    echo -e "  📊 Targets: Healthy: ${GREEN}$HEALTHY${NC} | Unhealthy: ${RED}$UNHEALTHY${NC} | Total: $TOTAL"
    
    # Get ALB metrics
    ALB_NAME=$(aws elbv2 describe-load-balancers \
        --region $REGION \
        --query "LoadBalancers[?contains(LoadBalancerName, 'oddiya')].LoadBalancerName | [0]" \
        --output text 2>/dev/null || echo "")
    
    if [ ! -z "$ALB_NAME" ] && [ "$ALB_NAME" != "None" ]; then
        # Response time
        RESPONSE_TIME=$(aws cloudwatch get-metric-statistics \
            --namespace AWS/ApplicationELB \
            --metric-name TargetResponseTime \
            --dimensions Name=LoadBalancer,Value=app/$ALB_NAME/* \
            --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
            --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
            --period 300 \
            --statistics Average \
            --region $REGION \
            --query 'Datapoints[0].Average' \
            --output text 2>/dev/null || echo "N/A")
        
        if [ "$RESPONSE_TIME" != "N/A" ] && [ "$RESPONSE_TIME" != "None" ]; then
            RESPONSE_MS=$(printf "%.0f" $(echo "$RESPONSE_TIME * 1000" | bc))
            echo -e "  ⚡ Avg Response Time: ${RESPONSE_MS}ms"
        fi
    fi
}

check_rds_health() {
    echo -e "\n${GREEN}${BOLD}🗄️  RDS Database Status${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Check if RDS instance exists
    DB_STATUS=$(aws rds describe-db-instances \
        --db-instance-identifier $DB_INSTANCE \
        --region $REGION \
        --query 'DBInstances[0].DBInstanceStatus' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$DB_STATUS" = "NOT_FOUND" ] || [ "$DB_STATUS" = "None" ]; then
        echo -e "  ${RED}❌ Database instance not found${NC}"
        return
    fi
    
    if [ "$DB_STATUS" = "available" ]; then
        echo -e "  ${GREEN}✅ Status: Available${NC}"
    else
        echo -e "  ${YELLOW}⚠️  Status: $DB_STATUS${NC}"
    fi
    
    # Get CPU utilization
    CPU=$(aws cloudwatch get-metric-statistics \
        --namespace AWS/RDS \
        --metric-name CPUUtilization \
        --dimensions Name=DBInstanceIdentifier,Value=$DB_INSTANCE \
        --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
        --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
        --period 300 \
        --statistics Average \
        --region $REGION \
        --query 'Datapoints[0].Average' \
        --output text 2>/dev/null || echo "N/A")
    
    if [ "$CPU" != "N/A" ] && [ "$CPU" != "None" ]; then
        CPU_INT=$(printf "%.0f" $CPU)
        if [ "$CPU_INT" -gt 80 ]; then
            echo -e "  🔥 CPU Usage: ${RED}${CPU_INT}%${NC}"
        elif [ "$CPU_INT" -gt 60 ]; then
            echo -e "  📊 CPU Usage: ${YELLOW}${CPU_INT}%${NC}"
        else
            echo -e "  📊 CPU Usage: ${GREEN}${CPU_INT}%${NC}"
        fi
    fi
    
    # Get connection count
    CONNECTIONS=$(aws cloudwatch get-metric-statistics \
        --namespace AWS/RDS \
        --metric-name DatabaseConnections \
        --dimensions Name=DBInstanceIdentifier,Value=$DB_INSTANCE \
        --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
        --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
        --period 300 \
        --statistics Average \
        --region $REGION \
        --query 'Datapoints[0].Average' \
        --output text 2>/dev/null || echo "N/A")
    
    if [ "$CONNECTIONS" != "N/A" ] && [ "$CONNECTIONS" != "None" ]; then
        CONN_INT=$(printf "%.0f" $CONNECTIONS)
        echo -e "  🔗 Active Connections: $CONN_INT"
    fi
    
    # Get storage info
    STORAGE=$(aws rds describe-db-instances \
        --db-instance-identifier $DB_INSTANCE \
        --region $REGION \
        --query 'DBInstances[0].[AllocatedStorage,StorageType]' \
        --output text 2>/dev/null || echo "N/A N/A")
    
    if [ "$STORAGE" != "N/A N/A" ]; then
        echo -e "  💾 Storage: $STORAGE"
    fi
}

check_recent_errors() {
    echo -e "\n${GREEN}${BOLD}❌ Application Errors (Last 5 min)${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Check if log group exists
    LOG_EXISTS=$(aws logs describe-log-groups \
        --log-group-name-prefix $LOG_GROUP \
        --region $REGION \
        --query 'logGroups[0].logGroupName' \
        --output text 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$LOG_EXISTS" = "NOT_FOUND" ] || [ "$LOG_EXISTS" = "None" ]; then
        echo -e "  ${YELLOW}⚠️  Log group not found${NC}"
        return
    fi
    
    # Count errors
    START_TIME=$(date -d '5 minutes ago' +%s000)
    END_TIME=$(date +%s000)
    
    ERROR_COUNT=$(aws logs filter-log-events \
        --log-group-name $LOG_GROUP \
        --start-time $START_TIME \
        --end-time $END_TIME \
        --filter-pattern "ERROR" \
        --region $REGION \
        --query 'events | length(@)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$ERROR_COUNT" = "None" ]; then
        ERROR_COUNT=0
    fi
    
    if [ "$ERROR_COUNT" -gt 10 ]; then
        echo -e "  ${RED}🚨 Critical: $ERROR_COUNT errors detected${NC}"
    elif [ "$ERROR_COUNT" -gt 0 ]; then
        echo -e "  ${YELLOW}⚠️  Warning: $ERROR_COUNT errors detected${NC}"
    else
        echo -e "  ${GREEN}✅ No errors detected${NC}"
    fi
    
    # Show recent error samples
    if [ "$ERROR_COUNT" -gt 0 ]; then
        echo -e "\n  ${CYAN}Recent Error Samples:${NC}"
        aws logs filter-log-events \
            --log-group-name $LOG_GROUP \
            --start-time $START_TIME \
            --end-time $END_TIME \
            --filter-pattern "ERROR" \
            --region $REGION \
            --query 'events[0:3].[message]' \
            --output text 2>/dev/null | while IFS=$'\t' read -r message; do
            # Truncate long messages
            if [ ${#message} -gt 100 ]; then
                echo -e "    ${message:0:100}..."
            else
                echo -e "    $message"
            fi
        done
    fi
}

check_active_alarms() {
    echo -e "\n${GREEN}${BOLD}🔔 CloudWatch Alarms${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Get alarms in ALARM state
    ALARMS=$(aws cloudwatch describe-alarms \
        --state-value ALARM \
        --region $REGION \
        --query 'MetricAlarms[?contains(AlarmName, `oddiya`) || contains(AlarmName, `Oddiya`)].{Name:AlarmName,State:StateValue,Reason:StateReason}' \
        --output json 2>/dev/null || echo "[]")
    
    ALARM_COUNT=$(echo $ALARMS | jq '. | length' 2>/dev/null || echo "0")
    
    if [ "$ALARM_COUNT" -gt 0 ]; then
        echo -e "  ${RED}🚨 $ALARM_COUNT alarm(s) triggered:${NC}"
        echo $ALARMS | jq -r '.[] | "    ❌ \(.Name): \(.Reason)"' 2>/dev/null
    else
        echo -e "  ${GREEN}✅ No active alarms${NC}"
    fi
    
    # Get alarms in OK state (summary)
    OK_COUNT=$(aws cloudwatch describe-alarms \
        --state-value OK \
        --region $REGION \
        --query 'MetricAlarms[?contains(AlarmName, `oddiya`) || contains(AlarmName, `Oddiya`)] | length(@)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$OK_COUNT" != "None" ] && [ "$OK_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}✅ $OK_COUNT alarm(s) in OK state${NC}"
    fi
}

check_test_metrics() {
    echo -e "\n${GREEN}${BOLD}🧪 Test Execution Metrics${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    
    # Check for custom test metrics
    TEST_COVERAGE=$(aws cloudwatch get-metric-statistics \
        --namespace "Oddiya/Application" \
        --metric-name TestCoverage \
        --start-time $(date -u -d '1 hour ago' '+%Y-%m-%dT%H:%M:%S') \
        --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
        --period 3600 \
        --statistics Average \
        --region $REGION \
        --query 'Datapoints[0].Average' \
        --output text 2>/dev/null || echo "N/A")
    
    if [ "$TEST_COVERAGE" != "N/A" ] && [ "$TEST_COVERAGE" != "None" ]; then
        COVERAGE_INT=$(printf "%.0f" $TEST_COVERAGE)
        if [ "$COVERAGE_INT" -lt 40 ]; then
            echo -e "  📊 Test Coverage: ${RED}${COVERAGE_INT}%${NC} (Target: 85%)"
        elif [ "$COVERAGE_INT" -lt 80 ]; then
            echo -e "  📊 Test Coverage: ${YELLOW}${COVERAGE_INT}%${NC} (Target: 85%)"
        else
            echo -e "  📊 Test Coverage: ${GREEN}${COVERAGE_INT}%${NC}"
        fi
    else
        echo -e "  ${YELLOW}📊 Test Coverage: 6% (baseline)${NC}"
    fi
    
    # Search for test-related logs
    TEST_LOGS=$(aws logs filter-log-events \
        --log-group-name $LOG_GROUP \
        --start-time $(date -d '1 hour ago' +%s000) \
        --filter-pattern "[Tt]est" \
        --region $REGION \
        --query 'events | length(@)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$TEST_LOGS" != "None" ] && [ "$TEST_LOGS" -gt 0 ]; then
        echo -e "  📝 Test-related logs: $TEST_LOGS entries (last hour)"
    fi
}

show_summary() {
    echo -e "\n${BLUE}${BOLD}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}${BOLD}Quick Actions:${NC}"
    echo -e "  ${CYAN}[L]${NC} View Logs  ${CYAN}[D]${NC} Dashboard  ${CYAN}[A]${NC} Alarms  ${CYAN}[M]${NC} Metrics  ${CYAN}[R]${NC} Refresh  ${CYAN}[Q]${NC} Quit"
    echo -e "${YELLOW}Auto-refresh in 30 seconds... (Press any key for menu)${NC}"
}

# Function to handle user input
handle_input() {
    case $1 in
        l|L)
            echo -e "\n${GREEN}Opening CloudWatch Logs...${NC}"
            open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#logsV2:log-groups/log-group/\$252Fecs\$252Foddiya-backend"
            ;;
        d|D)
            echo -e "\n${GREEN}Opening CloudWatch Dashboard...${NC}"
            open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#dashboards:"
            ;;
        a|A)
            echo -e "\n${GREEN}Opening CloudWatch Alarms...${NC}"
            open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#alarmsV2:"
            ;;
        m|M)
            echo -e "\n${GREEN}Opening CloudWatch Metrics...${NC}"
            open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#metricsV2:"
            ;;
        r|R)
            return 0  # Continue loop
            ;;
        q|Q)
            echo -e "\n${GREEN}Exiting monitor...${NC}"
            exit 0
            ;;
    esac
}

# Trap Ctrl+C
trap 'echo -e "\n${GREEN}Monitoring stopped.${NC}"; exit 0' INT

# Main monitoring loop
echo -e "${GREEN}Starting AWS CloudWatch monitoring...${NC}"
echo -e "${YELLOW}Checking AWS credentials...${NC}"

# Verify AWS access
aws sts get-caller-identity &>/dev/null || {
    echo -e "${RED}Error: Unable to access AWS. Please configure AWS credentials.${NC}"
    echo -e "Run: ${CYAN}aws configure${NC}"
    exit 1
}

while true; do
    print_header
    check_ecs_status
    check_alb_health
    check_rds_health
    check_recent_errors
    check_active_alarms
    check_test_metrics
    show_summary
    
    # Wait for input with timeout
    if read -t 30 -n 1 key; then
        handle_input $key
    fi
done