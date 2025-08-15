#!/bin/bash
# PostgreSQL EC2 Setup Script
# Comprehensive PostgreSQL 15 installation and configuration for production use

set -e

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="/var/log/postgresql-setup.log"
POSTGRESQL_VERSION="15"
BACKUP_RETENTION_DAYS="30"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} [${level}] ${message}" | tee -a "$LOG_FILE"
}

log_info() {
    log "INFO" "${BLUE}$*${NC}"
}

log_warn() {
    log "WARN" "${YELLOW}$*${NC}"
}

log_error() {
    log "ERROR" "${RED}$*${NC}"
}

log_success() {
    log "SUCCESS" "${GREEN}$*${NC}"
}

# Error handling
error_exit() {
    log_error "$1"
    exit 1
}

# Check if running as root
check_root() {
    if [[ $EUID -ne 0 ]]; then
        error_exit "This script must be run as root"
    fi
}

# Get system information
get_system_info() {
    log_info "=== System Information ==="
    log_info "Hostname: $(hostname)"
    log_info "OS: $(cat /etc/os-release | grep PRETTY_NAME | cut -d'=' -f2 | tr -d '\"')"
    log_info "Kernel: $(uname -r)"
    log_info "Architecture: $(uname -m)"
    log_info "Memory: $(free -h | grep Mem | awk '{print $2}')"
    log_info "CPU Cores: $(nproc)"
    log_info "Disk Space: $(df -h / | tail -1 | awk '{print $4}') available"
    log_info "=================================="
}

# Validate environment variables
validate_environment() {
    log_info "Validating environment variables..."
    
    # Check required variables
    local required_vars=("DB_NAME" "DB_USERNAME" "DB_PASSWORD")
    for var in "${required_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            error_exit "Required environment variable $var is not set"
        fi
    done
    
    # Set defaults for optional variables
    export AWS_REGION="${AWS_REGION:-us-east-1}"
    export S3_BACKUP_BUCKET="${S3_BACKUP_BUCKET:-}"
    export ENABLE_POSTGIS="${ENABLE_POSTGIS:-true}"
    export MAX_CONNECTIONS="${MAX_CONNECTIONS:-100}"
    export SHARED_BUFFERS="${SHARED_BUFFERS:-256MB}"
    export EFFECTIVE_CACHE_SIZE="${EFFECTIVE_CACHE_SIZE:-1GB}"
    
    log_info "Environment validation completed"
}

# System preparation and updates
prepare_system() {
    log_info "Preparing system and installing dependencies..."
    
    # Update system
    yum update -y
    
    # Install EPEL repository
    yum install -y epel-release
    
    # Install required packages
    yum install -y \
        wget \
        curl \
        vim \
        htop \
        iotop \
        git \
        chrony \
        awscli \
        python3 \
        python3-pip \
        amazon-cloudwatch-agent \
        yum-utils \
        device-mapper-persistent-data \
        lvm2 \
        net-tools \
        telnet \
        nc \
        screen \
        tmux \
        rsync \
        logrotate \
        crontabs
    
    # Start and enable time synchronization
    systemctl enable chronyd
    systemctl start chronyd
    
    log_success "System preparation completed"
}

# Install PostgreSQL
install_postgresql() {
    log_info "Installing PostgreSQL ${POSTGRESQL_VERSION}..."
    
    # Install PostgreSQL repository
    yum install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-8-x86_64/pgdg-redhat-repo-latest.noarch.rpm
    
    # Install PostgreSQL packages
    yum install -y \
        "postgresql${POSTGRESQL_VERSION}-server" \
        "postgresql${POSTGRESQL_VERSION}-contrib" \
        "postgresql${POSTGRESQL_VERSION}-devel" \
        "postgresql${POSTGRESQL_VERSION}-docs"
    
    # Install PostGIS if enabled
    if [[ "$ENABLE_POSTGIS" == "true" ]]; then
        yum install -y "postgis34_${POSTGRESQL_VERSION}"
    fi
    
    log_success "PostgreSQL installation completed"
}

