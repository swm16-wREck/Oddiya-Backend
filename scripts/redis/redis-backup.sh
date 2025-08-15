#!/bin/bash
# Redis Backup Script
# Agent 6 - Monitoring & Operations Engineer

set -e

# Configuration variables
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/redis/backups}"
S3_BUCKET="${S3_BUCKET:-oddiya-redis-backups}"
S3_PREFIX="${S3_PREFIX:-redis-backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
COMPRESSION="${COMPRESSION:-true}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"

# Logging configuration
LOG_FILE="${LOG_FILE:-/var/log/redis/backup.log}"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")
HOSTNAME=$(hostname -s)
BACKUP_NAME="redis_backup_${HOSTNAME}_${TIMESTAMP}"

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
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "${LOG_FILE}"
    send_alert "ERROR" "$1"
    exit 1
}

# Send alert notifications
send_alert() {
    local level="$1"
    local message="$2"
    
    # Email notification
    if [[ -n "$ALERT_EMAIL" ]]; then
        echo "Redis Backup Alert [$level]: $message" | \
            mail -s "Redis Backup Alert - $HOSTNAME" "$ALERT_EMAIL" 2>/dev/null || true
    fi
    
    # Slack notification
    if [[ -n "$SLACK_WEBHOOK" ]]; then
        local color="good"
        [[ "$level" == "ERROR" ]] && color="danger"
        [[ "$level" == "WARNING" ]] && color="warning"
        
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"attachments\":[{\"color\":\"$color\",\"title\":\"Redis Backup Alert\",\"text\":\"**$level**: $message\",\"fields\":[{\"title\":\"Host\",\"value\":\"$HOSTNAME\",\"short\":true},{\"title\":\"Environment\",\"value\":\"$ENVIRONMENT\",\"short\":true}]}]}" \
            "$SLACK_WEBHOOK" 2>/dev/null || true
    fi
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if redis-cli is available
    if ! command -v "$REDIS_CLI" &> /dev/null; then
        error "redis-cli not found. Please install Redis CLI tools."
    fi
    
    # Check if Redis is accessible
    if [[ -n "$REDIS_PASSWORD" ]]; then
        if ! "$REDIS_CLI" -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" ping &>/dev/null; then
            error "Cannot connect to Redis at $REDIS_HOST:$REDIS_PORT"
        fi
    else
        if ! "$REDIS_CLI" -h "$REDIS_HOST" -p "$REDIS_PORT" ping &>/dev/null; then
            error "Cannot connect to Redis at $REDIS_HOST:$REDIS_PORT"
        fi
    fi
    
    # Check AWS CLI for S3 operations
    if ! command -v aws &> /dev/null; then
        warning "AWS CLI not found. S3 upload will be skipped."
        S3_BUCKET=""
    fi
    
    # Create backup directory
    mkdir -p "$BACKUP_DIR"
    if [[ ! -w "$BACKUP_DIR" ]]; then
        error "Backup directory $BACKUP_DIR is not writable"
    fi
    
    success "Prerequisites check completed"
}

# Get Redis information
get_redis_info() {
    log "Gathering Redis information..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get Redis version and basic info
    REDIS_VERSION=$($redis_cmd info server | grep redis_version | cut -d: -f2 | tr -d '\r')
    REDIS_MODE=$($redis_cmd info server | grep redis_mode | cut -d: -f2 | tr -d '\r')
    USED_MEMORY=$($redis_cmd info memory | grep used_memory_human | cut -d: -f2 | tr -d '\r')
    CONNECTED_CLIENTS=$($redis_cmd info clients | grep connected_clients | cut -d: -f2 | tr -d '\r')
    
    log "Redis Version: $REDIS_VERSION"
    log "Redis Mode: $REDIS_MODE"
    log "Memory Usage: $USED_MEMORY"
    log "Connected Clients: $CONNECTED_CLIENTS"
}

