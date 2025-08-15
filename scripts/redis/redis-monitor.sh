#!/bin/bash
# Redis Monitoring Script
# Agent 6 - Monitoring & Operations Engineer

set -e

# Configuration variables
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
CLOUDWATCH_NAMESPACE="${CLOUDWATCH_NAMESPACE:-Oddiya/Redis}"

# Monitoring thresholds
MEMORY_THRESHOLD="${MEMORY_THRESHOLD:-85}"  # Percentage
CPU_THRESHOLD="${CPU_THRESHOLD:-80}"        # Percentage
CONNECTION_THRESHOLD="${CONNECTION_THRESHOLD:-1000}"
SLOW_LOG_THRESHOLD="${SLOW_LOG_THRESHOLD:-100}"  # microseconds
KEY_SPACE_THRESHOLD="${KEY_SPACE_THRESHOLD:-1000000}"

# Logging configuration
LOG_FILE="${LOG_FILE:-/var/log/redis/monitor.log}"
METRICS_FILE="${METRICS_FILE:-/var/log/redis/metrics.json}"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")
HOSTNAME=$(hostname -s)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "${LOG_FILE}"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "${LOG_FILE}"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "${LOG_FILE}"
    send_alert "WARNING" "$1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "${LOG_FILE}"
    send_alert "ERROR" "$1"
}

# Send alert notifications
send_alert() {
    local level="$1"
    local message="$2"
    
    # Email notification
    if [[ -n "$ALERT_EMAIL" ]]; then
        echo "Redis Monitor Alert [$level]: $message" | \
            mail -s "Redis Monitor Alert - $HOSTNAME" "$ALERT_EMAIL" 2>/dev/null || true
    fi
    
    # Slack notification
    if [[ -n "$SLACK_WEBHOOK" ]]; then
        local color="good"
        [[ "$level" == "ERROR" ]] && color="danger"
        [[ "$level" == "WARNING" ]] && color="warning"
        
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"attachments\":[{\"color\":\"$color\",\"title\":\"Redis Monitor Alert\",\"text\":\"**$level**: $message\",\"fields\":[{\"title\":\"Host\",\"value\":\"$HOSTNAME\",\"short\":true},{\"title\":\"Environment\",\"value\":\"$ENVIRONMENT\",\"short\":true}]}]}" \
            "$SLACK_WEBHOOK" 2>/dev/null || true
    fi
}

# Send metrics to CloudWatch
send_cloudwatch_metric() {
    local metric_name="$1"
    local value="$2"
    local unit="$3"
    local dimensions="$4"
    
    if command -v aws &> /dev/null; then
        aws cloudwatch put-metric-data \
            --namespace "$CLOUDWATCH_NAMESPACE" \
            --metric-data MetricName="$metric_name",Value="$value",Unit="$unit",Dimensions="$dimensions" \
            2>/dev/null || true
    fi
}

# Check Redis connectivity
check_redis_connection() {
    log "Checking Redis connectivity..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    if $redis_cmd ping &>/dev/null; then
        success "Redis is responding to ping"
        send_cloudwatch_metric "RedisAvailable" "1" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
        return 0
    else
        error "Redis is not responding at $REDIS_HOST:$REDIS_PORT"
        send_cloudwatch_metric "RedisAvailable" "0" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
        return 1
    fi
}

