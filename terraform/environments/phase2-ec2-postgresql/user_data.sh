#!/bin/bash
# User Data Script for PostgreSQL EC2 Instance Setup
# This script runs on instance initialization

set -e

# Variables (passed from Terraform)
DB_NAME="${db_name}"
DB_USERNAME="${db_username}"
DB_PASSWORD="${db_password}"
S3_BUCKET="${s3_bucket}"
AWS_REGION="${aws_region}"
BACKUP_SCHEDULE="${backup_schedule}"

# System update
echo "=== Starting system update ==="
yum update -y

# Install required packages
echo "=== Installing required packages ==="
yum install -y \
    postgresql15-server \
    postgresql15-contrib \
    postgresql15-devel \
    postgis34_15 \
    awscli \
    amazon-cloudwatch-agent \
    htop \
    iotop \
    git \
    wget \
    curl \
    vim \
    screen \
    chrony

# Start and enable chronyd for time synchronization
systemctl enable chronyd
systemctl start chronyd

# Create PostgreSQL data directory on dedicated EBS volume
echo "=== Setting up PostgreSQL data directory ==="
mkfs -t ext4 /dev/nvme1n1
mkdir -p /var/lib/pgsql/15/data
mount /dev/nvme1n1 /var/lib/pgsql/15/data

# Add to fstab for persistent mounting
echo "/dev/nvme1n1 /var/lib/pgsql/15/data ext4 defaults,nofail 0 2" >> /etc/fstab

# Set correct ownership
chown -R postgres:postgres /var/lib/pgsql/15/data

# Initialize PostgreSQL database
echo "=== Initializing PostgreSQL database ==="
sudo -u postgres /usr/pgsql-15/bin/postgresql-15-setup initdb

# Configure PostgreSQL
echo "=== Configuring PostgreSQL ==="
cat >> /var/lib/pgsql/15/data/postgresql.conf << EOF

# Custom PostgreSQL Configuration for Oddiya EC2 Instance
listen_addresses = '*'
port = 5432
max_connections = 100
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200

# Enable logging
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_statement = 'ddl'
log_min_duration_statement = 1000
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on

# Enable shared libraries for extensions
shared_preload_libraries = 'postgis-3'

# SSL Configuration
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'

# Performance tuning
checkpoint_segments = 32
checkpoint_completion_target = 0.7
wal_keep_segments = 128
max_wal_senders = 3
wal_level = replica
archive_mode = on
archive_command = 'test ! -f /var/lib/pgsql/15/archive/%f && cp %p /var/lib/pgsql/15/archive/%f'

EOF

# Configure pg_hba.conf for authentication
echo "=== Configuring PostgreSQL authentication ==="
cat > /var/lib/pgsql/15/data/pg_hba.conf << EOF
# PostgreSQL Client Authentication Configuration File
# TYPE  DATABASE        USER            ADDRESS                 METHOD

# Local connections
local   all             postgres                                peer
local   all             all                                     md5

# IPv4 connections
host    all             all             127.0.0.1/32            md5
host    all             all             10.0.0.0/16             md5
host    replication     postgres        10.0.0.0/16             md5

# IPv6 connections
host    all             all             ::1/128                 md5

EOF

# Create archive directory
mkdir -p /var/lib/pgsql/15/archive
chown postgres:postgres /var/lib/pgsql/15/archive

# Generate SSL certificates
echo "=== Generating SSL certificates ==="
cd /var/lib/pgsql/15/data
openssl req -new -x509 -days 365 -nodes -text -out server.crt \
    -keyout server.key -subj "/CN=postgresql-server"
chmod og-rwx server.key
chown postgres:postgres server.crt server.key

# Start and enable PostgreSQL
echo "=== Starting PostgreSQL service ==="
systemctl enable postgresql-15
systemctl start postgresql-15

# Wait for PostgreSQL to start
sleep 10

# Set PostgreSQL password and create database
echo "=== Configuring database and user ==="
sudo -u postgres psql -c "ALTER USER postgres PASSWORD '$DB_PASSWORD';"

# Create application database if it doesn't exist
if [ "$DB_NAME" != "postgres" ]; then
    sudo -u postgres createdb "$DB_NAME"
