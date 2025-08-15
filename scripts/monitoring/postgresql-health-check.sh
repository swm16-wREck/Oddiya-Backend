#!/bin/bash

# PostgreSQL Health Check Script for Phase 2
# Comprehensive monitoring script for Aurora PostgreSQL cluster

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENVIRONMENT="${1:-dev}"
ALERT_THRESHOLD_CPU=80
ALERT_THRESHOLD_CONNECTIONS=80
ALERT_THRESHOLD_STORAGE_GB=2
LOG_FILE="/tmp/postgresql-health-$(date +%Y%m%d).log"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

# Get database credentials from AWS Secrets Manager
get_db_credentials() {
    local secret_name="oddiya-${ENVIRONMENT}-db-password"
    
    log_info "Retrieving database credentials for environment: $ENVIRONMENT"
    
    if ! DB_CREDENTIALS=$(aws secretsmanager get-secret-value \
        --secret-id "$secret_name" \
        --region ap-northeast-2 \
        --query SecretString \
        --output text 2>/dev/null); then
        log_error "Failed to retrieve database credentials from $secret_name"
        return 1
    fi
    
    DB_HOST=$(echo "$DB_CREDENTIALS" | jq -r '.host // "localhost"')
    DB_PORT=$(echo "$DB_CREDENTIALS" | jq -r '.port // "5432"')
    DB_NAME=$(echo "$DB_CREDENTIALS" | jq -r '.database // "oddiya"')
    DB_USER=$(echo "$DB_CREDENTIALS" | jq -r '.username')
    DB_PASSWORD=$(echo "$DB_CREDENTIALS" | jq -r '.password')
    
    if [[ -z "$DB_HOST" || "$DB_HOST" == "null" ]]; then
        log_error "Invalid database host in credentials"
        return 1
    fi
    
    log_success "Database credentials retrieved successfully"
}

# Test basic connectivity
test_connectivity() {
    log_info "Testing database connectivity..."
    
    if ! PGPASSWORD="$DB_PASSWORD" pg_isready \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t 10 >/dev/null 2>&1; then
        log_error "Database connectivity test failed"
        return 1
    fi
    
    log_success "Database connectivity test passed"
}

# Check PostgreSQL version and extensions
check_version_and_extensions() {
    log_info "Checking PostgreSQL version and extensions..."
    
    local version_info
    version_info=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT version();" 2>/dev/null || echo "Failed to get version")
    
    log_info "PostgreSQL Version: $version_info"
    
    # Check PostGIS extension
    local postgis_version
    postgis_version=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT PostGIS_Version();" 2>/dev/null || echo "PostGIS not available")
    
    if [[ "$postgis_version" == *"PostGIS not available"* ]]; then
        log_warn "PostGIS extension not available or not installed"
    else
        log_success "PostGIS Version: $postgis_version"
    fi
}

# Check database performance metrics
check_performance_metrics() {
    log_info "Checking database performance metrics..."
    
    # Check connection count
    local connection_count
    connection_count=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB_NAME';" 2>/dev/null || echo "0")
    
    log_info "Active connections: $connection_count"
    
    if [[ $connection_count -gt $ALERT_THRESHOLD_CONNECTIONS ]]; then
        log_warn "High connection count: $connection_count (threshold: $ALERT_THRESHOLD_CONNECTIONS)"
    else
        log_success "Connection count is within normal range"
    fi
    
    # Check for long-running queries
    local long_queries
    long_queries=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT count(*) FROM pg_stat_activity 
               WHERE datname = '$DB_NAME' 
                 AND state = 'active' 
                 AND now() - query_start > INTERVAL '30 seconds';" 2>/dev/null || echo "0")
    
    if [[ $long_queries -gt 0 ]]; then
        log_warn "Long-running queries detected: $long_queries queries > 30 seconds"
        
        # Log details of long-running queries
        PGPASSWORD="$DB_PASSWORD" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$DB_NAME" \
            -c "SELECT pid, now() - query_start AS duration, state, left(query, 100) as query_preview
                FROM pg_stat_activity
                WHERE datname = '$DB_NAME'
                  AND state = 'active'
                  AND now() - query_start > INTERVAL '30 seconds'
                ORDER BY duration DESC;" >> "$LOG_FILE" 2>&1
    else
        log_success "No long-running queries detected"
    fi
}