# Create RDB backup
create_rdb_backup() {
    log "Creating RDB backup..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    local rdb_file="$BACKUP_DIR/${BACKUP_NAME}.rdb"
    
    # Trigger background save
    log "Triggering BGSAVE..."
    if ! $redis_cmd bgsave; then
        error "Failed to trigger BGSAVE"
    fi
    
    # Wait for BGSAVE to complete
    log "Waiting for BGSAVE to complete..."
    local max_wait=300  # 5 minutes
    local wait_time=0
    
    while [[ $wait_time -lt $max_wait ]]; do
        if $redis_cmd lastsave | grep -q "$(date +%s)"; then
            break
        fi
        
        # Check if BGSAVE is still running
        local bgsave_status=$($redis_cmd info persistence | grep rdb_bgsave_in_progress | cut -d: -f2 | tr -d '\r')
        if [[ "$bgsave_status" == "0" ]]; then
            break
        fi
        
        sleep 5
        wait_time=$((wait_time + 5))
        
        if [[ $((wait_time % 30)) -eq 0 ]]; then
            log "Still waiting for BGSAVE... (${wait_time}s elapsed)"
        fi
    done
    
    if [[ $wait_time -ge $max_wait ]]; then
        error "BGSAVE did not complete within $max_wait seconds"
    fi
    
    # Create backup using redis-cli --rdb
    log "Creating RDB backup file..."
    if ! $redis_cmd --rdb "$rdb_file"; then
        error "Failed to create RDB backup"
    fi
    
    # Verify backup file exists and has content
    if [[ ! -f "$rdb_file" ]] || [[ ! -s "$rdb_file" ]]; then
        error "RDB backup file was not created or is empty"
    fi
    
    local backup_size=$(du -h "$rdb_file" | cut -f1)
    success "RDB backup created: $rdb_file ($backup_size)"
    
    echo "$rdb_file"
}

# Create AOF backup if enabled
create_aof_backup() {
    log "Checking for AOF backup..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Check if AOF is enabled
    local aof_enabled=$($redis_cmd config get appendonly | tail -1)
    
    if [[ "$aof_enabled" == "yes" ]]; then
        log "AOF is enabled, creating AOF backup..."
        
        # Get AOF file path
        local aof_filename=$($redis_cmd config get appendfilename | tail -1)
        local aof_dir=$($redis_cmd config get dir | tail -1)
        local aof_source="$aof_dir/$aof_filename"
        local aof_backup="$BACKUP_DIR/${BACKUP_NAME}.aof"
        
        # Rewrite AOF first to optimize it
        log "Triggering AOF rewrite..."
        $redis_cmd bgrewriteaof
        
        # Wait a moment for rewrite to start
        sleep 2
        
        # Wait for AOF rewrite to complete
        local max_wait=300
        local wait_time=0
        
        while [[ $wait_time -lt $max_wait ]]; do
            local aof_rewrite_status=$($redis_cmd info persistence | grep aof_rewrite_in_progress | cut -d: -f2 | tr -d '\r')
            if [[ "$aof_rewrite_status" == "0" ]]; then
                break
            fi
            
            sleep 5
            wait_time=$((wait_time + 5))
            
            if [[ $((wait_time % 30)) -eq 0 ]]; then
                log "Still waiting for AOF rewrite... (${wait_time}s elapsed)"
            fi
        done
        
        # Copy AOF file (this might need to be done on the Redis server directly)
        if [[ "$REDIS_HOST" == "localhost" || "$REDIS_HOST" == "127.0.0.1" ]]; then
            if [[ -f "$aof_source" ]]; then
                cp "$aof_source" "$aof_backup"
                local aof_size=$(du -h "$aof_backup" | cut -f1)
                success "AOF backup created: $aof_backup ($aof_size)"
                echo "$aof_backup"
            else
                warning "AOF file not found at $aof_source"
            fi
        else
            warning "AOF backup skipped - Redis is on remote server"
        fi
    else
        log "AOF is not enabled, skipping AOF backup"
    fi
}

# Compress backup files
compress_backups() {
    local files=("$@")
    
    if [[ "$COMPRESSION" != "true" ]]; then
        log "Compression is disabled"
        return 0
    fi
    
    log "Compressing backup files..."
    
    local compressed_files=()
    
    for file in "${files[@]}"; do
        if [[ -f "$file" ]]; then
            log "Compressing $file..."
            
            if command -v pigz &> /dev/null; then
                # Use pigz for parallel compression if available
                pigz -9 "$file"
            else
                # Fall back to regular gzip
                gzip -9 "$file"
            fi
            
            local compressed_file="${file}.gz"
            if [[ -f "$compressed_file" ]]; then
                local compressed_size=$(du -h "$compressed_file" | cut -f1)
                success "Compressed: $compressed_file ($compressed_size)"
                compressed_files+=("$compressed_file")
            else
                error "Compression failed for $file"
            fi
        fi
    done
    
    echo "${compressed_files[@]}"
}

