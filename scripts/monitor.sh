#!/bin/bash
# Unified Monitoring Script for Oddiya
# Consolidates AWS production monitoring, test monitoring, and CloudWatch alarm management
# Usage: ./scripts/monitor.sh [--aws|--tests|--cloudwatch|--all] [options]

# Source common utilities
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
source "$SCRIPT_DIR/common.sh"

# Initialize script
init_script "Unified Monitor" true

# ═══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION AND CONSTANTS
# ═══════════════════════════════════════════════════════════════════════════════

# Monitoring modes
MODE=""
MONITORING_INTERVAL=${MONITOR_INTERVAL:-30}  # Default 30 seconds
AUTO_REFRESH=${AUTO_REFRESH:-true}

# Email for CloudWatch alerts
ALERT_EMAIL="${ALERT_EMAIL:-team@oddiya.com}"

# Test monitoring configuration
TEST_REPORT_DIR="build/reports/test-monitoring"
TIMESTAMP_FORMAT="%Y-%m-%d %H:%M:%S"

# Create directories
mkdir -p "$TEST_REPORT_DIR"

# ═══════════════════════════════════════════════════════════════════════════════
# USAGE AND HELP FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

show_usage() {
    cat << EOF
${BOLD}${BLUE}Unified Monitoring Script for Oddiya${NC}

${BOLD}Usage:${NC}
  $0 [MODE] [OPTIONS]

${BOLD}Modes:${NC}
  --aws          Monitor AWS infrastructure (ECS, RDS, ALB, CloudWatch)
  --tests        Monitor test execution, coverage, and security
  --cloudwatch   Setup and manage CloudWatch alarms
  --all          Monitor all systems (default)

${BOLD}Options:${NC}
  --interval N   Set refresh interval in seconds (default: 30)
  --email EMAIL  Set email for CloudWatch alerts (default: team@oddiya.com)
  --no-refresh   Disable auto-refresh (single run)
  --help         Show this help message

${BOLD}Examples:${NC}
  $0 --aws                    # Monitor AWS infrastructure only
  $0 --tests --interval 10    # Monitor tests with 10s refresh
  $0 --cloudwatch --email ops@example.com  # Setup alarms with custom email
  $0 --all --no-refresh       # Single snapshot of all systems

${BOLD}Interactive Commands (during monitoring):${NC}
  AWS Mode:      [L]ogs [D]ashboard [A]larms [M]etrics [R]efresh [Q]uit
  Tests Mode:    [T]ests [C]overage [S]ecurity [M]utation [P]erformance [Q]uit
  All Mode:      [1]AWS [2]Tests [3]Setup [R]efresh [Q]uit

EOF
}

# ═══════════════════════════════════════════════════════════════════════════════
# AWS MONITORING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

check_ecs_status() {
    print_section "📦 ECS Service Status"
    
    # Get service status
    local service_info=$(safe_aws_command \
        "aws ecs describe-services \
            --cluster $ECS_CLUSTER \
            --services $ECS_SERVICE \
            --region $REGION \
            --query 'services[0]' \
            --output json" 2>/dev/null || echo "{}")
    
    if [ "$service_info" = "{}" ]; then
        log_error "Service not found or error accessing ECS"
        return
    fi
    
    local status=$(echo $service_info | jq -r '.status // "UNKNOWN"')
    local running=$(echo $service_info | jq -r '.runningCount // 0')
    local desired=$(echo $service_info | jq -r '.desiredCount // 0')
    local pending=$(echo $service_info | jq -r '.pendingCount // 0')
    
    # Determine health status
    if [ "$running" -eq "$desired" ] && [ "$running" -gt 0 ]; then
        log_success "Status: HEALTHY"
        local status_color=$GREEN
    elif [ "$running" -eq 0 ]; then
        log_error "Status: DOWN"
        local status_color=$RED
    else
        log_warning "Status: DEGRADED"
        local status_color=$YELLOW
    fi
    
    echo -e "  📊 Tasks: ${status_color}Running: $running${NC} | Desired: $desired | Pending: $pending"
    
    # Get task details
    local task_arns=$(safe_aws_command \
        "aws ecs list-tasks \
            --cluster $ECS_CLUSTER \
            --service-name $ECS_SERVICE \
            --region $REGION \
            --query 'taskArns' \
            --output json" || echo "[]")
    
    if [ "$task_arns" != "[]" ] && [ "$task_arns" != "" ]; then
        echo -e "\n  ${CYAN}Recent Task Status:${NC}"
        local tasks=$(safe_aws_command \
            "aws ecs describe-tasks \
                --cluster $ECS_CLUSTER \
                --tasks $(echo $task_arns | jq -r '.[]') \
                --region $REGION \
                --query 'tasks[0:3].[lastStatus,healthStatus,stoppedReason]' \
                --output text" || echo "")
        
        if [ ! -z "$tasks" ]; then
            echo "$tasks" | while IFS=$'\t' read -r task_status health reason; do
                echo -e "    Status: $task_status | Health: ${health:-N/A} | ${reason:+Reason: $reason}"
            done
        fi
    fi
    
    # Recent events
    echo -e "\n  ${CYAN}Recent Events:${NC}"
    echo $service_info | jq -r '.events[0:3] | .[] | "    \(.createdAt | split("T")[0]) - \(.message)"' 2>/dev/null || echo "    No recent events"
}

