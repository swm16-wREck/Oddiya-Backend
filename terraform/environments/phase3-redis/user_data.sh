#!/bin/bash
# Redis EC2 User Data Script
# Agent 1 - EC2 Infrastructure Specialist

set -e

# Variables passed from Terraform
REDIS_VERSION="${redis_version}"
REDIS_PASSWORD="${redis_password}"
NODE_INDEX="${node_index}"
MASTER_IP="${master_ip}"
ENVIRONMENT="${environment}"
CLOUDWATCH_REGION="${cloudwatch_region}"

# System information
INSTANCE_ID=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
AVAILABILITY_ZONE=$(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)

# Logging setup
LOG_FILE="/var/log/redis-setup.log"
exec > >(tee -a $LOG_FILE)
exec 2>&1

echo "Starting Redis setup at $(date)"
echo "Instance ID: $INSTANCE_ID"
echo "Private IP: $PRIVATE_IP"
echo "AZ: $AVAILABILITY_ZONE"
echo "Environment: $ENVIRONMENT"

# Update system packages
echo "Updating system packages..."
yum update -y

# Install required packages
echo "Installing required packages..."
yum install -y \
    gcc \
    gcc-c++ \
    make \
    wget \
    curl \
    htop \
    iotop \
    sysstat \
    tcpdump \
    net-tools \
    awscli \
    cloudwatch-agent \
    chrony

# Start and enable chrony for time synchronization
systemctl enable chronyd
systemctl start chronyd

# Install Redis
echo "Installing Redis $REDIS_VERSION..."
cd /tmp
wget http://download.redis.io/releases/redis-${REDIS_VERSION}.tar.gz
tar -xzf redis-${REDIS_VERSION}.tar.gz
cd redis-${REDIS_VERSION}

# Compile Redis
make
make install

# Create Redis user and directories
echo "Setting up Redis user and directories..."
useradd -r -s /bin/false redis
mkdir -p /etc/redis /var/lib/redis /var/log/redis
chown redis:redis /var/lib/redis /var/log/redis
chmod 755 /etc/redis

# Determine node role based on tags
echo "Determining node role..."
ROLE=$(aws ec2 describe-tags --region $CLOUDWATCH_REGION --filters "Name=resource-id,Values=$INSTANCE_ID" "Name=key,Values=Role" --query 'Tags[0].Value' --output text)
echo "Node role: $ROLE"