# Setup storage for PostgreSQL data
setup_storage() {
    log_info "Setting up storage for PostgreSQL data..."
    
    # Check if dedicated EBS volume exists
    if lsblk | grep -q nvme1n1; then
        log_info "Dedicated EBS volume found, formatting and mounting..."
        
        # Format the volume if not already formatted
        if ! blkid /dev/nvme1n1; then
            mkfs.ext4 /dev/nvme1n1
        fi
        
        # Create mount point
        mkdir -p "/var/lib/pgsql/${POSTGRESQL_VERSION}/data"
        
        # Mount the volume
        mount /dev/nvme1n1 "/var/lib/pgsql/${POSTGRESQL_VERSION}/data"
        
        # Add to fstab for persistent mounting
        if ! grep -q nvme1n1 /etc/fstab; then
            echo "/dev/nvme1n1 /var/lib/pgsql/${POSTGRESQL_VERSION}/data ext4 defaults,nofail 0 2" >> /etc/fstab
        fi
        
        log_success "Dedicated EBS volume mounted successfully"
    else
        log_warn "No dedicated EBS volume found, using root volume"
        mkdir -p "/var/lib/pgsql/${POSTGRESQL_VERSION}/data"
    fi
    
    # Set proper ownership
    chown -R postgres:postgres "/var/lib/pgsql/${POSTGRESQL_VERSION}/"
    
    log_success "Storage setup completed"
}

# Initialize PostgreSQL database
initialize_database() {
    log_info "Initializing PostgreSQL database..."
    
    # Initialize database
    sudo -u postgres "/usr/pgsql-${POSTGRESQL_VERSION}/bin/postgresql-${POSTGRESQL_VERSION}-setup" initdb
    
    log_success "Database initialization completed"
}

# Configure PostgreSQL
configure_postgresql() {
    log_info "Configuring PostgreSQL..."
    
    local config_file="/var/lib/pgsql/${POSTGRESQL_VERSION}/data/postgresql.conf"
    local hba_file="/var/lib/pgsql/${POSTGRESQL_VERSION}/data/pg_hba.conf"
    
    # Backup original configuration
    cp "$config_file" "${config_file}.backup"
    cp "$hba_file" "${hba_file}.backup"
    
    # Configure postgresql.conf
    cat >> "$config_file" << EOF

# ========================================
# Oddiya PostgreSQL Configuration
# ========================================

# Connection settings
listen_addresses = '*'
port = 5432
max_connections = ${MAX_CONNECTIONS}
superuser_reserved_connections = 3

# Memory settings
shared_buffers = ${SHARED_BUFFERS}
effective_cache_size = ${EFFECTIVE_CACHE_SIZE}
maintenance_work_mem = 64MB
work_mem = 4MB
huge_pages = try

# WAL settings
wal_level = replica
max_wal_size = 1GB
min_wal_size = 80MB
checkpoint_completion_target = 0.9
checkpoint_timeout = 5min

# Query planner settings
random_page_cost = 1.1
effective_io_concurrency = 200

# Logging settings
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_rotation_size = 50MB
log_rotation_age = 1d
log_truncate_on_rotation = on
log_min_duration_statement = 1000
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_statement = 'ddl'
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
log_temp_files = 0

# SSL settings
ssl = on
ssl_ciphers = 'HIGH:MEDIUM:+3DES:!aNULL'
ssl_prefer_server_ciphers = on

# Performance monitoring
shared_preload_libraries = 'pg_stat_statements'
pg_stat_statements.track = all
pg_stat_statements.max = 10000

# PostGIS settings (if enabled)
EOF

    if [[ "$ENABLE_POSTGIS" == "true" ]]; then
        echo "shared_preload_libraries = 'pg_stat_statements,postgis-3'" >> "$config_file"
    fi
    
    # Configure pg_hba.conf for security
    cat > "$hba_file" << EOF
# PostgreSQL Client Authentication Configuration
# TYPE  DATABASE        USER            ADDRESS                 METHOD

# Local connections
local   all             postgres                                peer
local   all             all                                     md5

# IPv4 local connections
host    all             all             127.0.0.1/32            md5

# IPv4 connections from VPC
host    all             all             10.0.0.0/16             md5

# IPv6 local connections
host    all             all             ::1/128                 md5

# Replication connections
host    replication     postgres        10.0.0.0/16             md5
EOF
    
    log_success "PostgreSQL configuration completed"
}

# Generate SSL certificates
setup_ssl() {
    log_info "Setting up SSL certificates..."
    
    local data_dir="/var/lib/pgsql/${POSTGRESQL_VERSION}/data"
    
    cd "$data_dir"
    
    # Generate self-signed certificate
    openssl req -new -x509 -days 365 -nodes -text \
        -out server.crt \
        -keyout server.key \
        -subj "/CN=postgresql-$(hostname)"
    
    # Set proper permissions
    chmod 600 server.key
    chmod 644 server.crt
    chown postgres:postgres server.key server.crt
    
    log_success "SSL certificates generated"
}