# Get Redis info and extract metrics
get_redis_metrics() {
    log "Collecting Redis metrics..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get comprehensive Redis info
    local redis_info=$($redis_cmd info 2>/dev/null || echo "")
    
    if [[ -z "$redis_info" ]]; then
        error "Could not retrieve Redis info"
        return 1
    fi
    
    # Parse metrics
    local used_memory=$(echo "$redis_info" | grep "used_memory:" | cut -d: -f2 | tr -d '\r')
    local used_memory_human=$(echo "$redis_info" | grep "used_memory_human:" | cut -d: -f2 | tr -d '\r')
    local used_memory_rss=$(echo "$redis_info" | grep "used_memory_rss:" | cut -d: -f2 | tr -d '\r')
    local maxmemory=$(echo "$redis_info" | grep "maxmemory:" | cut -d: -f2 | tr -d '\r')
    local connected_clients=$(echo "$redis_info" | grep "connected_clients:" | cut -d: -f2 | tr -d '\r')
    local blocked_clients=$(echo "$redis_info" | grep "blocked_clients:" | cut -d: -f2 | tr -d '\r')
    local total_connections_received=$(echo "$redis_info" | grep "total_connections_received:" | cut -d: -f2 | tr -d '\r')
    local total_commands_processed=$(echo "$redis_info" | grep "total_commands_processed:" | cut -d: -f2 | tr -d '\r')
    local keyspace_hits=$(echo "$redis_info" | grep "keyspace_hits:" | cut -d: -f2 | tr -d '\r')
    local keyspace_misses=$(echo "$redis_info" | grep "keyspace_misses:" | cut -d: -f2 | tr -d '\r')
    local expired_keys=$(echo "$redis_info" | grep "expired_keys:" | cut -d: -f2 | tr -d '\r')
    local evicted_keys=$(echo "$redis_info" | grep "evicted_keys:" | cut -d: -f2 | tr -d '\r')
    local redis_version=$(echo "$redis_info" | grep "redis_version:" | cut -d: -f2 | tr -d '\r')
    local redis_mode=$(echo "$redis_info" | grep "redis_mode:" | cut -d: -f2 | tr -d '\r')
    local role=$(echo "$redis_info" | grep "role:" | cut -d: -f2 | tr -d '\r')
    local uptime_in_seconds=$(echo "$redis_info" | grep "uptime_in_seconds:" | cut -d: -f2 | tr -d '\r')
    
    # Calculate memory usage percentage
    local memory_percentage=0
    if [[ "$maxmemory" -gt 0 ]]; then
        memory_percentage=$(( (used_memory * 100) / maxmemory ))
    fi
    
    # Calculate hit rate
    local hit_rate=0
    local total_hits=$((keyspace_hits + keyspace_misses))
    if [[ $total_hits -gt 0 ]]; then
        hit_rate=$(( (keyspace_hits * 100) / total_hits ))
    fi
    
    # Store metrics in JSON format
    cat > "$METRICS_FILE" << EOF
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "hostname": "$HOSTNAME",
    "environment": "$ENVIRONMENT",
    "redis_info": {
        "version": "$redis_version",
        "mode": "$redis_mode",
        "role": "$role",
        "uptime_seconds": $uptime_in_seconds
    },
    "memory": {
        "used_bytes": $used_memory,
        "used_human": "$used_memory_human",
        "used_rss_bytes": $used_memory_rss,
        "max_bytes": $maxmemory,
        "usage_percentage": $memory_percentage
    },
    "clients": {
        "connected": $connected_clients,
        "blocked": $blocked_clients,
        "total_connections_received": $total_connections_received
    },
    "commands": {
        "total_processed": $total_commands_processed
    },
    "keyspace": {
        "hits": $keyspace_hits,
        "misses": $keyspace_misses,
        "hit_rate_percentage": $hit_rate,
        "expired_keys": $expired_keys,
        "evicted_keys": $evicted_keys
    }
}
EOF

    log "Memory Usage: $used_memory_human (${memory_percentage}%)"
    log "Connected Clients: $connected_clients"
    log "Hit Rate: ${hit_rate}%"
    log "Role: $role"
    
    # Send key metrics to CloudWatch
    send_cloudwatch_metric "MemoryUsagePercent" "$memory_percentage" "Percent" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    send_cloudwatch_metric "ConnectedClients" "$connected_clients" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    send_cloudwatch_metric "HitRatePercent" "$hit_rate" "Percent" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    send_cloudwatch_metric "EvictedKeys" "$evicted_keys" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    
    # Check thresholds and alert
    check_memory_threshold "$memory_percentage"
    check_connection_threshold "$connected_clients"
    check_hit_rate "$hit_rate"
    
    return 0
}

# Check memory threshold
check_memory_threshold() {
    local memory_usage="$1"
    
    if [[ $memory_usage -ge $MEMORY_THRESHOLD ]]; then
        warning "Redis memory usage is ${memory_usage}% (threshold: ${MEMORY_THRESHOLD}%)"
        return 1
    fi
    return 0
}

# Check connection threshold
check_connection_threshold() {
    local connections="$1"
    
    if [[ $connections -ge $CONNECTION_THRESHOLD ]]; then
        warning "Redis connection count is $connections (threshold: $CONNECTION_THRESHOLD)"
        return 1
    fi
    return 0
}

# Check hit rate
check_hit_rate() {
    local hit_rate="$1"
    local min_hit_rate="${MIN_HIT_RATE:-70}"
    
    if [[ $hit_rate -lt $min_hit_rate ]]; then
        warning "Redis hit rate is ${hit_rate}% (minimum expected: ${min_hit_rate}%)"
        return 1
    fi
    return 0
}