fi

# Create application user if different from postgres
if [ "$DB_USERNAME" != "postgres" ]; then
    sudo -u postgres psql -c "CREATE USER $DB_USERNAME WITH PASSWORD '$DB_PASSWORD';"
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USERNAME;"
fi

# Install PostGIS extension
echo "=== Installing PostGIS extension ==="
sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"
sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;"
sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;"

# Create backup script
echo "=== Creating backup script ==="
mkdir -p /opt/postgresql/scripts
cat > /opt/postgresql/scripts/backup.sh << 'BACKUP_EOF'
#!/bin/bash
# PostgreSQL Backup Script

set -e

DB_NAME="$1"
DB_USERNAME="$2"
S3_BUCKET="$3"
AWS_REGION="$4"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="postgresql_backup_$${TIMESTAMP}.sql"
LOG_FILE="/var/log/postgresql_backup.log"

echo "$(date): Starting backup of database $DB_NAME" | tee -a $LOG_FILE

# Create backup directory
BACKUP_DIR="/opt/postgresql/backups"
mkdir -p $BACKUP_DIR

# Perform database backup
pg_dump -h localhost -U "$DB_USERNAME" -d "$DB_NAME" -f "$BACKUP_DIR/$BACKUP_FILE" 2>&1 | tee -a $LOG_FILE

# Compress backup
gzip "$BACKUP_DIR/$BACKUP_FILE"
BACKUP_FILE="$${BACKUP_FILE}.gz"

# Upload to S3
aws s3 cp "$BACKUP_DIR/$BACKUP_FILE" "s3://$S3_BUCKET/postgresql-backups/$BACKUP_FILE" --region "$AWS_REGION" 2>&1 | tee -a $LOG_FILE

# Verify upload
if aws s3 ls "s3://$S3_BUCKET/postgresql-backups/$BACKUP_FILE" --region "$AWS_REGION" > /dev/null; then
    echo "$(date): Backup successfully uploaded to S3" | tee -a $LOG_FILE
    # Remove local backup after successful upload
    rm -f "$BACKUP_DIR/$BACKUP_FILE"
else
    echo "$(date): ERROR: Failed to upload backup to S3" | tee -a $LOG_FILE
    exit 1
fi

# Clean up old local backups (keep last 3 days)
find $BACKUP_DIR -name "postgresql_backup_*.sql.gz" -mtime +3 -delete

echo "$(date): Backup completed successfully" | tee -a $LOG_FILE
BACKUP_EOF

chmod +x /opt/postgresql/scripts/backup.sh

# Create restore script
cat > /opt/postgresql/scripts/restore.sh << 'RESTORE_EOF'
#!/bin/bash
# PostgreSQL Restore Script

set -e

DB_NAME="$1"
DB_USERNAME="$2"
S3_BACKUP_PATH="$3"
AWS_REGION="$4"
LOG_FILE="/var/log/postgresql_restore.log"

echo "$(date): Starting restore of database $DB_NAME from $S3_BACKUP_PATH" | tee -a $LOG_FILE

# Create restore directory
RESTORE_DIR="/opt/postgresql/restore"
mkdir -p $RESTORE_DIR

# Download backup from S3
BACKUP_FILE=$(basename "$S3_BACKUP_PATH")
aws s3 cp "$S3_BACKUP_PATH" "$RESTORE_DIR/$BACKUP_FILE" --region "$AWS_REGION" 2>&1 | tee -a $LOG_FILE

# Decompress if needed
if [[ $BACKUP_FILE == *.gz ]]; then
    gunzip "$RESTORE_DIR/$BACKUP_FILE"
    BACKUP_FILE="$${BACKUP_FILE%.gz}"
fi

# Drop and recreate database (BE CAREFUL!)
echo "$(date): WARNING: Dropping existing database $DB_NAME" | tee -a $LOG_FILE
sudo -u postgres psql -c "DROP DATABASE IF EXISTS $DB_NAME;" 2>&1 | tee -a $LOG_FILE
sudo -u postgres psql -c "CREATE DATABASE $DB_NAME;" 2>&1 | tee -a $LOG_FILE

