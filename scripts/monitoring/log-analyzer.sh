#!/bin/bash

# ECS Log Analysis and Troubleshooting Script
# Analyzes CloudWatch logs for deployment and runtime issues

set -e

# Configuration
REGION="${AWS_REGION:-ap-northeast-2}"
LOG_GROUP="${LOG_GROUP:-/ecs/oddiya-dev}"
SERVICE_NAME="${ECS_SERVICE:-oddiya-dev}"
CLUSTER_NAME="${ECS_CLUSTER:-oddiya-dev}"
HOURS_BACK="${HOURS_BACK:-24}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Icons
ERROR_ICON="🚨"
WARNING_ICON="⚠️"
INFO_ICON="ℹ️"
SUCCESS_ICON="✅"
SEARCH_ICON="🔍"
CHART_ICON="📊"
BUG_ICON="🐛"
PERF_ICON="⚡"

echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${BLUE}${BOLD}${SEARCH_ICON} ECS LOG ANALYZER${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo -e "${CYAN}Log Group: $LOG_GROUP${NC}"
echo -e "${CYAN}Service: $SERVICE_NAME${NC}"
echo -e "${CYAN}Analysis Period: Last ${HOURS_BACK} hours${NC}"
echo -e "${CYAN}Timestamp: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${BLUE}${BOLD}=========================================${NC}"
echo ""

# Calculate time range
START_TIME=$(date -d "${HOURS_BACK} hours ago" +%s)000
END_TIME=$(date +%s)000

# Check prerequisites
check_prerequisites() {
    local errors=()
    
    if ! command -v aws &> /dev/null; then
        errors+=("AWS CLI is not installed")
    fi
    
    if ! aws sts get-caller-identity &> /dev/null; then
        errors+=("AWS credentials not configured")
    fi
    
    if ! command -v jq &> /dev/null; then
        errors+=("jq is not installed")
    fi
    
    # Check if log group exists
    local log_exists
    log_exists=$(aws logs describe-log-groups \
        --log-group-name-prefix "$LOG_GROUP" \
        --region "$REGION" \
        --query 'length(logGroups)' \
        --output text 2>/dev/null || echo "0")
    
    if [ "$log_exists" -eq 0 ]; then
        errors+=("Log group '$LOG_GROUP' not found")
    fi
    
    if [ ${#errors[@]} -gt 0 ]; then
        echo -e "${RED}${BOLD}Prerequisites check failed:${NC}"
        for error in "${errors[@]}"; do
            echo -e "${RED}  ${ERROR_ICON} $error${NC}"
        done
        echo ""
        exit 1
    fi
}

# Query logs with CloudWatch Insights
query_logs() {
    local query="$1"
    local description="$2"
    
    echo -e "${BLUE}${BOLD}${SEARCH_ICON} $description${NC}"
    
    # Start query
    local query_id
    query_id=$(aws logs start-query \
        --log-group-name "$LOG_GROUP" \
        --start-time "$START_TIME" \
        --end-time "$END_TIME" \
        --query-string "$query" \
        --region "$REGION" \
        --query 'queryId' \
        --output text 2>/dev/null)
    
    if [ -z "$query_id" ] || [ "$query_id" == "None" ]; then
        echo -e "${RED}  ${ERROR_ICON} Failed to start query${NC}"
        return 1
    fi
    
    # Wait for query completion
    local status="Running"
    local attempts=0
    local max_attempts=30
    
    while [ "$status" == "Running" ] && [ $attempts -lt $max_attempts ]; do
        sleep 2
        attempts=$((attempts + 1))
        
        local query_result
        query_result=$(aws logs get-query-results \
            --query-id "$query_id" \
            --region "$REGION" \
            --output json 2>/dev/null)
        
        status=$(echo "$query_result" | jq -r '.status')
        
        if [ "$status" == "Complete" ]; then
            echo "$query_result" | jq -r '.results'
            return 0
        fi
    done
    
    if [ "$status" != "Complete" ]; then
        echo -e "${YELLOW}  ${WARNING_ICON} Query timeout or still running${NC}"
        return 1
    fi
}

# Analyze error patterns
analyze_errors() {
    echo -e "${RED}${BOLD}${BUG_ICON} ERROR ANALYSIS${NC}"
    echo -e "${RED}${BOLD}===========================================${NC}"
    
    # Query for errors
    local error_query='
    fields @timestamp, @message
    | filter @message like /ERROR/
    | stats count() by bin(5m)
    | sort @timestamp desc
    | limit 20
    '
    
    local error_results
    error_results=$(query_logs "$error_query" "Error Rate Over Time (5-minute bins)")
    
    if [ -n "$error_results" ] && [ "$error_results" != "[]" ]; then
        echo "$error_results" | jq -r '.[] | [.[0], .[1]] | @tsv' | while IFS=$'\t' read -r timestamp count; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M" 2>/dev/null || echo "$timestamp")
            
            local bar=""
            for i in $(seq 1 "$count"); do
                bar="${bar}█"
            done
            
            if [ "$count" -gt 10 ]; then
                echo -e "${RED}  $formatted_time: $count errors ${bar}${NC}"
            elif [ "$count" -gt 5 ]; then
                echo -e "${YELLOW}  $formatted_time: $count errors ${bar}${NC}"
            else
                echo -e "${CYAN}  $formatted_time: $count errors ${bar}${NC}"
            fi
        done
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No errors found in the specified time period${NC}"
    fi
    echo ""
    
    # Top error messages
    local top_errors_query='
    fields @timestamp, @message
    | filter @message like /ERROR/
    | parse @message "*ERROR*: *" as prefix, error_msg
    | stats count() as error_count by error_msg
    | sort error_count desc
    | limit 10
    '
    
    local top_errors
    top_errors=$(query_logs "$top_errors_query" "Top Error Messages")
    
    if [ -n "$top_errors" ] && [ "$top_errors" != "[]" ]; then
        echo -e "${RED}${BOLD}Most Common Errors:${NC}"
        echo "$top_errors" | jq -r '.[] | [.[1], .[0]] | @tsv' | while IFS=$'\t' read -r count error_msg; do
            echo -e "${RED}  ${ERROR_ICON} ($count occurrences) ${error_msg:0:80}${NC}"
        done
    fi
    echo ""
}

# Analyze deployment issues
analyze_deployment() {
    echo -e "${YELLOW}${BOLD}${PERF_ICON} DEPLOYMENT ANALYSIS${NC}"
    echo -e "${YELLOW}${BOLD}===========================================${NC}"
    
    # Health check failures
    local health_check_query='
    fields @timestamp, @message
    | filter @message like /health/ or @message like /actuator/
    | filter @message like /error/ or @message like /failed/ or @message like /timeout/
    | sort @timestamp desc
    | limit 20
    '
    
    local health_results
    health_results=$(query_logs "$health_check_query" "Health Check Issues")
    
    if [ -n "$health_results" ] && [ "$health_results" != "[]" ]; then
        echo -e "${YELLOW}${BOLD}Health Check Issues:${NC}"
        echo "$health_results" | jq -r '.[] | [.[0], .[1]] | @tsv' | while IFS=$'\t' read -r timestamp message; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M:%S" 2>/dev/null || echo "$timestamp")
            echo -e "${YELLOW}  ${WARNING_ICON} $formatted_time: ${message:0:80}...${NC}"
        done
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No health check issues found${NC}"
    fi
    echo ""
    
    # Application startup time
    local startup_query='
    fields @timestamp, @message
    | filter @message like /Started/ and @message like /seconds/
    | parse @message "Started * in * seconds" as app, duration
    | sort @timestamp desc
    | limit 10
    '
    
    local startup_results
    startup_results=$(query_logs "$startup_query" "Application Startup Times")
    
    if [ -n "$startup_results" ] && [ "$startup_results" != "[]" ]; then
        echo -e "${GREEN}${BOLD}Recent Application Starts:${NC}"
        echo "$startup_results" | jq -r '.[] | [.[0], .[2]] | @tsv' | while IFS=$'\t' read -r timestamp duration; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M:%S" 2>/dev/null || echo "$timestamp")
            
            if (( $(echo "$duration > 60" | bc -l 2>/dev/null || echo 0) )); then
                echo -e "${RED}  ${WARNING_ICON} $formatted_time: ${duration}s (slow startup)${NC}"
            elif (( $(echo "$duration > 30" | bc -l 2>/dev/null || echo 0) )); then
                echo -e "${YELLOW}  ${WARNING_ICON} $formatted_time: ${duration}s${NC}"
            else
                echo -e "${GREEN}  ${SUCCESS_ICON} $formatted_time: ${duration}s${NC}"
            fi
        done
    fi
    echo ""
    
    # Memory and GC analysis
    local memory_query='
    fields @timestamp, @message
    | filter @message like /OutOfMemoryError/ or @message like /heap/ or @message like /GC/
    | sort @timestamp desc
    | limit 10
    '
    
    local memory_results
    memory_results=$(query_logs "$memory_query" "Memory and GC Issues")
    
    if [ -n "$memory_results" ] && [ "$memory_results" != "[]" ]; then
        echo -e "${RED}${BOLD}Memory/GC Issues:${NC}"
        echo "$memory_results" | jq -r '.[] | [.[0], .[1]] | @tsv' | while IFS=$'\t' read -r timestamp message; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M:%S" 2>/dev/null || echo "$timestamp")
            echo -e "${RED}  ${ERROR_ICON} $formatted_time: ${message:0:80}...${NC}"
        done
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No memory issues detected${NC}"
    fi
    echo ""
}

# Analyze performance metrics
analyze_performance() {
    echo -e "${PURPLE}${BOLD}${PERF_ICON} PERFORMANCE ANALYSIS${NC}"
    echo -e "${PURPLE}${BOLD}===========================================${NC}"
    
    # Response time analysis
    local response_time_query='
    fields @timestamp, @message
    | filter @message like /ms/ and (@message like /GET/ or @message like /POST/ or @message like /PUT/ or @message like /DELETE/)
    | parse @message "* * * *ms*" as method, path, status, response_time
    | filter response_time > 1000
    | stats avg(response_time) as avg_time, max(response_time) as max_time, count() as slow_requests by bin(10m)
    | sort @timestamp desc
    | limit 12
    '
    
    local perf_results
    perf_results=$(query_logs "$response_time_query" "Slow Requests (>1000ms)")
    
    if [ -n "$perf_results" ] && [ "$perf_results" != "[]" ]; then
        echo -e "${PURPLE}${BOLD}Slow Request Analysis:${NC}"
        echo "$perf_results" | jq -r '.[] | [.[0], .[3], .[2], .[1]] | @tsv' | while IFS=$'\t' read -r timestamp slow_count max_time avg_time; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M" 2>/dev/null || echo "$timestamp")
            
            if [ "$slow_count" -gt 10 ]; then
                echo -e "${RED}  ${WARNING_ICON} $formatted_time: $slow_count slow requests (avg: ${avg_time}ms, max: ${max_time}ms)${NC}"
            elif [ "$slow_count" -gt 5 ]; then
                echo -e "${YELLOW}  ${WARNING_ICON} $formatted_time: $slow_count slow requests (avg: ${avg_time}ms, max: ${max_time}ms)${NC}"
            else
                echo -e "${CYAN}  ${INFO_ICON} $formatted_time: $slow_count slow requests (avg: ${avg_time}ms, max: ${max_time}ms)${NC}"
            fi
        done
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No slow requests detected${NC}"
    fi
    echo ""
    
    # Database connection issues
    local db_query='
    fields @timestamp, @message
    | filter @message like /connection/ and (@message like /timeout/ or @message like /failed/ or @message like /error/)
    | sort @timestamp desc
    | limit 10
    '
    
    local db_results
    db_results=$(query_logs "$db_query" "Database Connection Issues")
    
    if [ -n "$db_results" ] && [ "$db_results" != "[]" ]; then
        echo -e "${RED}${BOLD}Database Issues:${NC}"
        echo "$db_results" | jq -r '.[] | [.[0], .[1]] | @tsv' | while IFS=$'\t' read -r timestamp message; do
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M:%S" 2>/dev/null || echo "$timestamp")
            echo -e "${RED}  ${ERROR_ICON} $formatted_time: ${message:0:80}...${NC}"
        done
    else
        echo -e "${GREEN}  ${SUCCESS_ICON} No database connection issues detected${NC}"
    fi
    echo ""
}

# Generate summary and recommendations
generate_summary() {
    echo -e "${BLUE}${BOLD}${CHART_ICON} SUMMARY AND RECOMMENDATIONS${NC}"
    echo -e "${BLUE}${BOLD}===========================================${NC}"
    
    # Log volume analysis
    local volume_query='
    fields @timestamp
    | stats count() as log_count by bin(1h)
    | sort @timestamp desc
    | limit 24
    '
    
    local volume_results
    volume_results=$(query_logs "$volume_query" "Log Volume per Hour")
    
    if [ -n "$volume_results" ] && [ "$volume_results" != "[]" ]; then
        echo -e "${CYAN}${BOLD}Log Volume (Last 24 hours):${NC}"
        local total_logs=0
        local max_logs=0
        
        while read -r line; do
            local timestamp count
            timestamp=$(echo "$line" | jq -r '.[0]')
            count=$(echo "$line" | jq -r '.[1]')
            
            total_logs=$((total_logs + count))
            if [ "$count" -gt "$max_logs" ]; then
                max_logs=$count
            fi
            
            local formatted_time
            formatted_time=$(date -d "$timestamp" "+%H:%M" 2>/dev/null || echo "$timestamp")
            
            local bar=""
            local bar_length=$((count * 50 / max_logs))
            for i in $(seq 1 "$bar_length"); do
                bar="${bar}▓"
            done
            
            echo -e "${CYAN}  $formatted_time: $count logs $bar${NC}"
        done <<< "$(echo "$volume_results" | jq -c '.[]')"
        
        echo -e "${CYAN}  Total logs: $total_logs${NC}"
        echo ""
    fi
    
    # Recommendations
    echo -e "${GREEN}${BOLD}Recommendations:${NC}"
    echo ""
    
    echo -e "${GREEN}1. Monitoring Setup:${NC}"
    echo -e "${CYAN}   - Set up CloudWatch alarms for error rates > 5 errors/5min${NC}"
    echo -e "${CYAN}   - Monitor response times > 5000ms${NC}"
    echo -e "${CYAN}   - Alert on memory usage > 85%${NC}"
    echo ""
    
    echo -e "${GREEN}2. Performance Optimization:${NC}"
    echo -e "${CYAN}   - Consider increasing task memory if JVM issues detected${NC}"
    echo -e "${CYAN}   - Optimize health check frequency and timeout${NC}"
    echo -e "${CYAN}   - Implement connection pooling for databases${NC}"
    echo ""
    
    echo -e "${GREEN}3. Deployment Best Practices:${NC}"
    echo -e "${CYAN}   - Implement blue-green deployments for zero downtime${NC}"
    echo -e "${CYAN}   - Add deployment circuit breakers${NC}"
    echo -e "${CYAN}   - Monitor deployment duration (target <5min)${NC}"
    echo ""
    
    echo -e "${GREEN}4. Troubleshooting Commands:${NC}"
    echo -e "${YELLOW}   # Real-time log monitoring:${NC}"
    echo -e "${YELLOW}   aws logs tail $LOG_GROUP --follow --region $REGION${NC}"
    echo ""
    echo -e "${YELLOW}   # Check ECS service events:${NC}"
    echo -e "${YELLOW}   aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $REGION${NC}"
    echo ""
    echo -e "${YELLOW}   # Check task health:${NC}"
    echo -e "${YELLOW}   aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks \$(aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --query 'taskArns[0]' --output text) --region $REGION${NC}"
    echo ""
}

# Export analysis results
export_results() {
    local output_file="/tmp/log-analysis-$(date +%Y%m%d-%H%M%S).json"
    
    echo -e "${BLUE}${BOLD}Exporting detailed analysis to: $output_file${NC}"
    
    # Create comprehensive report
    cat > "$output_file" << EOF
{
  "analysis_metadata": {
    "timestamp": "$(date -Iseconds)",
    "log_group": "$LOG_GROUP",
    "service_name": "$SERVICE_NAME",
    "cluster_name": "$CLUSTER_NAME",
    "hours_analyzed": $HOURS_BACK,
    "start_time": "$START_TIME",
    "end_time": "$END_TIME"
  },
  "queries_executed": [
    "Error rate analysis",
    "Health check issues",
    "Application startup times",
    "Memory and GC issues",
    "Performance analysis",
    "Database connection issues",
    "Log volume analysis"
  ],
  "recommendations": [
    "Set up CloudWatch alarms for error rates",
    "Monitor response times and resource usage",
    "Implement deployment best practices",
    "Optimize application performance"
  ]
}
EOF
    
    echo -e "${GREEN}Analysis complete. Report saved to: $output_file${NC}"
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -g, --log-group GROUP   CloudWatch log group name"
    echo "  -t, --hours HOURS       Hours to look back (default: 24)"
    echo "  -e, --export            Export detailed results to JSON"
    echo ""
    echo "Environment Variables:"
    echo "  AWS_REGION              AWS region (default: ap-northeast-2)"
    echo "  LOG_GROUP               CloudWatch log group (default: /ecs/oddiya-dev)"
    echo "  ECS_SERVICE             ECS service name (default: oddiya-dev)"
    echo "  ECS_CLUSTER             ECS cluster name (default: oddiya-dev)"
    echo "  HOURS_BACK              Hours to analyze (default: 24)"
    echo ""
}

# Parse command line arguments
EXPORT_RESULTS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -g|--log-group)
            LOG_GROUP="$2"
            shift 2
            ;;
        -t|--hours)
            HOURS_BACK="$2"
            shift 2
            ;;
        -e|--export)
            EXPORT_RESULTS=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    check_prerequisites
    analyze_errors
    analyze_deployment
    analyze_performance
    generate_summary
    
    if [ "$EXPORT_RESULTS" = true ]; then
        export_results
    fi
}

main "$@"