# Upload to S3
upload_to_s3() {
    local files=("$@")
    
    if [[ -z "$S3_BUCKET" ]]; then
        log "S3 upload is disabled or AWS CLI not available"
        return 0
    fi
    
    log "Uploading backups to S3..."
    
    for file in "${files[@]}"; do
        if [[ -f "$file" ]]; then
            local s3_key="$S3_PREFIX/$ENVIRONMENT/$HOSTNAME/$(basename "$file")"
            
            log "Uploading $file to s3://$S3_BUCKET/$s3_key"
            
            if aws s3 cp "$file" "s3://$S3_BUCKET/$s3_key" \
                --storage-class STANDARD_IA \
                --metadata "hostname=$HOSTNAME,environment=$ENVIRONMENT,timestamp=$TIMESTAMP"; then
                success "Uploaded to S3: s3://$S3_BUCKET/$s3_key"
            else
                error "Failed to upload $file to S3"
            fi
        fi
    done
}

# Create backup manifest
create_manifest() {
    local files=("$@")
    
    local manifest_file="$BACKUP_DIR/${BACKUP_NAME}_manifest.json"
    
    log "Creating backup manifest..."
    
    cat > "$manifest_file" << EOF
{
    "backup_name": "$BACKUP_NAME",
    "timestamp": "$TIMESTAMP",
    "hostname": "$HOSTNAME",
    "environment": "$ENVIRONMENT",
    "redis_info": {
        "version": "$REDIS_VERSION",
        "mode": "$REDIS_MODE",
        "used_memory": "$USED_MEMORY",
        "connected_clients": "$CONNECTED_CLIENTS"
    },
    "backup_files": [
EOF

    local first=true
    for file in "${files[@]}"; do
        if [[ -f "$file" ]]; then
            [[ "$first" == false ]] && echo "," >> "$manifest_file"
            first=false
            
            local file_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            local file_hash=$(md5sum "$file" 2>/dev/null | cut -d' ' -f1 || md5 -q "$file" 2>/dev/null || echo "unknown")
            
            cat >> "$manifest_file" << EOF
        {
            "filename": "$(basename "$file")",
            "path": "$file",
            "size_bytes": $file_size,
            "md5_hash": "$file_hash",
            "type": "$(if [[ "$file" == *.rdb* ]]; then echo "rdb"; elif [[ "$file" == *.aof* ]]; then echo "aof"; else echo "unknown"; fi)"
        }
EOF
        fi
    done

    cat >> "$manifest_file" << EOF
    ],
    "s3_bucket": "$S3_BUCKET",
    "s3_prefix": "$S3_PREFIX"
}
EOF

    success "Backup manifest created: $manifest_file"
    echo "$manifest_file"
}

# Clean up old backups
cleanup_old_backups() {
    log "Cleaning up old backups (older than $RETENTION_DAYS days)..."
    
    # Clean up local backups
    local deleted_count=0
    while IFS= read -r -d '' file; do
        rm -f "$file"
        ((deleted_count++))
        log "Deleted old backup: $file"
    done < <(find "$BACKUP_DIR" -name "redis_backup_${HOSTNAME}_*" -mtime "+$RETENTION_DAYS" -print0 2>/dev/null)
    
    success "Deleted $deleted_count old local backup files"
    
    # Clean up S3 backups if configured
    if [[ -n "$S3_BUCKET" ]] && command -v aws &> /dev/null; then
        log "Cleaning up old S3 backups..."
        
        local cutoff_date=$(date -d "$RETENTION_DAYS days ago" +%Y-%m-%d)
        
        # List and delete old S3 objects
        aws s3api list-objects-v2 \
            --bucket "$S3_BUCKET" \
            --prefix "$S3_PREFIX/$ENVIRONMENT/$HOSTNAME/" \
            --query "Contents[?LastModified<'$cutoff_date'].[Key]" \
            --output text | \
        while read -r key; do
            if [[ -n "$key" && "$key" != "None" ]]; then
                aws s3 rm "s3://$S3_BUCKET/$key"
                log "Deleted old S3 backup: s3://$S3_BUCKET/$key"
            fi
        done
    fi
}