# Check table and index statistics
check_table_statistics() {
    log_info "Checking table and index statistics..."
    
    # Check table sizes
    local table_sizes
    table_sizes=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "SELECT 
                schemaname,
                tablename,
                pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
                n_live_tup
            FROM pg_stat_user_tables 
            ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC 
            LIMIT 5;" 2>/dev/null) || table_sizes="Failed to retrieve table sizes"
    
    log_info "Largest tables:"
    echo "$table_sizes" | tail -n +3 >> "$LOG_FILE"
    
    # Check unused indexes
    local unused_indexes
    unused_indexes=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT count(*) FROM pg_stat_user_indexes 
               WHERE idx_scan = 0 AND schemaname = 'public';" 2>/dev/null || echo "0")
    
    if [[ $unused_indexes -gt 0 ]]; then
        log_warn "Unused indexes detected: $unused_indexes indexes with zero scans"
    else
        log_success "All indexes are being used"
    fi
}

# Check spatial data integrity (PostGIS specific)
check_spatial_integrity() {
    log_info "Checking spatial data integrity..."
    
    # Check if spatial functions are working
    local spatial_test
    spatial_test=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT ST_AsText(ST_MakePoint(126.9780, 37.5665)) AS seoul_test;" 2>/dev/null || echo "FAILED")
    
    if [[ "$spatial_test" == *"POINT"* ]]; then
        log_success "Spatial functions are working correctly"
    else
        log_warn "Spatial functions test failed or PostGIS not available"
    fi
    
    # Check places table spatial data if it exists
    local places_spatial_check
    places_spatial_check=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT 
                   COUNT(*) as total_places,
                   COUNT(location) as places_with_geometry
               FROM places;" 2>/dev/null || echo "0|0")
    
    if [[ "$places_spatial_check" != "0|0" ]]; then
        local total_places=$(echo "$places_spatial_check" | cut -d'|' -f1 | xargs)
        local spatial_places=$(echo "$places_spatial_check" | cut -d'|' -f2 | xargs)
        log_info "Places: $total_places total, $spatial_places with geometry"
        
        if [[ $total_places -gt 0 && $spatial_places -lt $total_places ]]; then
            log_warn "Some places missing spatial geometry: $((total_places - spatial_places)) places"
        fi
    fi
}

# Check CloudWatch metrics
check_cloudwatch_metrics() {
    log_info "Checking recent CloudWatch alarms..."
    
    local cluster_name="oddiya-phase2-cluster"
    local recent_alarms
    
    # Check for recent alarms in the last 24 hours
    recent_alarms=$(aws cloudwatch describe-alarms \
        --state-value ALARM \
        --query "MetricAlarms[?StateUpdatedTimestamp>=\`$(date -d '24 hours ago' -u +%Y-%m-%dT%H:%M:%S.%3NZ)\`].{Name:AlarmName,State:StateValue,Reason:StateReason,Time:StateUpdatedTimestamp}" \
        --output table 2>/dev/null || echo "Failed to retrieve CloudWatch alarms")
    
    if [[ "$recent_alarms" == *"ALARM"* ]]; then
        log_warn "Recent CloudWatch alarms detected:"
        echo "$recent_alarms" >> "$LOG_FILE"
    else
        log_success "No recent CloudWatch alarms"
    fi
    
    # Get current CPU utilization
    local cpu_metric
    cpu_metric=$(aws cloudwatch get-metric-statistics \
        --namespace AWS/RDS \
        --metric-name CPUUtilization \
        --dimensions Name=DBClusterIdentifier,Value="$cluster_name" \
        --start-time "$(date -d '10 minutes ago' -u +%Y-%m-%dT%H:%M:%S)" \
        --end-time "$(date -u +%Y-%m-%dT%H:%M:%S)" \
        --period 300 \
        --statistics Average \
        --query 'Datapoints[0].Average' \
        --output text 2>/dev/null || echo "None")
    
    if [[ "$cpu_metric" != "None" && "$cpu_metric" != "null" ]]; then
        local cpu_value=$(echo "$cpu_metric" | cut -d'.' -f1)
        log_info "Current CPU utilization: ${cpu_value}%"
        
        if [[ $cpu_value -gt $ALERT_THRESHOLD_CPU ]]; then
            log_warn "High CPU utilization: ${cpu_value}% (threshold: ${ALERT_THRESHOLD_CPU}%)"
        fi
    fi
}