# Restore database
sudo -u postgres psql -d "$DB_NAME" -f "$RESTORE_DIR/$BACKUP_FILE" 2>&1 | tee -a $LOG_FILE

# Clean up restore file
rm -f "$RESTORE_DIR/$BACKUP_FILE"

echo "$(date): Database restore completed successfully" | tee -a $LOG_FILE
RESTORE_EOF

chmod +x /opt/postgresql/scripts/restore.sh

# Set up cron job for automated backups
echo "=== Setting up automated backups ==="
cat > /etc/cron.d/postgresql-backup << CRON_EOF
$BACKUP_SCHEDULE root /opt/postgresql/scripts/backup.sh "$DB_NAME" "$DB_USERNAME" "$S3_BUCKET" "$AWS_REGION"
CRON_EOF

# Create monitoring script
cat > /opt/postgresql/scripts/monitor.sh << 'MONITOR_EOF'
#!/bin/bash
# PostgreSQL Monitoring Script

DB_NAME="$1"
DB_USERNAME="$2"

# Check if PostgreSQL is running
if systemctl is-active postgresql-15 >/dev/null 2>&1; then
    echo "PostgreSQL service is running"
else
    echo "ERROR: PostgreSQL service is not running"
    exit 1
fi

# Check database connectivity
if sudo -u postgres psql -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
    echo "Database connection successful"
else
    echo "ERROR: Cannot connect to database"
    exit 1
fi

# Check disk space
DISK_USAGE=$(df /var/lib/pgsql/15/data | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt 80 ]; then
    echo "WARNING: Disk usage is at $${DISK_USAGE}%"
else
    echo "Disk usage is at $${DISK_USAGE}%"
fi

# Check active connections
CONNECTIONS=$(sudo -u postgres psql -d "$DB_NAME" -t -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';" | xargs)
echo "Active connections: $CONNECTIONS"

echo "All checks completed successfully"
MONITOR_EOF

chmod +x /opt/postgresql/scripts/monitor.sh

# Install and configure CloudWatch agent
echo "=== Setting up CloudWatch monitoring ==="
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << 'CW_EOF'
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
                "metrics_collection_interval": 60,
                "totalcpu": false
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
                    "write_bytes",
                    "reads",
                    "writes"
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
            },
            "swap": {
                "measurement": [
                    "swap_used_percent"
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
                        "log_stream_name": "{instance_id}/postgresql",
                        "timezone": "UTC"
                    },
                    {
                        "file_path": "/var/log/postgresql_backup.log",
                        "log_group_name": "/oddiya/postgresql-backup",
                        "log_stream_name": "{instance_id}/backup",
                        "timezone": "UTC"
                    }
                ]
            }
        }
    }
}
CW_EOF

# Start CloudWatch agent
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config \
    -m ec2 \
    -s \
    -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

# Create health check script
cat > /opt/postgresql/scripts/health-check.sh << 'HEALTH_EOF'
#!/bin/bash
# PostgreSQL Health Check Script for Load Balancer

DB_NAME="$1"
DB_USERNAME="$2"

# Return HTTP 200 if PostgreSQL is healthy, 503 if not
if sudo -u postgres psql -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
    echo "HTTP/1.1 200 OK"
    echo "Content-Type: application/json"
    echo ""
    echo '{"status":"healthy","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    exit 0
else
    echo "HTTP/1.1 503 Service Unavailable"
    echo "Content-Type: application/json"
    echo ""
    echo '{"status":"unhealthy","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    exit 1
fi
HEALTH_EOF

chmod +x /opt/postgresql/scripts/health-check.sh

# Set proper permissions
chown -R postgres:postgres /var/lib/pgsql/15/
chown -R root:root /opt/postgresql/

# Create completion marker
echo "=== PostgreSQL EC2 setup completed successfully ==="
echo "$(date): PostgreSQL EC2 setup completed" > /opt/postgresql/setup-complete.log

# Final service restart to ensure all configurations are loaded
systemctl restart postgresql-15

echo "Setup completed successfully. PostgreSQL is ready for use."