# Create archive directory for WAL files
setup_archive() {
    log_info "Setting up WAL archive directory..."
    
    local archive_dir="/var/lib/pgsql/${POSTGRESQL_VERSION}/archive"
    mkdir -p "$archive_dir"
    chown postgres:postgres "$archive_dir"
    chmod 750 "$archive_dir"
    
    # Configure archive command in postgresql.conf
    local config_file="/var/lib/pgsql/${POSTGRESQL_VERSION}/data/postgresql.conf"
    echo "archive_mode = on" >> "$config_file"
    echo "archive_command = 'test ! -f ${archive_dir}/%f && cp %p ${archive_dir}/%f'" >> "$config_file"
    
    log_success "WAL archive setup completed"
}

# Start and enable PostgreSQL service
start_postgresql() {
    log_info "Starting PostgreSQL service..."
    
    # Enable PostgreSQL service
    systemctl enable "postgresql-${POSTGRESQL_VERSION}"
    
    # Start PostgreSQL service
    systemctl start "postgresql-${POSTGRESQL_VERSION}"
    
    # Wait for PostgreSQL to start
    sleep 10
    
    # Verify PostgreSQL is running
    if systemctl is-active "postgresql-${POSTGRESQL_VERSION}" >/dev/null; then
        log_success "PostgreSQL service started successfully"
    else
        error_exit "Failed to start PostgreSQL service"
    fi
}

# Setup database and users
setup_database() {
    log_info "Setting up database and users..."
    
    # Change postgres user password
    sudo -u postgres psql -c "ALTER USER postgres PASSWORD '${DB_PASSWORD}';"
    
    # Create application database if it doesn't exist
    if [[ "$DB_NAME" != "postgres" ]]; then
        sudo -u postgres createdb "$DB_NAME"
        log_info "Created database: $DB_NAME"
    fi
    
    # Create application user if different from postgres
    if [[ "$DB_USERNAME" != "postgres" ]]; then
        sudo -u postgres psql -c "CREATE USER ${DB_USERNAME} WITH PASSWORD '${DB_PASSWORD}';"
        sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USERNAME};"
        log_info "Created user: $DB_USERNAME"
    fi
    
    # Install extensions
    sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
    
    if [[ "$ENABLE_POSTGIS" == "true" ]]; then
        log_info "Installing PostGIS extensions..."
        sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;"
        sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"
        sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;"
        sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;"
    fi
    
    log_success "Database and user setup completed"
}