# Generate backup report
generate_report() {
    local files=("$@")
    local manifest_file="$1"
    
    log "Generating backup report..."
    
    local total_size=0
    local file_count=0
    
    for file in "${files[@]}"; do
        if [[ -f "$file" && "$file" != "$manifest_file" ]]; then
            local file_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            total_size=$((total_size + file_size))
            ((file_count++))
        fi
    done
    
    # Convert bytes to human readable
    local human_size=$(numfmt --to=iec-i --suffix=B $total_size 2>/dev/null || echo "${total_size} bytes")
    
    local report="Redis Backup Completed Successfully

Backup Details:
- Backup Name: $BACKUP_NAME
- Timestamp: $(date -d "@$(date +%s)" '+%Y-%m-%d %H:%M:%S %Z')
- Hostname: $HOSTNAME
- Environment: $ENVIRONMENT

Redis Information:
- Version: $REDIS_VERSION
- Mode: $REDIS_MODE
- Memory Usage: $USED_MEMORY
- Connected Clients: $CONNECTED_CLIENTS

Backup Files:
- Total Files: $file_count
- Total Size: $human_size"

    [[ -n "$S3_BUCKET" ]] && report+="
- S3 Location: s3://$S3_BUCKET/$S3_PREFIX/$ENVIRONMENT/$HOSTNAME/"

    echo "$report"
    
    # Send success notification
    send_alert "SUCCESS" "Redis backup completed successfully. Total size: $human_size, Files: $file_count"
}

# Main execution function
main() {
    local start_time=$(date +%s)
    
    log "Starting Redis backup process..."
    log "Host: $REDIS_HOST:$REDIS_PORT, Environment: $ENVIRONMENT"
    
    # Check prerequisites
    check_prerequisites
    
    # Get Redis information
    get_redis_info
    
    # Create backups
    local backup_files=()
    
    # RDB backup
    local rdb_file=$(create_rdb_backup)
    [[ -n "$rdb_file" ]] && backup_files+=("$rdb_file")
    
    # AOF backup
    local aof_file=$(create_aof_backup)
    [[ -n "$aof_file" ]] && backup_files+=("$aof_file")
    
    if [[ ${#backup_files[@]} -eq 0 ]]; then
        error "No backup files were created"
    fi
    
    # Compress backups
    if [[ "$COMPRESSION" == "true" ]]; then
        mapfile -t backup_files < <(compress_backups "${backup_files[@]}")
    fi
    
    # Create manifest
    local manifest_file=$(create_manifest "${backup_files[@]}")
    backup_files+=("$manifest_file")
    
    # Upload to S3
    upload_to_s3 "${backup_files[@]}"
    
    # Clean up old backups
    cleanup_old_backups
    
    # Generate report
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Backup process completed in ${duration} seconds"
    generate_report "${backup_files[@]}"
    
    success "Redis backup process completed successfully!"
}

# Show usage information
show_usage() {
    cat << EOF
Redis Backup Script

Usage: $0 [OPTIONS]

Options:
    -h, --help              Show this help message
    -H, --host HOST         Redis host (default: localhost)
    -p, --port PORT         Redis port (default: 6379)
    -a, --auth PASSWORD     Redis password
    -d, --dir DIRECTORY     Backup directory (default: /var/lib/redis/backups)
    -b, --bucket BUCKET     S3 bucket name
    -r, --retention DAYS    Retention period in days (default: 30)
    -e, --environment ENV   Environment name (default: dev)
    -c, --compress          Enable compression (default: true)
    -n, --no-compress       Disable compression
    --alert-email EMAIL     Email for alerts
    --slack-webhook URL     Slack webhook URL for notifications

Environment Variables:
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, BACKUP_DIR, S3_BUCKET,
    RETENTION_DAYS, COMPRESSION, ENVIRONMENT, ALERT_EMAIL, SLACK_WEBHOOK

Examples:
    $0                                          # Basic backup
    $0 --host redis.example.com --auth secret  # Remote Redis with auth
    $0 --bucket my-backups --retention 7       # S3 backup with 7 day retention
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
            -d|--dir)
                BACKUP_DIR="$2"
                shift 2
                ;;
            -b|--bucket)
                S3_BUCKET="$2"
                shift 2
                ;;
            -r|--retention)
                RETENTION_DAYS="$2"
                shift 2
                ;;
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -c|--compress)
                COMPRESSION="true"
                shift
                ;;
            -n|--no-compress)
                COMPRESSION="false"
                shift
                ;;
            --alert-email)
                ALERT_EMAIL="$2"
                shift 2
                ;;
            --slack-webhook)
                SLACK_WEBHOOK="$2"
                shift 2
                ;;
            *)
                error "Unknown argument: $1"
                ;;
        esac
    done
}

# Trap signals for cleanup
trap 'error "Script interrupted"' INT TERM

# Parse arguments and run main function
parse_arguments "$@"
main

exit 0