check_alb_health() {
    print_section "🔄 Application Load Balancer"
    
    # Get target group ARN
    local target_group=$(safe_aws_command \
        "aws elbv2 describe-target-groups \
            --region $REGION \
            --query \"TargetGroups[?contains(TargetGroupName, 'oddiya')].TargetGroupArn | [0]\" \
            --output text" || echo "")
    
    if [ -z "$target_group" ] || [ "$target_group" = "None" ]; then
        log_warning "No target group found"
        return
    fi
    
    # Check target health
    local targets=$(safe_aws_command \
        "aws elbv2 describe-target-health \
            --target-group-arn $target_group \
            --region $REGION \
            --query 'TargetHealthDescriptions' \
            --output json" || echo "[]")
    
    local healthy=$(echo $targets | jq '[.[] | select(.TargetHealth.State=="healthy")] | length' 2>/dev/null || echo "0")
    local unhealthy=$(echo $targets | jq '[.[] | select(.TargetHealth.State=="unhealthy")] | length' 2>/dev/null || echo "0")
    local total=$(echo $targets | jq '. | length' 2>/dev/null || echo "0")
    
    if [ "$healthy" -eq "$total" ] && [ "$total" -gt 0 ]; then
        log_success "All targets healthy"
    elif [ "$healthy" -eq 0 ]; then
        log_error "No healthy targets"
    else
        log_warning "Some targets unhealthy"
    fi
    
    echo -e "  📊 Targets: Healthy: ${GREEN}$healthy${NC} | Unhealthy: ${RED}$unhealthy${NC} | Total: $total"
    
    # Get response time metrics
    local alb_name=$(safe_aws_command \
        "aws elbv2 describe-load-balancers \
            --region $REGION \
            --query \"LoadBalancers[?contains(LoadBalancerName, 'oddiya')].LoadBalancerName | [0]\" \
            --output text" || echo "")
    
    if [ ! -z "$alb_name" ] && [ "$alb_name" != "None" ]; then
        local response_time=$(safe_aws_command \
            "aws cloudwatch get-metric-statistics \
                --namespace AWS/ApplicationELB \
                --metric-name TargetResponseTime \
                --dimensions Name=LoadBalancer,Value=app/$alb_name/* \
                --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
                --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
                --period 300 \
                --statistics Average \
                --region $REGION \
                --query 'Datapoints[0].Average' \
                --output text" || echo "N/A")
        
        if [ "$response_time" != "N/A" ] && [ "$response_time" != "None" ]; then
            local response_ms=$(printf "%.0f" $(echo "$response_time * 1000" | bc))
            echo -e "  ⚡ Avg Response Time: ${response_ms}ms"
        fi
    fi
}

check_rds_health() {
    print_section "🗄️  RDS Database Status"
    
    # Check if RDS instance exists
    local db_status=$(safe_aws_command \
        "aws rds describe-db-instances \
            --db-instance-identifier $RDS_INSTANCE \
            --region $REGION \
            --query 'DBInstances[0].DBInstanceStatus' \
            --output text" || echo "NOT_FOUND")
    
    if [ "$db_status" = "NOT_FOUND" ] || [ "$db_status" = "None" ]; then
        log_error "Database instance not found"
        return
    fi
    
    if [ "$db_status" = "available" ]; then
        log_success "Status: Available"
    else
        log_warning "Status: $db_status"
    fi
    
    # Get CPU utilization
    local cpu=$(safe_aws_command \
        "aws cloudwatch get-metric-statistics \
            --namespace AWS/RDS \
            --metric-name CPUUtilization \
            --dimensions Name=DBInstanceIdentifier,Value=$RDS_INSTANCE \
            --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
            --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
            --period 300 \
            --statistics Average \
            --region $REGION \
            --query 'Datapoints[0].Average' \
            --output text" || echo "N/A")
    
    if [ "$cpu" != "N/A" ] && [ "$cpu" != "None" ]; then
        local cpu_int=$(printf "%.0f" $cpu)
        if [ "$cpu_int" -gt 80 ]; then
            echo -e "  🔥 CPU Usage: ${RED}${cpu_int}%${NC}"
        elif [ "$cpu_int" -gt 60 ]; then
            echo -e "  📊 CPU Usage: ${YELLOW}${cpu_int}%${NC}"
        else
            echo -e "  📊 CPU Usage: ${GREEN}${cpu_int}%${NC}"
        fi
    fi
    
    # Get connection count
    local connections=$(safe_aws_command \
        "aws cloudwatch get-metric-statistics \
            --namespace AWS/RDS \
            --metric-name DatabaseConnections \
            --dimensions Name=DBInstanceIdentifier,Value=$RDS_INSTANCE \
            --start-time $(date -u -d '5 minutes ago' '+%Y-%m-%dT%H:%M:%S') \
            --end-time $(date -u '+%Y-%m-%dT%H:%M:%S') \
            --period 300 \
            --statistics Average \
            --region $REGION \
            --query 'Datapoints[0].Average' \
            --output text" || echo "N/A")
    
    if [ "$connections" != "N/A" ] && [ "$connections" != "None" ]; then
        local conn_int=$(printf "%.0f" $connections)
        echo -e "  🔗 Active Connections: $conn_int"
    fi
}

check_recent_errors() {
    print_section "❌ Application Errors (Last 5 min)"
    
    # Check if log group exists
    local log_exists=$(safe_aws_command \
        "aws logs describe-log-groups \
            --log-group-name-prefix $LOG_GROUP \
            --region $REGION \
            --query 'logGroups[0].logGroupName' \
            --output text" || echo "NOT_FOUND")
    
    if [ "$log_exists" = "NOT_FOUND" ] || [ "$log_exists" = "None" ]; then
        log_warning "Log group not found"
        return
    fi
    
    # Count errors
    local start_time=$(date -d '5 minutes ago' +%s000)
    local end_time=$(date +%s000)
    
    local error_count=$(safe_aws_command \
        "aws logs filter-log-events \
            --log-group-name $LOG_GROUP \
            --start-time $start_time \
            --end-time $end_time \
            --filter-pattern \"ERROR\" \
            --region $REGION \
            --query 'events | length(@)' \
            --output text" || echo "0")
    
    if [ "$error_count" = "None" ]; then
        error_count=0
    fi
    
    if [ "$error_count" -gt 10 ]; then
        echo -e "  ${RED}🚨 Critical: $error_count errors detected${NC}"
    elif [ "$error_count" -gt 0 ]; then
        echo -e "  ${YELLOW}⚠️  Warning: $error_count errors detected${NC}"
    else
        log_success "No errors detected"
    fi
    
    # Show recent error samples if any
    if [ "$error_count" -gt 0 ]; then
        echo -e "\n  ${CYAN}Recent Error Samples:${NC}"
        local errors=$(safe_aws_command \
            "aws logs filter-log-events \
                --log-group-name $LOG_GROUP \
                --start-time $start_time \
                --end-time $end_time \
                --filter-pattern \"ERROR\" \
                --region $REGION \
                --query 'events[0:3].[message]' \
                --output text" || echo "")
        
        if [ ! -z "$errors" ]; then
            echo "$errors" | while IFS=$'\t' read -r message; do
                # Truncate long messages
                if [ ${#message} -gt 100 ]; then
                    echo -e "    ${message:0:100}..."
                else
                    echo -e "    $message"
                fi
            done
        fi
    fi
}

check_active_alarms() {
    print_section "🔔 CloudWatch Alarms"
    
    # Get alarms in ALARM state
    local alarms=$(safe_aws_command \
        "aws cloudwatch describe-alarms \
            --state-value ALARM \
            --region $REGION \
            --query \"MetricAlarms[?contains(AlarmName, \\`oddiya\\`) || contains(AlarmName, \\`Oddiya\\`)].{Name:AlarmName,State:StateValue,Reason:StateReason}\" \
            --output json" || echo "[]")
    
    local alarm_count=$(echo $alarms | jq '. | length' 2>/dev/null || echo "0")
    
    if [ "$alarm_count" -gt 0 ]; then
        echo -e "  ${RED}🚨 $alarm_count alarm(s) triggered:${NC}"
        echo $alarms | jq -r '.[] | "    ❌ \(.Name): \(.Reason)"' 2>/dev/null
    else
        log_success "No active alarms"
    fi
    
    # Get alarms in OK state (summary)
    local ok_count=$(safe_aws_command \
        "aws cloudwatch describe-alarms \
            --state-value OK \
            --region $REGION \
            --query \"MetricAlarms[?contains(AlarmName, \\`oddiya\\`) || contains(AlarmName, \\`Oddiya\\`)] | length(@)\" \
            --output text" || echo "0")
    
    if [ "$ok_count" != "None" ] && [ "$ok_count" -gt 0 ]; then
        echo -e "  ${GREEN}✅ $ok_count alarm(s) in OK state${NC}"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# TEST MONITORING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

# Function to format percentage with color
format_percentage() {
    local value=$1
    local threshold=$2
    local warning_threshold=$3
    
    if (( $(echo "$value >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${GREEN}${value}%${NC}"
    elif (( $(echo "$value >= $warning_threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${YELLOW}${value}%${NC}"
    else
        echo -e "${RED}${value}%${NC}"
    fi
}

get_coverage_metrics() {
    print_section "📊 Coverage Metrics"
    local csv_file="build/reports/jacoco/test/jacocoTestReport.csv"
    
    if [ -f "$csv_file" ]; then
        local lines=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$4; covered+=$5} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        local branches=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$6; covered+=$7} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        local complexity=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{sum+=$8} END {print sum}' || echo "0")
        
        echo -e "  📊 Line Coverage:      $(format_percentage "$lines" 80 60) (Target: 85%)"
        echo -e "  🌳 Branch Coverage:    $(format_percentage "$branches" 75 50) (Target: 80%)"
        echo -e "  🔀 Complexity:         ${complexity}"
    else
        log_warning "No coverage data available"
        echo -e "  ${CYAN}Run: ./gradlew test jacocoTestReport${NC}"
    fi
}

get_test_metrics() {
    print_section "🧪 Test Execution"
    
    if [ -d "build/test-results/test" ]; then
        local total=$(find build/test-results/test -name "*.xml" -exec grep -c '<testcase' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local failures=$(find build/test-results/test -name "*.xml" -exec grep -c '<failure' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local errors=$(find build/test-results/test -name "*.xml" -exec grep -c '<error' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local skipped=$(find build/test-results/test -name "*.xml" -exec grep -c '<skipped' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        
        local passed=$((total - failures - errors - skipped))
        local success_rate=0
        if [ "$total" -gt 0 ]; then
            success_rate=$((passed * 100 / total))
        fi
        
        echo -e "  📝 Total Tests:        ${BOLD}${total}${NC}"
        echo -e "  ✅ Passed:            ${GREEN}${passed}${NC}"
        if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
            echo -e "  ❌ Failed:            ${RED}$((failures + errors))${NC}"
        else
            echo -e "  ❌ Failed:            ${GREEN}0${NC}"
        fi
        if [ "$skipped" -gt 0 ]; then
            echo -e "  ⏭️  Skipped:           ${YELLOW}${skipped}${NC}"
        else
            echo -e "  ⏭️  Skipped:           ${GREEN}0${NC}"
        fi
        echo -e "  📈 Success Rate:       $(format_percentage "$success_rate" 98 95)"
        
        # Get execution time
        local exec_time=$(find build/test-results/test -name "*.xml" -exec grep -o 'time="[^"]*"' {} \; 2>/dev/null | sed 's/time="//' | sed 's/"//' | awk '{sum+=$1} END {printf "%.2f", sum}' || echo "0")
        echo -e "  ⏱️  Execution Time:    ${exec_time}s"
    else
        log_warning "No test results available"
        echo -e "  ${CYAN}Run: ./gradlew test${NC}"
    fi
}

get_security_metrics() {
    print_section "🔒 Security Scan"
    local report="build/reports/dependency-check-report.html"
    
    if [ -f "$report" ]; then
        local critical=$(grep -o "Critical</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local high=$(grep -o "High</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local medium=$(grep -o "Medium</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local low=$(grep -o "Low</td>" "$report" 2>/dev/null | wc -l || echo "0")
        
        echo -n "  🔴 Critical: "
        if [ "$critical" -gt 0 ]; then
            echo -e "${RED}${critical}${NC}"
        else
            echo -e "${GREEN}0${NC}"
        fi
        
        echo -n "  🟠 High:     "
        if [ "$high" -gt 0 ]; then
            echo -e "${YELLOW}${high}${NC}"
        else
            echo -e "${GREEN}0${NC}"
        fi
        
        echo -e "  🟡 Medium:   ${medium}"
        echo -e "  🟢 Low:      ${low}"
        
        if [ "$critical" -gt 0 ]; then
            echo -e "  ${RED}⚠️  Critical vulnerabilities require immediate attention!${NC}"
        fi
    else
        log_warning "No security scan available"
        echo -e "  ${CYAN}Run: ./gradlew dependencyCheckAnalyze${NC}"
    fi
}

get_mutation_metrics() {
    print_section "🧬 Mutation Testing"
    local xml_file=$(find build/reports/pitest -name "mutations.xml" 2>/dev/null | head -1)
    
    if [ -f "$xml_file" ]; then
        local mutation_score=$(grep -o 'mutationScore="[^"]*"' "$xml_file" 2>/dev/null | cut -d'"' -f2 | head -1 || echo "0")
        local total_mutations=$(grep -o '<mutation ' "$xml_file" 2>/dev/null | wc -l || echo "0")
        local killed=$(grep -o 'status="KILLED"' "$xml_file" 2>/dev/null | wc -l || echo "0")
        local survived=$(grep -o 'status="SURVIVED"' "$xml_file" 2>/dev/null | wc -l || echo "0")
        
        echo -e "  🎯 Mutation Score:     $(format_percentage "$mutation_score" 80 60) (Target: 80%)"
        echo -e "  🔢 Total Mutations:    ${total_mutations}"
        echo -e "  ☠️  Killed:            ${GREEN}${killed}${NC}"
        if [ "$survived" -gt 0 ]; then
            echo -e "  🧟 Survived:          ${RED}${survived}${NC}"
        else
            echo -e "  🧟 Survived:          ${GREEN}0${NC}"
        fi
    else
        log_warning "No mutation test results"
        echo -e "  ${CYAN}Run: ./gradlew pitest${NC}"
    fi
}

get_performance_metrics() {
    print_section "⚡ Performance Tests"
    local jmeter_results="build/reports/jmeter/results.csv"
    local gatling_stats=$(find build/reports/gatling -name "stats.json" 2>/dev/null | head -1)
    
    if [ -f "$jmeter_results" ]; then
        local avg_response=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}' "$jmeter_results")
        local max_response=$(awk -F',' 'NR>1 {if($2>max) max=$2} END {printf "%.0f", max}' "$jmeter_results")
        local error_rate=$(awk -F',' 'NR>1 {if($4!="true") errors++; total++} END {if(total>0) printf "%.1f", errors*100/total; else print "0"}' "$jmeter_results")
        
        echo -e "  📊 Avg Response:       ${avg_response}ms"
        echo -e "  📈 Max Response:       ${max_response}ms"
        echo -e "  ❌ Error Rate:         ${error_rate}%"
    elif [ -f "$gatling_stats" ]; then
        local avg_response=$(grep -o '"mean":[0-9]*' "$gatling_stats" 2>/dev/null | head -1 | cut -d':' -f2 || echo "0")
        echo -e "  📊 Avg Response:       ${avg_response}ms"
    else
        log_warning "No performance test results"
        echo -e "  ${CYAN}Run: ./gradlew jmRun or ./gradlew gatlingRun${NC}"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# CLOUDWATCH ALARM SETUP FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

setup_sns_topic() {
    log_info "Setting up SNS Topic..."
    
    local topic_arn=$(safe_aws_command \
        "aws sns list-topics \
            --region $REGION \
            --query \"Topics[?contains(TopicArn, '$SNS_TOPIC')].TopicArn | [0]\" \
            --output text" || echo "")
    
    if [ -z "$topic_arn" ] || [ "$topic_arn" = "None" ]; then
        log_info "Creating SNS topic: $SNS_TOPIC"
        topic_arn=$(safe_aws_command \
            "aws sns create-topic \
                --name $SNS_TOPIC \
                --region $REGION \
                --query 'TopicArn' \
                --output text")
        log_success "SNS topic created: $topic_arn"
    else
        log_success "Using existing SNS topic: $topic_arn"
    fi
    
    # Subscribe email to SNS topic
    log_info "Setting up Email Notifications..."
    local subscription_exists=$(safe_aws_command \
        "aws sns list-subscriptions-by-topic \
            --topic-arn $topic_arn \
            --region $REGION \
            --query \"Subscriptions[?Endpoint=='$ALERT_EMAIL'].SubscriptionArn | [0]\" \
            --output text" || echo "")
    
    if [ -z "$subscription_exists" ] || [ "$subscription_exists" = "None" ]; then
        log_info "Subscribing $ALERT_EMAIL to alerts..."
        safe_aws_command \
            "aws sns subscribe \
                --topic-arn $topic_arn \
                --protocol email \
                --notification-endpoint $ALERT_EMAIL \
                --region $REGION"
        log_warning "Please check your email and confirm the subscription"
    else
        log_success "Email already subscribed: $ALERT_EMAIL"
    fi
    
    echo "$topic_arn"
}

create_alarm() {
    local alarm_name=$1
    local description=$2
    local namespace=$3
    local metric_name=$4
    local dimensions=$5
    local statistic=$6
    local period=$7
    local threshold=$8
    local comparison=$9
    local evaluation_periods=${10}
    local sns_topic_arn=${11}
    
    log_info "Creating alarm: $alarm_name"
    
    if safe_aws_command \
        "aws cloudwatch put-metric-alarm \
            --alarm-name \"$alarm_name\" \
            --alarm-description \"$description\" \
            --actions-enabled \
            --alarm-actions $sns_topic_arn \
            --metric-name \"$metric_name\" \
            --namespace \"$namespace\" \
            --statistic \"$statistic\" \
            --dimensions $dimensions \
            --period $period \
            --threshold $threshold \
            --comparison-operator $comparison \
            --evaluation-periods $evaluation_periods \
            --region $REGION"; then
        log_success "Alarm configured: $alarm_name"
    else
        log_warning "Failed to create alarm: $alarm_name"
        return 1
    fi
}

setup_cloudwatch_alarms() {
    print_header "CloudWatch Alarms Setup for Oddiya"
    
    # Create SNS topic first
    local sns_topic_arn=$(setup_sns_topic)
    
    log_info "Creating CloudWatch Alarms..."
    
    # Core alarms array: name, description, namespace, metric, dimensions, statistic, period, threshold, comparison, evaluation_periods
    declare -a alarms=(
        "Oddiya-ECS-No-Running-Tasks|Alert when ECS service has no running tasks|AWS/ECS|CPUUtilization|Name=ServiceName,Value=$ECS_SERVICE Name=ClusterName,Value=$ECS_CLUSTER|SampleCount|60|1|LessThanThreshold|2"
        "Oddiya-ECS-High-CPU|Alert when ECS CPU utilization exceeds 80%|AWS/ECS|CPUUtilization|Name=ServiceName,Value=$ECS_SERVICE Name=ClusterName,Value=$ECS_CLUSTER|Average|300|80|GreaterThanThreshold|2"
        "Oddiya-ECS-High-Memory|Alert when ECS memory utilization exceeds 85%|AWS/ECS|MemoryUtilization|Name=ServiceName,Value=$ECS_SERVICE Name=ClusterName,Value=$ECS_CLUSTER|Average|300|85|GreaterThanThreshold|2"
        "Oddiya-RDS-High-CPU|Alert when RDS CPU exceeds 75%|AWS/RDS|CPUUtilization|Name=DBInstanceIdentifier,Value=$RDS_INSTANCE|Average|300|75|GreaterThanThreshold|2"
        "Oddiya-RDS-Connection-Limit|Alert when database connections near limit|AWS/RDS|DatabaseConnections|Name=DBInstanceIdentifier,Value=$RDS_INSTANCE|Average|300|45|GreaterThanThreshold|2"
        "Oddiya-RDS-Low-Storage|Alert when free storage drops below 1GB|AWS/RDS|FreeStorageSpace|Name=DBInstanceIdentifier,Value=$RDS_INSTANCE|Average|300|1073741824|LessThanThreshold|1"
        "Oddiya-RDS-High-Read-Latency|Alert when read latency exceeds 200ms|AWS/RDS|ReadLatency|Name=DBInstanceIdentifier,Value=$RDS_INSTANCE|Average|300|0.2|GreaterThanThreshold|2"
    )
    
    # Create each alarm
    for alarm_config in "${alarms[@]}"; do
        IFS='|' read -r name description namespace metric dimensions statistic period threshold comparison evaluation_periods <<< "$alarm_config"
        create_alarm "$name" "$description" "$namespace" "$metric" "$dimensions" "$statistic" "$period" "$threshold" "$comparison" "$evaluation_periods" "$sns_topic_arn"
    done
    
    # Create composite alarm for service health
    log_info "Creating Composite Alarm..."
    if safe_aws_command \
        "aws cloudwatch put-composite-alarm \
            --alarm-name \"Oddiya-Service-Critical\" \
            --alarm-description \"Critical: Multiple service components are failing\" \
            --actions-enabled \
            --alarm-actions $sns_topic_arn \
            --alarm-rule \"(ALARM('Oddiya-ECS-No-Running-Tasks'))\" \
            --region $REGION"; then
        log_success "Composite alarm configured"
    else
        log_warning "Composite alarm may already exist"
    fi
    
    # Summary
    print_header "CloudWatch Alarms Setup Complete"
    log_success "CloudWatch Alarms Setup Complete!"
    
    echo -e "\n${CYAN}Configured Alarms:${NC}"
    safe_aws_command \
        "aws cloudwatch describe-alarms \
            --alarm-name-prefix \"Oddiya-\" \
            --region $REGION \
            --query 'MetricAlarms[].{Name:AlarmName,State:StateValue}' \
            --output table"
    
    echo -e "\n${CYAN}Next Steps:${NC}"
    echo -e "1. ${YELLOW}Confirm email subscription${NC} at $ALERT_EMAIL"
    echo -e "2. Test alarms: ${CYAN}aws cloudwatch set-alarm-state --alarm-name Oddiya-ECS-High-CPU --state-value ALARM --state-reason 'Testing'${NC}"
    echo -e "3. View alarms: ${CYAN}https://console.aws.amazon.com/cloudwatch/home?region=$REGION#alarmsV2:${NC}"
    echo -e "4. Monitor dashboard: ${CYAN}./scripts/monitor.sh --aws${NC}"
    
    log_success "Monitoring is now active! 🚀"
}

# ═══════════════════════════════════════════════════════════════════════════════
# DISPLAY AND INTERACTION FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

show_aws_dashboard() {
    clear
    print_header "AWS CloudWatch Production Monitor - Oddiya"
    print_aws_info
    
    check_ecs_status
    check_alb_health  
    check_rds_health
    check_recent_errors
    check_active_alarms
    
    echo -e "\n${BLUE}${BOLD}$(printf '═%.0s' $(seq 1 68))${NC}"
    echo -e "${CYAN}${BOLD}Quick Actions:${NC}"
    echo -e "  ${CYAN}[L]${NC} View Logs  ${CYAN}[D]${NC} Dashboard  ${CYAN}[A]${NC} Alarms  ${CYAN}[M]${NC} Metrics  ${CYAN}[R]${NC} Refresh  ${CYAN}[Q]${NC} Quit"
    
    if [ "$AUTO_REFRESH" = "true" ]; then
        echo -e "${YELLOW}Auto-refresh in $MONITORING_INTERVAL seconds... (Press any key for menu)${NC}"
    else
        echo -e "${YELLOW}Press any key for actions...${NC}"
    fi
}

show_test_dashboard() {
    clear
    print_header "Oddiya Test Monitoring Dashboard"
    echo -e "${CYAN}$(printf "%*s" $(((68 + $(date +"$TIMESTAMP_FORMAT" | wc -c))/2)) "$(date +"$TIMESTAMP_FORMAT")")${NC}"
    print_header ""
    
    get_coverage_metrics
    get_test_metrics
    get_security_metrics
    get_mutation_metrics
    get_performance_metrics
    
    echo -e "\n${BLUE}${BOLD}$(printf '═%.0s' $(seq 1 68))${NC}"
    echo -e "${CYAN}${BOLD}Quick Actions:${NC}"
    echo -e "  ${CYAN}[T]${NC} Run Tests  ${CYAN}[C]${NC} Coverage  ${CYAN}[S]${NC} Security  ${CYAN}[M]${NC} Mutation  ${CYAN}[P]${NC} Performance  ${CYAN}[Q]${NC} Quit"
    
    if [ "$AUTO_REFRESH" = "true" ]; then
        echo -e "${YELLOW}Auto-refresh in $MONITORING_INTERVAL seconds... (Press any key for menu)${NC}"
    else
        echo -e "${YELLOW}Press any key for actions...${NC}"
    fi
}

show_all_dashboard() {
    clear
    print_header "Unified Monitoring Dashboard - Oddiya"
    print_aws_info
    
    # AWS Section (Condensed)
    echo -e "\n${BOLD}${BLUE}🏗️  AWS INFRASTRUCTURE${NC}"
    echo -e "${BLUE}$(printf '─%.0s' $(seq 1 40))${NC}"
    
    # Quick status checks
    local ecs_status=$(get_resource_status "ecs-service" "$ECS_SERVICE" "$ECS_CLUSTER")
    local rds_status=$(get_resource_status "rds-instance" "$RDS_INSTANCE")
    
    echo -e "  📦 ECS Service:        $ecs_status"
    echo -e "  🗄️  RDS Database:      $rds_status"
    
    # Quick alarm check
    local alarm_count=$(safe_aws_command \
        "aws cloudwatch describe-alarms \
            --state-value ALARM \
            --region $REGION \
            --query \"MetricAlarms[?contains(AlarmName, \\`oddiya\\`) || contains(AlarmName, \\`Oddiya\\`)] | length(@)\" \
            --output text" || echo "0")
    
    if [ "$alarm_count" -gt 0 ]; then
        echo -e "  🔔 Active Alarms:      ${RED}$alarm_count${NC}"
    else
        echo -e "  🔔 Active Alarms:      ${GREEN}0${NC}"
    fi
    
    # Test Section (Condensed)
    echo -e "\n${BOLD}${GREEN}🧪 TESTING STATUS${NC}"
    echo -e "${BLUE}$(printf '─%.0s' $(seq 1 40))${NC}"
    
    # Quick coverage check
    local csv_file="build/reports/jacoco/test/jacocoTestReport.csv"
    if [ -f "$csv_file" ]; then
        local coverage=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$4; covered+=$5} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        echo -e "  📊 Code Coverage:      $(format_percentage "$coverage" 80 60)"
    else
        echo -e "  📊 Code Coverage:      ${YELLOW}No data${NC}"
    fi
    
    # Quick test status
    if [ -d "build/test-results/test" ]; then
        local total=$(find build/test-results/test -name "*.xml" -exec grep -c '<testcase' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local failures=$(find build/test-results/test -name "*.xml" -exec grep -c '<failure' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local errors=$(find build/test-results/test -name "*.xml" -exec grep -c '<error' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local passed=$((total - failures - errors))
        
        if [ "$total" -gt 0 ]; then
            local success_rate=$((passed * 100 / total))
            echo -e "  🧪 Test Success Rate:  $(format_percentage "$success_rate" 98 95) ($passed/$total)"
        else
            echo -e "  🧪 Test Success Rate:  ${YELLOW}No tests${NC}"
        fi
    else
        echo -e "  🧪 Test Success Rate:  ${YELLOW}No results${NC}"
    fi
    
    echo -e "\n${BLUE}${BOLD}$(printf '═%.0s' $(seq 1 68))${NC}"
    echo -e "${CYAN}${BOLD}Quick Actions:${NC}"
    echo -e "  ${CYAN}[1]${NC} AWS Detail  ${CYAN}[2]${NC} Test Detail  ${CYAN}[3]${NC} Setup Alarms  ${CYAN}[R]${NC} Refresh  ${CYAN}[Q]${NC} Quit"
    
    if [ "$AUTO_REFRESH" = "true" ]; then
        echo -e "${YELLOW}Auto-refresh in $MONITORING_INTERVAL seconds... (Press any key for menu)${NC}"
    else
        echo -e "${YELLOW}Press any key for actions...${NC}"
    fi
}

# Function to handle AWS mode user input
handle_aws_input() {
    case $1 in
        l|L)
            log_info "Opening CloudWatch Logs..."
            if command -v open &> /dev/null; then
                open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#logsV2:log-groups/log-group/\$252Fecs\$252Foddiya-backend"
            else
                echo "CloudWatch Logs URL: https://console.aws.amazon.com/cloudwatch/home?region=$REGION#logsV2:log-groups/log-group/\$252Fecs\$252Foddiya-backend"
            fi
            ;;
        d|D)
            log_info "Opening CloudWatch Dashboard..."
            if command -v open &> /dev/null; then
                open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#dashboards:"
            else
                echo "CloudWatch Dashboard URL: https://console.aws.amazon.com/cloudwatch/home?region=$REGION#dashboards:"
            fi
            ;;
        a|A)
            log_info "Opening CloudWatch Alarms..."
            if command -v open &> /dev/null; then
                open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#alarmsV2:"
            else
                echo "CloudWatch Alarms URL: https://console.aws.amazon.com/cloudwatch/home?region=$REGION#alarmsV2:"
            fi
            ;;
        m|M)
            log_info "Opening CloudWatch Metrics..."
            if command -v open &> /dev/null; then
                open "https://console.aws.amazon.com/cloudwatch/home?region=$REGION#metricsV2:"
            else
                echo "CloudWatch Metrics URL: https://console.aws.amazon.com/cloudwatch/home?region=$REGION#metricsV2:"
            fi
            ;;
        r|R)
            return 0  # Continue loop
            ;;
        q|Q)
            log_success "Exiting monitor..."
            exit 0
            ;;
    esac
}

# Function to handle test mode user input
handle_test_input() {
    case $1 in
        t|T)
            log_info "Running tests..."
            ./gradlew test || log_warning "Test execution failed"
            ;;
        c|C)
            log_info "Generating coverage report..."
            ./gradlew jacocoTestReport || log_warning "Coverage report failed"
            ;;
        s|S)
            log_info "Running security scan..."
            ./gradlew dependencyCheckAnalyze || log_warning "Security scan failed"
            ;;
        m|M)
            log_info "Running mutation tests..."
            ./gradlew pitest || log_warning "Mutation tests failed"
            ;;
        p|P)
            log_info "Running performance tests..."
            ./gradlew jmRun || log_warning "Performance tests failed"
            ;;
        q|Q)
            log_success "Exiting monitor..."
            exit 0
            ;;
    esac
}

# Function to handle all mode user input
handle_all_input() {
    case $1 in
        1)
            MODE="aws"
            return 0
            ;;
        2)
            MODE="tests"
            return 0
            ;;
        3)
            setup_cloudwatch_alarms
            if confirm_action "Return to monitoring?"; then
                return 0
            else
                exit 0
            fi
            ;;
        r|R)
            return 0  # Continue loop
            ;;
        q|Q)
            log_success "Exiting monitor..."
            exit 0
            ;;
    esac
}

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN MONITORING LOOP
# ═══════════════════════════════════════════════════════════════════════════════