# Check slow queries
check_slow_queries() {
    log "Checking for slow queries..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get slow log entries
    local slow_log_len=$($redis_cmd slowlog len 2>/dev/null || echo "0")
    
    if [[ $slow_log_len -gt 0 ]]; then
        log "Found $slow_log_len entries in slow log"
        
        # Get recent slow log entries
        local slow_entries=$($redis_cmd slowlog get 10 2>/dev/null || echo "")
        
        if [[ -n "$slow_entries" ]]; then
            log "Recent slow queries:"
            echo "$slow_entries" | head -20 >> "$LOG_FILE"
            
            if [[ $slow_log_len -gt $SLOW_LOG_THRESHOLD ]]; then
                warning "High number of slow queries: $slow_log_len (threshold: $SLOW_LOG_THRESHOLD)"
            fi
        fi
        
        # Send slow query count to CloudWatch
        send_cloudwatch_metric "SlowQueryCount" "$slow_log_len" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    else
        log "No slow queries found"
        send_cloudwatch_metric "SlowQueryCount" "0" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    fi
}

# Check Redis configuration
check_redis_config() {
    log "Checking Redis configuration..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Check key configuration parameters
    local maxmemory_policy=$($redis_cmd config get maxmemory-policy | tail -1)
    local timeout=$($redis_cmd config get timeout | tail -1)
    local tcp_keepalive=$($redis_cmd config get tcp-keepalive | tail -1)
    local save_config=$($redis_cmd config get save | tail -1)
    
    log "Configuration Check:"
    log "  - maxmemory-policy: $maxmemory_policy"
    log "  - timeout: $timeout"
    log "  - tcp-keepalive: $tcp_keepalive"
    log "  - save: $save_config"
    
    # Configuration recommendations
    if [[ "$maxmemory_policy" == "noeviction" ]]; then
        warning "maxmemory-policy is set to 'noeviction' - consider using 'allkeys-lru' or 'volatile-lru'"
    fi
    
    if [[ "$timeout" == "0" ]]; then
        warning "Client timeout is disabled - consider setting a reasonable timeout value"
    fi
}

# Check keyspace
check_keyspace() {
    log "Checking keyspace information..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get database info
    local db_info=$($redis_cmd info keyspace 2>/dev/null || echo "")
    
    if [[ -n "$db_info" ]]; then
        log "Keyspace information:"
        echo "$db_info" >> "$LOG_FILE"
        
        # Extract total keys count
        local total_keys=0
        while IFS= read -r line; do
            if [[ $line =~ db[0-9]+:keys=([0-9]+) ]]; then
                local db_keys="${BASH_REMATCH[1]}"
                total_keys=$((total_keys + db_keys))
            fi
        done <<< "$db_info"
        
        log "Total keys across all databases: $total_keys"
        send_cloudwatch_metric "TotalKeys" "$total_keys" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
        
        if [[ $total_keys -gt $KEY_SPACE_THRESHOLD ]]; then
            warning "High number of keys: $total_keys (threshold: $KEY_SPACE_THRESHOLD)"
        fi
    else
        log "No keyspace information available"
    fi
}

# Check replication status
check_replication() {
    log "Checking replication status..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    local replication_info=$($redis_cmd info replication 2>/dev/null || echo "")
    
    if [[ -n "$replication_info" ]]; then
        local role=$(echo "$replication_info" | grep "role:" | cut -d: -f2 | tr -d '\r')
        
        log "Replication role: $role"
        
        if [[ "$role" == "master" ]]; then
            local connected_slaves=$(echo "$replication_info" | grep "connected_slaves:" | cut -d: -f2 | tr -d '\r')
            log "Connected slaves: $connected_slaves"
            
            send_cloudwatch_metric "ConnectedSlaves" "$connected_slaves" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
            
            if [[ $connected_slaves -eq 0 ]]; then
                warning "No slaves connected to master Redis instance"
            fi
            
        elif [[ "$role" == "slave" ]]; then
            local master_link_status=$(echo "$replication_info" | grep "master_link_status:" | cut -d: -f2 | tr -d '\r')
            local master_last_io_seconds_ago=$(echo "$replication_info" | grep "master_last_io_seconds_ago:" | cut -d: -f2 | tr -d '\r')
            
            log "Master link status: $master_link_status"
            log "Master last IO: ${master_last_io_seconds_ago}s ago"
            
            if [[ "$master_link_status" != "up" ]]; then
                error "Master link is down: $master_link_status"
            fi
            
            if [[ $master_last_io_seconds_ago -gt 30 ]]; then
                warning "Master last IO was ${master_last_io_seconds_ago}s ago (threshold: 30s)"
            fi
        fi
    fi
}