# Setup backup system
setup_backup() {
    log_info "Setting up backup system..."
    
    # Create backup directories
    mkdir -p /opt/postgresql/{scripts,backups,logs}
    
    # Create backup script
    cat > /opt/postgresql/scripts/backup.sh << 'BACKUP_SCRIPT_EOF'
#!/bin/bash
# PostgreSQL Backup Script

set -e

# Configuration
DB_NAME="${1:-$DB_NAME}"
DB_USERNAME="${2:-$DB_USERNAME}"
S3_BUCKET="${3:-$S3_BACKUP_BUCKET}"
AWS_REGION="${4:-$AWS_REGION}"

BACKUP_DIR="/opt/postgresql/backups"
LOG_FILE="/opt/postgresql/logs/backup.log"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="postgresql_${DB_NAME}_${TIMESTAMP}.sql"

# Logging function
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

log "Starting backup of database: $DB_NAME"

# Create backup
if pg_dump -h localhost -U "$DB_USERNAME" -d "$DB_NAME" -f "$BACKUP_DIR/$BACKUP_FILE"; then
    log "Database dump completed successfully"
else
    log "ERROR: Database dump failed"
    exit 1
fi

# Compress backup
gzip "$BACKUP_DIR/$BACKUP_FILE"
BACKUP_FILE="${BACKUP_FILE}.gz"

# Upload to S3 if bucket is configured
if [[ -n "$S3_BUCKET" ]]; then
    if aws s3 cp "$BACKUP_DIR/$BACKUP_FILE" "s3://$S3_BUCKET/postgresql-backups/$BACKUP_FILE" --region "$AWS_REGION"; then
        log "Backup uploaded to S3 successfully"
        # Remove local copy after successful upload
        rm -f "$BACKUP_DIR/$BACKUP_FILE"
    else
        log "ERROR: Failed to upload backup to S3"
    fi
fi

# Clean up old local backups
find "$BACKUP_DIR" -name "postgresql_*.sql.gz" -mtime +7 -delete

log "Backup completed successfully"
BACKUP_SCRIPT_EOF

    # Create restore script
    cat > /opt/postgresql/scripts/restore.sh << 'RESTORE_SCRIPT_EOF'
#!/bin/bash
# PostgreSQL Restore Script

set -e

# Configuration
BACKUP_FILE="$1"
DB_NAME="${2:-$DB_NAME}"
DB_USERNAME="${3:-$DB_USERNAME}"
RESTORE_DIR="/opt/postgresql/restore"
LOG_FILE="/opt/postgresql/logs/restore.log"

# Logging function
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

if [[ -z "$BACKUP_FILE" ]]; then
    echo "Usage: $0 <backup_file> [db_name] [db_username]"
    exit 1
fi

log "Starting restore from backup: $BACKUP_FILE"

# Create restore directory
mkdir -p "$RESTORE_DIR"

# If backup file is an S3 path, download it
if [[ "$BACKUP_FILE" =~ ^s3:// ]]; then
    log "Downloading backup from S3"
    LOCAL_FILE="$RESTORE_DIR/$(basename "$BACKUP_FILE")"
    aws s3 cp "$BACKUP_FILE" "$LOCAL_FILE"
    BACKUP_FILE="$LOCAL_FILE"
fi

# Decompress if needed
if [[ "$BACKUP_FILE" =~ \.gz$ ]]; then
    log "Decompressing backup file"
    gunzip "$BACKUP_FILE"
    BACKUP_FILE="${BACKUP_FILE%.gz}"
fi

# Restore database
log "Restoring database: $DB_NAME"
sudo -u postgres psql -d "$DB_NAME" -f "$BACKUP_FILE"

log "Database restore completed successfully"
RESTORE_SCRIPT_EOF

    # Make scripts executable
    chmod +x /opt/postgresql/scripts/*.sh
    
    # Set up cron job for automated backups
    if [[ -n "$S3_BACKUP_BUCKET" ]]; then
        cat > /etc/cron.d/postgresql-backup << EOF
# PostgreSQL automated backup
0 2 * * * root /opt/postgresql/scripts/backup.sh "$DB_NAME" "$DB_USERNAME" "$S3_BACKUP_BUCKET" "$AWS_REGION"
EOF
        log_info "Automated backup scheduled for 2 AM daily"
    fi
    
    log_success "Backup system setup completed"
}

# Setup monitoring
setup_monitoring() {
    log_info "Setting up monitoring..."
    
    # Create monitoring script
    cat > /opt/postgresql/scripts/monitor.sh << 'MONITOR_SCRIPT_EOF'
#!/bin/bash
# PostgreSQL Monitoring Script

DB_NAME="${1:-$DB_NAME}"
DB_USERNAME="${2:-$DB_USERNAME}"

echo "=== PostgreSQL Status Check ==="
echo "Date: $(date)"
echo ""

# Service status
echo "Service Status:"
if systemctl is-active postgresql-15 >/dev/null 2>&1; then
    echo "✓ PostgreSQL service is running"
else
    echo "✗ PostgreSQL service is NOT running"
    exit 1
fi

# Database connectivity
echo ""
echo "Database Connectivity:"
if sudo -u postgres psql -d "$DB_NAME" -c "SELECT version();" >/dev/null 2>&1; then
    echo "✓ Database connection successful"
    VERSION=$(sudo -u postgres psql -d "$DB_NAME" -t -c "SELECT version();" | xargs)
    echo "  Version: $VERSION"
else
    echo "✗ Cannot connect to database"
    exit 1
fi

# Connection count
echo ""
echo "Connection Statistics:"
TOTAL_CONNECTIONS=$(sudo -u postgres psql -d "$DB_NAME" -t -c "SELECT count(*) FROM pg_stat_activity;" | xargs)
ACTIVE_CONNECTIONS=$(sudo -u postgres psql -d "$DB_NAME" -t -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';" | xargs)
echo "  Total connections: $TOTAL_CONNECTIONS"
echo "  Active connections: $ACTIVE_CONNECTIONS"

# Disk usage
echo ""
echo "Disk Usage:"
DATA_SIZE=$(sudo -u postgres psql -d "$DB_NAME" -t -c "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));" | xargs)
echo "  Database size: $DATA_SIZE"

DISK_USAGE=$(df /var/lib/pgsql/15/data | tail -1)
echo "  Data directory: $DISK_USAGE"

# Recent activity
echo ""
echo "Recent Activity:"
sudo -u postgres psql -d "$DB_NAME" -c "SELECT now() - query_start AS runtime, datname, usename, state, query FROM pg_stat_activity WHERE state != 'idle' ORDER BY runtime DESC LIMIT 5;"

echo ""
echo "=== Status Check Complete ==="
MONITOR_SCRIPT_EOF

    chmod +x /opt/postgresql/scripts/monitor.sh
    
    log_success "Monitoring setup completed"
}

# Setup CloudWatch agent
setup_cloudwatch() {
    log_info "Setting up CloudWatch monitoring..."
    
    # Create CloudWatch configuration
    cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << 'CW_CONFIG_EOF'
{
    "metrics": {
        "namespace": "Oddiya/PostgreSQL",
        "metrics_collected": {
            "cpu": {
                "measurement": [
                    "cpu_usage_idle",
                    "cpu_usage_iowait",
                    "cpu_usage_user",
                    "cpu_usage_system"
                ],
                "metrics_collection_interval": 60
            },
            "disk": {
                "measurement": [
                    "used_percent"
                ],
                "metrics_collection_interval": 60,
                "resources": [
                    "*"
                ]
            },
            "diskio": {
                "measurement": [
                    "io_time",
                    "read_bytes",
                    "write_bytes"
                ],
                "metrics_collection_interval": 60,
                "resources": [
                    "*"
                ]
            },
            "mem": {
                "measurement": [
                    "mem_used_percent"
                ],
                "metrics_collection_interval": 60
            },
            "netstat": {
                "measurement": [
                    "tcp_established",
                    "tcp_time_wait"
                ],
                "metrics_collection_interval": 60
            }
        }
    },
    "logs": {
        "logs_collected": {
            "files": {
                "collect_list": [
                    {
                        "file_path": "/var/lib/pgsql/15/data/log/postgresql*.log",
                        "log_group_name": "/oddiya/postgresql",
                        "log_stream_name": "{instance_id}/postgresql"
                    },
                    {
                        "file_path": "/opt/postgresql/logs/backup.log",
                        "log_group_name": "/oddiya/postgresql-backup",
                        "log_stream_name": "{instance_id}/backup"
                    }
                ]
            }
        }
    }
}
CW_CONFIG_EOF

    # Start CloudWatch agent
    /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
        -a fetch-config \
        -m ec2 \
        -s \
        -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
    
    log_success "CloudWatch monitoring configured"
}

# Performance tuning
tune_performance() {
    log_info "Applying performance tuning..."
    
    # System-level tuning
    cat >> /etc/sysctl.conf << 'SYSCTL_EOF'

# PostgreSQL performance tuning
kernel.shmmax = 137438953472
kernel.shmall = 33554432
kernel.sem = 250 32000 100 128
vm.overcommit_memory = 2
vm.overcommit_ratio = 80
vm.swappiness = 1
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5
SYSCTL_EOF

    # Apply sysctl changes
    sysctl -p
    
    # Set resource limits for postgres user
    cat >> /etc/security/limits.conf << 'LIMITS_EOF'

# PostgreSQL limits
postgres soft nofile 65536
postgres hard nofile 65536
postgres soft nproc 32768
postgres hard nproc 32768
LIMITS_EOF
    
    log_success "Performance tuning applied"
}

# Create service health check
create_health_check() {
    log_info "Creating health check endpoint..."
    
    cat > /opt/postgresql/scripts/health-check.sh << 'HEALTH_SCRIPT_EOF'
#!/bin/bash
# Health check script for load balancers

DB_NAME="${1:-$DB_NAME}"

# Simple health check - return 0 if healthy, 1 if not
if sudo -u postgres psql -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
    echo '{"status": "healthy", "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    exit 0
else
    echo '{"status": "unhealthy", "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    exit 1
fi
HEALTH_SCRIPT_EOF

    chmod +x /opt/postgresql/scripts/health-check.sh
    
    log_success "Health check endpoint created"
}

# Setup logrotate
setup_logrotate() {
    log_info "Setting up log rotation..."
    
    cat > /etc/logrotate.d/postgresql << 'LOGROTATE_EOF'
/var/lib/pgsql/15/data/log/*.log {
    daily
    missingok
    rotate 14
    compress
    delaycompress
    notifempty
    create 640 postgres postgres
    postrotate
        /bin/kill -HUP `cat /var/run/postgresql/.s.PGSQL.5432.lock 2>/dev/null` 2>/dev/null || true
    endscript
}

/opt/postgresql/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 640 root root
}
LOGROTATE_EOF
    
    log_success "Log rotation configured"
}

# Final validation and testing
validate_installation() {
    log_info "Validating installation..."
    
    # Test database connection
    if sudo -u postgres psql -d "$DB_NAME" -c "SELECT now() as current_time;" >/dev/null 2>&1; then
        log_success "Database connection test passed"
    else
        error_exit "Database connection test failed"
    fi
    
    # Test extensions
    if [[ "$ENABLE_POSTGIS" == "true" ]]; then
        if sudo -u postgres psql -d "$DB_NAME" -c "SELECT PostGIS_Version();" >/dev/null 2>&1; then
            log_success "PostGIS extension test passed"
        else
            log_error "PostGIS extension test failed"
        fi
    fi
    
    # Test backup script
    if /opt/postgresql/scripts/backup.sh >/dev/null 2>&1; then
        log_success "Backup script test passed"
    else
        log_error "Backup script test failed"
    fi
    
    # Test monitoring script
    if /opt/postgresql/scripts/monitor.sh >/dev/null 2>&1; then
        log_success "Monitoring script test passed"
    else
        log_error "Monitoring script test failed"
    fi
    
    log_success "Installation validation completed"
}

# Display setup summary
display_summary() {
    log_info "=== PostgreSQL Setup Summary ==="
    log_info "PostgreSQL Version: ${POSTGRESQL_VERSION}"
    log_info "Database Name: ${DB_NAME}"
    log_info "Database User: ${DB_USERNAME}"
    log_info "PostGIS Enabled: ${ENABLE_POSTGIS}"
    log_info "Max Connections: ${MAX_CONNECTIONS}"
    log_info "Shared Buffers: ${SHARED_BUFFERS}"
    log_info "Effective Cache Size: ${EFFECTIVE_CACHE_SIZE}"
    log_info "Backup S3 Bucket: ${S3_BACKUP_BUCKET:-'Not configured'}"
    log_info ""
    log_info "Configuration Files:"
    log_info "  - postgresql.conf: /var/lib/pgsql/${POSTGRESQL_VERSION}/data/postgresql.conf"
    log_info "  - pg_hba.conf: /var/lib/pgsql/${POSTGRESQL_VERSION}/data/pg_hba.conf"
    log_info ""
    log_info "Scripts Location: /opt/postgresql/scripts/"
    log_info "  - backup.sh: Database backup script"
    log_info "  - restore.sh: Database restore script"
    log_info "  - monitor.sh: System monitoring script"
    log_info "  - health-check.sh: Health check script"
    log_info ""
    log_info "Log Files:"
    log_info "  - PostgreSQL: /var/lib/pgsql/${POSTGRESQL_VERSION}/data/log/"
    log_info "  - Setup: ${LOG_FILE}"
    log_info "  - Backup: /opt/postgresql/logs/backup.log"
    log_info ""
    log_info "Service Management:"
    log_info "  - Start: systemctl start postgresql-${POSTGRESQL_VERSION}"
    log_info "  - Stop: systemctl stop postgresql-${POSTGRESQL_VERSION}"
    log_info "  - Status: systemctl status postgresql-${POSTGRESQL_VERSION}"
    log_info "  - Restart: systemctl restart postgresql-${POSTGRESQL_VERSION}"
    log_info ""
    log_success "PostgreSQL setup completed successfully!"
    log_info "=================================="
}

# Main execution
main() {
    log_info "Starting PostgreSQL EC2 setup script..."
    log_info "Script version: 1.0"
    log_info "Execution time: $(date)"
    
    check_root
    get_system_info
    validate_environment
    prepare_system
    install_postgresql
    setup_storage
    initialize_database
    configure_postgresql
    setup_ssl
    setup_archive
    start_postgresql
    setup_database
    setup_backup
    setup_monitoring
    setup_cloudwatch
    tune_performance
    create_health_check
    setup_logrotate
    validate_installation
    display_summary
    
    log_success "All setup tasks completed successfully!"
}

# Execute main function
main "$@"