run_monitoring_loop() {
    # Trap Ctrl+C
    trap 'echo -e "\n${GREEN}Monitoring stopped.${NC}"; exit 0' INT
    
    while true; do
        case "$MODE" in
            "aws")
                show_aws_dashboard
                if [ "$AUTO_REFRESH" = "true" ]; then
                    if read -t $MONITORING_INTERVAL -n 1 key; then
                        handle_aws_input "$key"
                    fi
                else
                    read -n 1 key
                    handle_aws_input "$key"
                fi
                ;;
            "tests")
                show_test_dashboard
                if [ "$AUTO_REFRESH" = "true" ]; then
                    if read -t $MONITORING_INTERVAL -n 1 key; then
                        handle_test_input "$key"
                    fi
                else
                    read -n 1 key
                    handle_test_input "$key"
                fi
                ;;
            "all"|"")
                show_all_dashboard
                if [ "$AUTO_REFRESH" = "true" ]; then
                    if read -t $MONITORING_INTERVAL -n 1 key; then
                        handle_all_input "$key"
                    fi
                else
                    read -n 1 key
                    handle_all_input "$key"
                fi
                ;;
        esac
    done
}

# ═══════════════════════════════════════════════════════════════════════════════
# ARGUMENT PARSING AND MAIN FUNCTION
# ═══════════════════════════════════════════════════════════════════════════════

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --aws)
                MODE="aws"
                shift
                ;;
            --tests)
                MODE="tests"
                shift
                ;;
            --cloudwatch)
                MODE="cloudwatch"
                shift
                ;;
            --all)
                MODE="all"
                shift
                ;;
            --interval)
                MONITORING_INTERVAL="$2"
                shift 2
                ;;
            --email)
                ALERT_EMAIL="$2"
                shift 2
                ;;
            --no-refresh)
                AUTO_REFRESH="false"
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Set default mode if not specified
    if [ -z "$MODE" ]; then
        MODE="all"
    fi
}

# Main function
main() {
    parse_arguments "$@"
    
    case "$MODE" in
        "cloudwatch")
            setup_cloudwatch_alarms
            ;;
        "aws"|"tests"|"all")
            log_info "Starting $MODE monitoring..."
            if [ "$AUTO_REFRESH" = "true" ]; then
                log_info "Refresh interval: $MONITORING_INTERVAL seconds"
            else
                log_info "Single run mode (no auto-refresh)"
            fi
            sleep 1
            run_monitoring_loop
            ;;
        *)
            log_error "Invalid mode: $MODE"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function
main "$@"