# Get master IP for slave configuration
if [[ "$ROLE" == "redis-slave" ]]; then
    echo "Getting master IP for slave configuration..."
    MASTER_IP=$(aws ec2 describe-instances --region $CLOUDWATCH_REGION --filters "Name=tag:Role,Values=redis-master" "Name=instance-state-name,Values=running" --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
    echo "Master IP: $MASTER_IP"
fi

# Create Redis configuration
echo "Creating Redis configuration..."
if [[ "$ROLE" == "redis-master" ]]; then
    cat > /etc/redis/redis.conf << EOF
# Redis Master Configuration
# Basic Configuration
port 6379
bind 0.0.0.0
protected-mode yes
requirepass $REDIS_PASSWORD
masterauth $REDIS_PASSWORD

# Persistence Configuration
save 900 1
save 300 10
save 60 10000
dir /var/lib/redis
dbfilename dump.rdb
rdbcompression yes
rdbchecksum yes

# AOF Configuration
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# Memory Configuration
maxmemory-policy allkeys-lru

# Network Configuration
tcp-keepalive 300
timeout 0
tcp-backlog 511
maxclients 10000

# Logging
loglevel notice
logfile /var/log/redis/redis-server.log
syslog-enabled yes
syslog-ident redis

# Security
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command EVAL ""
rename-command DEBUG ""
rename-command CONFIG "CONFIG_b3d4c5e6f7a8b9c0"

# Performance Tuning
latency-monitor-threshold 100
EOF
else
    cat > /etc/redis/redis.conf << EOF
# Redis Slave Configuration
# Basic Configuration
port 6379
bind 0.0.0.0
protected-mode yes
requirepass $REDIS_PASSWORD
masterauth $REDIS_PASSWORD

# Replication Configuration
replicaof $MASTER_IP 6379
replica-read-only yes
replica-serve-stale-data yes
replica-priority 100

# Persistence Configuration
save 900 1
save 300 10
save 60 10000
dir /var/lib/redis
dbfilename dump.rdb
rdbcompression yes
rdbchecksum yes

# AOF Configuration
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# Memory Configuration
maxmemory-policy allkeys-lru

# Network Configuration
tcp-keepalive 300
timeout 0
tcp-backlog 511
maxclients 10000

# Logging
loglevel notice
logfile /var/log/redis/redis-server.log
syslog-enabled yes
syslog-ident redis

# Security
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command EVAL ""
rename-command DEBUG ""
rename-command CONFIG "CONFIG_b3d4c5e6f7a8b9c0"

# Performance Tuning
latency-monitor-threshold 100
EOF
fi

# Create Sentinel configuration
echo "Creating Sentinel configuration..."
cat > /etc/redis/sentinel.conf << EOF
# Redis Sentinel Configuration
port 26379
bind 0.0.0.0
dir /var/lib/redis

# Sentinel monitoring
sentinel monitor redis-master $PRIVATE_IP 6379 2
sentinel auth-pass redis-master $REDIS_PASSWORD
sentinel down-after-milliseconds redis-master 30000
sentinel parallel-syncs redis-master 1
sentinel failover-timeout redis-master 180000
sentinel deny-scripts-reconfig yes

# Logging
logfile /var/log/redis/sentinel.log
syslog-enabled yes
syslog-ident sentinel
EOF

# If this is a slave, update sentinel to point to master
if [[ "$ROLE" == "redis-slave" && -n "$MASTER_IP" ]]; then
    sed -i "s/redis-master $PRIVATE_IP 6379/redis-master $MASTER_IP 6379/" /etc/redis/sentinel.conf
fi

# Set correct ownership
chown redis:redis /etc/redis/redis.conf /etc/redis/sentinel.conf
chmod 640 /etc/redis/redis.conf /etc/redis/sentinel.conf

# Create systemd service files
echo "Creating systemd service files..."
cat > /etc/systemd/system/redis.service << 'EOF'
[Unit]
Description=Redis In-Memory Data Store
After=network.target

[Service]
User=redis
Group=redis
ExecStart=/usr/local/bin/redis-server /etc/redis/redis.conf
ExecStop=/usr/local/bin/redis-cli shutdown
Restart=always
RestartSec=5

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/redis /var/log/redis

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/redis-sentinel.service << 'EOF'
[Unit]
Description=Redis Sentinel
After=network.target redis.service
Requires=redis.service

[Service]
User=redis
Group=redis
ExecStart=/usr/local/bin/redis-sentinel /etc/redis/sentinel.conf
ExecStop=/usr/local/bin/redis-cli -p 26379 shutdown
Restart=always
RestartSec=5

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/redis /var/log/redis

[Install]
WantedBy=multi-user.target
EOF

# Configure system limits
echo "Configuring system limits..."
cat >> /etc/security/limits.conf << 'EOF'
# Redis limits
redis soft nofile 65535
redis hard nofile 65535
redis soft nproc 4096
redis hard nproc 4096
EOF

# Configure sysctl for Redis
echo "Configuring kernel parameters..."
cat >> /etc/sysctl.conf << 'EOF'
# Redis optimizations
vm.overcommit_memory = 1
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
EOF
sysctl -p

# Disable transparent huge pages
echo 'never' > /sys/kernel/mm/transparent_hugepage/enabled
echo 'echo never > /sys/kernel/mm/transparent_hugepage/enabled' >> /etc/rc.local

# Create backup script
echo "Creating backup script..."
cat > /usr/local/bin/redis-backup.sh << 'EOF'
#!/bin/bash
# Redis backup script

BACKUP_DIR="/var/lib/redis/backups"
S3_BUCKET=$(aws ec2 describe-tags --region $CLOUDWATCH_REGION --filters "Name=resource-id,Values=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)" "Name=key,Values=BackupBucket" --query 'Tags[0].Value' --output text 2>/dev/null || echo "")
DATE=$(date +%Y%m%d_%H%M%S)
HOSTNAME=$(hostname)

mkdir -p $BACKUP_DIR

# Create backup
redis-cli --rdb $BACKUP_DIR/dump_${HOSTNAME}_${DATE}.rdb

# Compress backup
gzip $BACKUP_DIR/dump_${HOSTNAME}_${DATE}.rdb

# Upload to S3 if bucket is configured
if [[ -n "$S3_BUCKET" ]]; then
    aws s3 cp $BACKUP_DIR/dump_${HOSTNAME}_${DATE}.rdb.gz s3://$S3_BUCKET/redis-backups/$HOSTNAME/
    
    # Clean up old local backups (keep 7 days)
    find $BACKUP_DIR -name "*.rdb.gz" -mtime +7 -delete
fi

echo "Backup completed: dump_${HOSTNAME}_${DATE}.rdb.gz"
EOF

chmod +x /usr/local/bin/redis-backup.sh
chown redis:redis /usr/local/bin/redis-backup.sh

# Create monitoring script
echo "Creating monitoring script..."
cat > /usr/local/bin/redis-monitor.sh << EOF
#!/bin/bash
# Redis monitoring script for CloudWatch

INSTANCE_ID=\$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
REGION="$CLOUDWATCH_REGION"
NAMESPACE="Oddiya/Redis/$ENVIRONMENT"

# Get Redis metrics
REDIS_INFO=\$(redis-cli info 2>/dev/null)
if [[ \$? -eq 0 ]]; then
    CONNECTED_CLIENTS=\$(echo "\$REDIS_INFO" | grep "connected_clients:" | cut -d: -f2 | tr -d '\r')
    USED_MEMORY=\$(echo "\$REDIS_INFO" | grep "used_memory:" | cut -d: -f2 | tr -d '\r')
    KEYSPACE_HITS=\$(echo "\$REDIS_INFO" | grep "keyspace_hits:" | cut -d: -f2 | tr -d '\r')
    KEYSPACE_MISSES=\$(echo "\$REDIS_INFO" | grep "keyspace_misses:" | cut -d: -f2 | tr -d '\r')
    
    # Send metrics to CloudWatch
    aws cloudwatch put-metric-data --region \$REGION --namespace \$NAMESPACE \\
        --metric-data MetricName=ConnectedClients,Value=\$CONNECTED_CLIENTS,Unit=Count,Dimensions=InstanceId=\$INSTANCE_ID \\
                      MetricName=UsedMemory,Value=\$USED_MEMORY,Unit=Bytes,Dimensions=InstanceId=\$INSTANCE_ID \\
                      MetricName=KeyspaceHits,Value=\$KEYSPACE_HITS,Unit=Count,Dimensions=InstanceId=\$INSTANCE_ID \\
                      MetricName=KeyspaceMisses,Value=\$KEYSPACE_MISSES,Unit=Count,Dimensions=InstanceId=\$INSTANCE_ID
fi
EOF

chmod +x /usr/local/bin/redis-monitor.sh

# Create health check script
cat > /usr/local/bin/redis-health-check.sh << 'EOF'
#!/bin/bash
# Redis health check script

# Check if Redis is running
if ! pgrep -f redis-server > /dev/null; then
    echo "CRITICAL: Redis server is not running"
    exit 2
fi

# Check Redis connectivity
PING_RESULT=$(redis-cli ping 2>/dev/null)
if [[ "$PING_RESULT" != "PONG" ]]; then
    echo "CRITICAL: Redis is not responding to ping"
    exit 2
fi

# Check memory usage
MEMORY_INFO=$(redis-cli info memory 2>/dev/null)
USED_MEMORY=$(echo "$MEMORY_INFO" | grep "used_memory:" | cut -d: -f2 | tr -d '\r')
MAX_MEMORY=$(echo "$MEMORY_INFO" | grep "maxmemory:" | cut -d: -f2 | tr -d '\r')

if [[ $MAX_MEMORY -gt 0 ]]; then
    MEMORY_USAGE=$(( (USED_MEMORY * 100) / MAX_MEMORY ))
    if [[ $MEMORY_USAGE -gt 90 ]]; then
        echo "WARNING: Memory usage is ${MEMORY_USAGE}%"
        exit 1
    fi
fi

echo "OK: Redis is healthy"
exit 0
EOF

chmod +x /usr/local/bin/redis-health-check.sh

# Setup cron jobs
echo "Setting up cron jobs..."
crontab -u redis << 'EOF'
# Redis backup (daily at 2 AM)
0 2 * * * /usr/local/bin/redis-backup.sh >> /var/log/redis/backup.log 2>&1

# Redis monitoring (every minute)
* * * * * /usr/local/bin/redis-monitor.sh >> /var/log/redis/monitor.log 2>&1

# Health check (every 5 minutes)
*/5 * * * * /usr/local/bin/redis-health-check.sh >> /var/log/redis/health.log 2>&1
EOF

# Configure CloudWatch agent
echo "Configuring CloudWatch agent..."
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << EOF
{
    "metrics": {
        "namespace": "Oddiya/Redis/$ENVIRONMENT",
        "metrics_collected": {
            "cpu": {
                "measurement": ["cpu_usage_idle", "cpu_usage_iowait", "cpu_usage_user", "cpu_usage_system"],
                "metrics_collection_interval": 60
            },
            "disk": {
                "measurement": ["used_percent", "inodes_free"],
                "metrics_collection_interval": 60,
                "resources": ["*"]
            },
            "diskio": {
                "measurement": ["io_time"],
                "metrics_collection_interval": 60,
                "resources": ["*"]
            },
            "mem": {
                "measurement": ["mem_used_percent"],
                "metrics_collection_interval": 60
            },
            "netstat": {
                "measurement": ["tcp_established", "tcp_time_wait"],
                "metrics_collection_interval": 60
            },
            "swap": {
                "measurement": ["swap_used_percent"],
                "metrics_collection_interval": 60
            }
        }
    },
    "logs": {
        "logs_collected": {
            "files": {
                "collect_list": [
                    {
                        "file_path": "/var/log/redis/redis-server.log",
                        "log_group_name": "/aws/ec2/redis/$ENVIRONMENT",
                        "log_stream_name": "{instance_id}-redis-server"
                    },
                    {
                        "file_path": "/var/log/redis/sentinel.log",
                        "log_group_name": "/aws/ec2/redis/$ENVIRONMENT",
                        "log_stream_name": "{instance_id}-sentinel"
                    },
                    {
                        "file_path": "/var/log/redis-setup.log",
                        "log_group_name": "/aws/ec2/redis/$ENVIRONMENT",
                        "log_stream_name": "{instance_id}-setup"
                    }
                ]
            }
        }
    }
}
EOF

# Start services
echo "Starting services..."
systemctl daemon-reload

# Enable and start Redis
systemctl enable redis
systemctl start redis

# Wait for Redis to be ready
sleep 10

# Enable and start Sentinel
systemctl enable redis-sentinel
systemctl start redis-sentinel

# Start CloudWatch agent
systemctl enable amazon-cloudwatch-agent
systemctl start amazon-cloudwatch-agent

# Create a simple HTTP health check endpoint for ALB
cat > /usr/local/bin/redis-http-health.py << 'EOF'
#!/usr/bin/env python3
import socket
import subprocess
from http.server import HTTPServer, BaseHTTPRequestHandler

class HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            result = subprocess.run(['redis-cli', 'ping'], capture_output=True, text=True, timeout=5)
            if result.stdout.strip() == 'PONG':
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'OK')
            else:
                self.send_response(503)
                self.end_headers()
                self.wfile.write(b'Redis not responding')
        except Exception as e:
            self.send_response(503)
            self.end_headers()
            self.wfile.write(f'Error: {str(e)}'.encode())

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8080), HealthHandler)
    server.serve_forever()
EOF

chmod +x /usr/local/bin/redis-http-health.py

# Create systemd service for HTTP health check
cat > /etc/systemd/system/redis-http-health.service << 'EOF'
[Unit]
Description=Redis HTTP Health Check
After=redis.service

[Service]
ExecStart=/usr/local/bin/redis-http-health.py
Restart=always
RestartSec=5
User=redis

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable redis-http-health
systemctl start redis-http-health

# Wait for services to stabilize
echo "Waiting for services to stabilize..."
sleep 30

# Verify services are running
echo "Verifying services..."
systemctl is-active redis
systemctl is-active redis-sentinel
systemctl is-active redis-http-health
systemctl is-active amazon-cloudwatch-agent

# Test Redis connectivity
echo "Testing Redis connectivity..."
redis-cli ping

echo "Redis setup completed successfully at $(date)"
echo "Node role: $ROLE"
echo "Private IP: $PRIVATE_IP"
echo "Master IP: ${MASTER_IP:-"This is the master"}"

# Signal completion
touch /var/lib/redis/setup-complete