# Check application health endpoints
check_application_health() {
    log_info "Checking application health endpoints..."
    
    # Get load balancer DNS from Terraform output or environment variable
    local app_url="${APP_URL:-http://localhost:8080}"
    
    # Check general health
    if curl -f -s "$app_url/actuator/health" >/dev/null 2>&1; then
        log_success "Application health endpoint is responsive"
    else
        log_warn "Application health endpoint is not responsive"
    fi
    
    # Check database-specific health
    if curl -f -s "$app_url/actuator/health/db" >/dev/null 2>&1; then
        log_success "Database health check passed from application"
    else
        log_warn "Database health check failed from application"
    fi
    
    # Check HikariCP metrics if available
    local pool_active
    pool_active=$(curl -s "$app_url/actuator/metrics/hikaricp.connections.active" 2>/dev/null | \
                 jq -r '.measurements[0].value // "unknown"' 2>/dev/null || echo "unknown")
    
    if [[ "$pool_active" != "unknown" ]]; then
        log_info "HikariCP active connections: $pool_active"
    fi
}

# Generate summary report
generate_summary() {
    log_info "=== PostgreSQL Health Check Summary ==="
    log_info "Environment: $ENVIRONMENT"
    log_info "Database Host: $DB_HOST"
    log_info "Check Time: $(date)"
    log_info "Log File: $LOG_FILE"
    
    # Count issues
    local warnings=$(grep -c "\[WARN\]" "$LOG_FILE" || echo "0")
    local errors=$(grep -c "\[ERROR\]" "$LOG_FILE" || echo "0")
    
    if [[ $errors -gt 0 ]]; then
        log_error "Health check completed with $errors errors and $warnings warnings"
        return 1
    elif [[ $warnings -gt 0 ]]; then
        log_warn "Health check completed with $warnings warnings"
        return 2
    else
        log_success "Health check completed successfully - no issues detected"
        return 0
    fi
}

# Send alert if issues detected
send_alert() {
    local exit_code=$1
    local sns_topic_arn="${SNS_TOPIC_ARN:-}"
    
    if [[ -n "$sns_topic_arn" && $exit_code -ne 0 ]]; then
        local message="PostgreSQL Health Check Alert - Environment: $ENVIRONMENT"
        local subject="[Oddiya] Database Health Check "
        
        if [[ $exit_code -eq 1 ]]; then
            subject+="CRITICAL"
            message+="\n\nCritical issues detected. Immediate attention required."
        else
            subject+="WARNING"
            message+="\n\nWarnings detected. Please review."
        fi
        
        message+="\n\nLog file: $LOG_FILE"
        message+="\n\nLast 10 log entries:\n$(tail -n 10 "$LOG_FILE")"
        
        aws sns publish \
            --topic-arn "$sns_topic_arn" \
            --subject "$subject" \
            --message "$message" >/dev/null 2>&1 || \
            log_warn "Failed to send SNS alert"
    fi
}

# Main execution function
main() {
    log_info "Starting PostgreSQL health check for environment: $ENVIRONMENT"
    
    # Check prerequisites
    local missing_tools=()
    command -v aws >/dev/null 2>&1 || missing_tools+=("aws")
    command -v psql >/dev/null 2>&1 || missing_tools+=("psql")
    command -v jq >/dev/null 2>&1 || missing_tools+=("jq")
    command -v pg_isready >/dev/null 2>&1 || missing_tools+=("pg_isready")
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        exit 1
    fi
    
    # Execute health checks
    if ! get_db_credentials; then
        log_error "Failed to get database credentials"
        exit 1
    fi
    
    local checks=(
        "test_connectivity"
        "check_version_and_extensions"
        "check_performance_metrics"
        "check_table_statistics"
        "check_spatial_integrity"
        "check_cloudwatch_metrics"
        "check_application_health"
    )
    
    for check in "${checks[@]}"; do
        if ! "$check"; then
            log_warn "Check $check encountered issues but continuing..."
        fi
    done
    
    # Generate summary and exit with appropriate code
    if ! generate_summary; then
        local exit_code=$?
        send_alert $exit_code
        exit $exit_code
    fi
    
    log_success "All health checks completed successfully"
}

# Show usage if help requested
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    cat << EOF
Usage: $0 [ENVIRONMENT]

PostgreSQL health check script for Oddiya Phase 2.

ENVIRONMENT:
  dev      - Development environment (default)
  staging  - Staging environment
  prod     - Production environment

Environment Variables:
  APP_URL           - Application URL for health checks (default: http://localhost:8080)
  SNS_TOPIC_ARN    - SNS topic for alerts (optional)

Examples:
  $0                # Check dev environment
  $0 prod          # Check production environment
  
  # With alerts enabled
  SNS_TOPIC_ARN=arn:aws:sns:... $0 prod

EOF
    exit 0
fi

# Run main function
main "$@"