# Generate comprehensive report
generate_report() {
    log "Generating monitoring report..."
    
    local report_file="/tmp/redis_monitor_report_${TIMESTAMP}.txt"
    
    cat > "$report_file" << EOF
Redis Monitoring Report
Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')
Hostname: $HOSTNAME
Environment: $ENVIRONMENT
Redis Host: $REDIS_HOST:$REDIS_PORT

EOF
    
    # Append metrics if available
    if [[ -f "$METRICS_FILE" ]]; then
        echo "=== CURRENT METRICS ===" >> "$report_file"
        cat "$METRICS_FILE" >> "$report_file"
        echo "" >> "$report_file"
    fi
    
    # Append recent log entries
    echo "=== RECENT LOG ENTRIES ===" >> "$report_file"
    tail -50 "$LOG_FILE" >> "$report_file"
    
    success "Monitoring report generated: $report_file"
    echo "$report_file"
}

# Main monitoring function
main() {
    local start_time=$(date +%s)
    
    log "Starting Redis monitoring check..."
    log "Host: $REDIS_HOST:$REDIS_PORT, Environment: $ENVIRONMENT"
    
    local checks_passed=0
    local checks_failed=0
    
    # Core connectivity check
    if check_redis_connection; then
        ((checks_passed++))
    else
        ((checks_failed++))
        return 1  # Exit early if Redis is not available
    fi
    
    # Metrics collection
    if get_redis_metrics; then
        ((checks_passed++))
    else
        ((checks_failed++))
    fi
    
    # Additional checks
    check_slow_queries && ((checks_passed++)) || ((checks_failed++))
    check_redis_config && ((checks_passed++)) || ((checks_failed++))
    check_keyspace && ((checks_passed++)) || ((checks_failed++))
    check_replication && ((checks_passed++)) || ((checks_failed++))
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Monitoring completed in ${duration} seconds"
    log "Checks passed: $checks_passed, failed: $checks_failed"
    
    # Generate report
    local report_file=$(generate_report)
    
    # Send summary metric
    send_cloudwatch_metric "MonitoringChecksPassed" "$checks_passed" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    send_cloudwatch_metric "MonitoringChecksFailed" "$checks_failed" "Count" "Host=$HOSTNAME,Environment=$ENVIRONMENT"
    
    if [[ $checks_failed -gt 0 ]]; then
        warning "Redis monitoring completed with $checks_failed failed checks"
        return 1
    else
        success "All Redis monitoring checks passed successfully!"
        return 0
    fi
}

# Show usage information
show_usage() {
    cat << EOF
Redis Monitoring Script

Usage: $0 [OPTIONS]

Options:
    -h, --help              Show this help message
    -H, --host HOST         Redis host (default: localhost)
    -p, --port PORT         Redis port (default: 6379)
    -a, --auth PASSWORD     Redis password
    -e, --environment ENV   Environment name (default: dev)
    -m, --memory-threshold  Memory usage alert threshold percentage (default: 85)
    -c, --connection-threshold  Connection count alert threshold (default: 1000)
    --alert-email EMAIL     Email for alerts
    --slack-webhook URL     Slack webhook URL for notifications
    --cloudwatch-namespace  CloudWatch namespace (default: Oddiya/Redis)

Environment Variables:
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, ENVIRONMENT, MEMORY_THRESHOLD,
    CONNECTION_THRESHOLD, ALERT_EMAIL, SLACK_WEBHOOK, CLOUDWATCH_NAMESPACE

Examples:
    $0                                          # Basic monitoring check
    $0 --host redis.example.com --auth secret  # Remote Redis with auth
    $0 --memory-threshold 90 --environment prod # Custom thresholds for production
EOF
}

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -H|--host)
                REDIS_HOST="$2"
                shift 2
                ;;
            -p|--port)
                REDIS_PORT="$2"
                shift 2
                ;;
            -a|--auth)
                REDIS_PASSWORD="$2"
                shift 2
                ;;
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -m|--memory-threshold)
                MEMORY_THRESHOLD="$2"
                shift 2
                ;;
            -c|--connection-threshold)
                CONNECTION_THRESHOLD="$2"
                shift 2
                ;;
            --alert-email)
                ALERT_EMAIL="$2"
                shift 2
                ;;
            --slack-webhook)
                SLACK_WEBHOOK="$2"
                shift 2
                ;;
            --cloudwatch-namespace)
                CLOUDWATCH_NAMESPACE="$2"
                shift 2
                ;;
            *)
                error "Unknown argument: $1"
                ;;
        esac
    done
}

# Trap signals for cleanup
trap 'error "Monitoring script interrupted"' INT TERM

# Parse arguments and run main function
parse_arguments "$@"
main

exit 0