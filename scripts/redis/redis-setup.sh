#!/bin/bash
# Redis Setup and Configuration Script
# Agent 2 - Redis Configuration Engineer

set -e

# Configuration variables
REDIS_VERSION="${REDIS_VERSION:-7.2}"
REDIS_USER="redis"
REDIS_HOME="/opt/redis"
REDIS_DATA_DIR="/var/lib/redis"
REDIS_LOG_DIR="/var/log/redis"
REDIS_CONFIG_DIR="/etc/redis"
REDIS_PASSWORD="${REDIS_PASSWORD:-change-me-in-production}"
ENVIRONMENT="${ENVIRONMENT:-dev}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# Function to check if script is run as root
check_root() {
    if [[ $EUID -ne 0 ]]; then
        error "This script must be run as root (use sudo)"
    fi
}

# Function to detect OS
detect_os() {
    if [[ -f /etc/redhat-release ]]; then
        OS="centos"
        PKG_MANAGER="yum"
    elif [[ -f /etc/debian_version ]]; then
        OS="ubuntu"
        PKG_MANAGER="apt"
    else
        error "Unsupported operating system"
    fi
    log "Detected OS: $OS"
}

# Function to install dependencies
install_dependencies() {
    log "Installing dependencies..."
    
    if [[ $OS == "centos" ]]; then
        $PKG_MANAGER update -y
        $PKG_MANAGER groupinstall -y "Development Tools"
        $PKG_MANAGER install -y wget curl gcc gcc-c++ make tcl-devel \
            openssl-devel systemd-devel jemalloc-devel
    elif [[ $OS == "ubuntu" ]]; then
        $PKG_MANAGER update -y
        $PKG_MANAGER install -y build-essential wget curl gcc make \
            libssl-dev libsystemd-dev libjemalloc-dev tcl-dev
    fi
    
    success "Dependencies installed"
}

# Function to create Redis user and directories
setup_user_directories() {
    log "Setting up Redis user and directories..."
    
    # Create Redis user
    if ! getent passwd $REDIS_USER > /dev/null 2>&1; then
        useradd -r -d $REDIS_HOME -s /bin/false $REDIS_USER
        success "Created Redis user: $REDIS_USER"
    else
        log "Redis user already exists"
    fi
    
    # Create directories
    mkdir -p $REDIS_HOME $REDIS_DATA_DIR $REDIS_LOG_DIR $REDIS_CONFIG_DIR
    mkdir -p $REDIS_DATA_DIR/backups
    
    # Set ownership and permissions
    chown -R $REDIS_USER:$REDIS_USER $REDIS_HOME $REDIS_DATA_DIR $REDIS_LOG_DIR
    chown -R root:$REDIS_USER $REDIS_CONFIG_DIR
    
    chmod 755 $REDIS_HOME $REDIS_DATA_DIR $REDIS_LOG_DIR $REDIS_CONFIG_DIR
    chmod 750 $REDIS_DATA_DIR/backups
    
    success "User and directories setup completed"
}

# Function to download and compile Redis
install_redis() {
    log "Installing Redis $REDIS_VERSION..."
    
    cd /tmp
    
    # Download Redis
    if [[ ! -f redis-$REDIS_VERSION.tar.gz ]]; then
        wget http://download.redis.io/releases/redis-$REDIS_VERSION.tar.gz
    fi
    
    # Extract and compile
    rm -rf redis-$REDIS_VERSION
    tar -xzf redis-$REDIS_VERSION.tar.gz
    cd redis-$REDIS_VERSION
    
    # Compile with jemalloc for better memory management
    make MALLOC=jemalloc
    make install PREFIX=$REDIS_HOME
    
    # Create symlinks in /usr/local/bin for system-wide access
    ln -sf $REDIS_HOME/bin/redis-server /usr/local/bin/redis-server
    ln -sf $REDIS_HOME/bin/redis-cli /usr/local/bin/redis-cli
    ln -sf $REDIS_HOME/bin/redis-sentinel /usr/local/bin/redis-sentinel
    ln -sf $REDIS_HOME/bin/redis-benchmark /usr/local/bin/redis-benchmark
    ln -sf $REDIS_HOME/bin/redis-check-aof /usr/local/bin/redis-check-aof
    ln -sf $REDIS_HOME/bin/redis-check-rdb /usr/local/bin/redis-check-rdb
    
    success "Redis $REDIS_VERSION installed successfully"
}

# Function to configure system parameters
configure_system() {
    log "Configuring system parameters for Redis..."
    
    # Configure kernel parameters
    cat >> /etc/sysctl.conf << 'EOF'
# Redis optimizations
vm.overcommit_memory = 1
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
vm.swappiness = 10
EOF
    
    # Apply sysctl changes
    sysctl -p
    
    # Configure limits
    cat >> /etc/security/limits.conf << EOF
# Redis limits
$REDIS_USER soft nofile 65535
$REDIS_USER hard nofile 65535
$REDIS_USER soft nproc 4096
$REDIS_USER hard nproc 4096
EOF
    
    # Disable transparent huge pages
    echo 'never' > /sys/kernel/mm/transparent_hugepage/enabled
    echo 'never' > /sys/kernel/mm/transparent_hugepage/defrag
    
    # Make it persistent
    cat >> /etc/rc.local << 'EOF'
# Disable transparent huge pages for Redis
echo never > /sys/kernel/mm/transparent_hugepage/enabled
echo never > /sys/kernel/mm/transparent_hugepage/defrag
EOF
    
    chmod +x /etc/rc.local
    
    success "System parameters configured"
}

# Function to generate Redis configuration
generate_redis_config() {
    local role=$1
    local master_ip=$2
    
    log "Generating Redis configuration for role: $role"
    
    local config_file="$REDIS_CONFIG_DIR/redis.conf"
    
    # Copy base configuration based on role
    if [[ $role == "master" ]]; then
        cp "$(dirname "$0")/redis-master.conf" $config_file
    else
        cp "$(dirname "$0")/redis-slave.conf" $config_file
        # Configure replication
        if [[ -n $master_ip ]]; then
            sed -i "s/# replicaof <masterip> <masterport>/replicaof $master_ip 6379/" $config_file
        fi
    fi
    
    # Replace passwords
    sed -i "s/CHANGE_ME_REDIS_PASSWORD/$REDIS_PASSWORD/g" $config_file
    
    # Set proper file ownership and permissions
    chown root:$REDIS_USER $config_file
    chmod 640 $config_file
    
    success "Redis configuration generated for $role"
}

# Function to generate Sentinel configuration
generate_sentinel_config() {
    local master_ip=${1:-"127.0.0.1"}
    
    log "Generating Sentinel configuration..."
    
    local config_file="$REDIS_CONFIG_DIR/sentinel.conf"
    
    # Copy base configuration
    cp "$(dirname "$0")/sentinel.conf" $config_file
    
    # Replace placeholders
    sed -i "s/CHANGE_ME_REDIS_PASSWORD/$REDIS_PASSWORD/g" $config_file
    sed -i "s/CHANGE_ME_MASTER_IP/$master_ip/g" $config_file
    
    # Set proper file ownership and permissions
    chown root:$REDIS_USER $config_file
    chmod 640 $config_file
    
    success "Sentinel configuration generated"
}

# Function to create systemd service files
create_systemd_services() {
    log "Creating systemd service files..."
    
    # Redis service
    cat > /etc/systemd/system/redis.service << EOF
[Unit]
Description=Redis In-Memory Data Store ($ENVIRONMENT)
After=network.target

[Service]
Type=notify
User=$REDIS_USER
Group=$REDIS_USER
ExecStart=$REDIS_HOME/bin/redis-server $REDIS_CONFIG_DIR/redis.conf
ExecReload=/bin/kill -USR2 \$MAINPID
ExecStop=$REDIS_HOME/bin/redis-cli shutdown
TimeoutStopSec=10
Restart=always
RestartSec=5

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$REDIS_DATA_DIR $REDIS_LOG_DIR
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictRealtime=true
RestrictNamespaces=true

# Resource limits
LimitNOFILE=65535
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
EOF

    # Sentinel service
    cat > /etc/systemd/system/redis-sentinel.service << EOF
[Unit]
Description=Redis Sentinel ($ENVIRONMENT)
After=network.target redis.service
Requires=redis.service

[Service]
Type=notify
User=$REDIS_USER
Group=$REDIS_USER
ExecStart=$REDIS_HOME/bin/redis-sentinel $REDIS_CONFIG_DIR/sentinel.conf
ExecReload=/bin/kill -USR2 \$MAINPID
ExecStop=$REDIS_HOME/bin/redis-cli -p 26379 shutdown
TimeoutStopSec=10
Restart=always
RestartSec=5

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$REDIS_DATA_DIR $REDIS_LOG_DIR
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictRealtime=true
RestrictNamespaces=true

# Resource limits
LimitNOFILE=65535
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
EOF
    
    # Reload systemd
    systemctl daemon-reload
    
    success "Systemd service files created"
}

# Function to create utility scripts
create_utility_scripts() {
    log "Creating utility scripts..."
    
    # Redis CLI wrapper
    cat > /usr/local/bin/redis-cli-auth << EOF
#!/bin/bash
# Redis CLI with authentication
exec redis-cli -a "$REDIS_PASSWORD" "\$@"
EOF
    chmod +x /usr/local/bin/redis-cli-auth
    
    # Redis status script
    cat > /usr/local/bin/redis-status << 'EOF'
#!/bin/bash
# Redis status script

echo "=== Redis Status ==="
systemctl is-active redis && echo "Redis: RUNNING" || echo "Redis: STOPPED"
systemctl is-active redis-sentinel && echo "Sentinel: RUNNING" || echo "Sentinel: STOPPED"

echo -e "\n=== Redis Info ==="
redis-cli -a "$REDIS_PASSWORD" info server 2>/dev/null | grep -E "(redis_version|os|uptime_in_seconds|connected_clients)"

echo -e "\n=== Memory Usage ==="
redis-cli -a "$REDIS_PASSWORD" info memory 2>/dev/null | grep -E "(used_memory_human|used_memory_peak_human|maxmemory_human)"

echo -e "\n=== Replication ==="
redis-cli -a "$REDIS_PASSWORD" info replication 2>/dev/null

echo -e "\n=== Persistence ==="
redis-cli -a "$REDIS_PASSWORD" info persistence 2>/dev/null | grep -E "(rdb_last_save_time|aof_enabled|aof_last_rewrite_time_sec)"
EOF
    chmod +x /usr/local/bin/redis-status
    
    # Performance monitoring script
    cat > /usr/local/bin/redis-monitor << EOF
#!/bin/bash
# Redis performance monitoring

REDIS_PASSWORD="$REDIS_PASSWORD"

echo "=== Redis Performance Monitor ==="
echo "Timestamp: \$(date)"
echo ""

# Basic metrics
redis-cli -a "\$REDIS_PASSWORD" --latency-history -i 1 > /tmp/redis-latency.log &
LATENCY_PID=\$!

# Info stats
echo "=== Current Stats ==="
redis-cli -a "\$REDIS_PASSWORD" info stats | grep -E "(total_commands_processed|total_connections_received|keyspace_hits|keyspace_misses|expired_keys|evicted_keys)"

echo ""
echo "=== Memory Usage ==="
redis-cli -a "\$REDIS_PASSWORD" info memory | grep -E "(used_memory|used_memory_peak|mem_fragmentation_ratio)"

echo ""
echo "=== Connected Clients ==="
redis-cli -a "\$REDIS_PASSWORD" info clients

echo ""
echo "=== Slow Log (last 5) ==="
redis-cli -a "\$REDIS_PASSWORD" slowlog get 5

# Stop latency monitoring after 10 seconds
sleep 10
kill \$LATENCY_PID 2>/dev/null
EOF
    chmod +x /usr/local/bin/redis-monitor
    
    success "Utility scripts created"
}

# Function to setup log rotation
setup_log_rotation() {
    log "Setting up log rotation..."
    
    cat > /etc/logrotate.d/redis << EOF
$REDIS_LOG_DIR/*.log {
    weekly
    missingok
    rotate 52
    compress
    delaycompress
    notifempty
    create 644 $REDIS_USER $REDIS_USER
    postrotate
        systemctl reload redis 2>/dev/null || true
        systemctl reload redis-sentinel 2>/dev/null || true
    endscript
}
EOF
    
    success "Log rotation configured"
}

# Function to start and enable services
start_services() {
    log "Starting and enabling Redis services..."
    
    # Enable services
    systemctl enable redis
    systemctl enable redis-sentinel
    
    # Start Redis
    systemctl start redis
    
    # Wait for Redis to be ready
    sleep 5
    
    # Verify Redis is running
    if systemctl is-active --quiet redis; then
        success "Redis started successfully"
    else
        error "Failed to start Redis"
    fi
    
    # Start Sentinel
    systemctl start redis-sentinel
    
    # Wait for Sentinel
    sleep 5
    
    # Verify Sentinel is running
    if systemctl is-active --quiet redis-sentinel; then
        success "Redis Sentinel started successfully"
    else
        warning "Sentinel failed to start (this may be normal if master IP is not set)"
    fi
}

# Function to run basic tests
run_tests() {
    log "Running basic Redis tests..."
    
    # Test Redis connectivity
    if redis-cli -a "$REDIS_PASSWORD" ping | grep -q "PONG"; then
        success "Redis connectivity test passed"
    else
        error "Redis connectivity test failed"
    fi
    
    # Test basic operations
    redis-cli -a "$REDIS_PASSWORD" set test-key "test-value" > /dev/null
    if [[ $(redis-cli -a "$REDIS_PASSWORD" get test-key) == "test-value" ]]; then
        success "Redis read/write test passed"
        redis-cli -a "$REDIS_PASSWORD" del test-key > /dev/null
    else
        error "Redis read/write test failed"
    fi
    
    # Test persistence
    redis-cli -a "$REDIS_PASSWORD" bgsave > /dev/null
    if [[ $? -eq 0 ]]; then
        success "Redis persistence test passed"
    else
        warning "Redis persistence test failed"
    fi
    
    success "All basic tests completed"
}

# Function to display summary
display_summary() {
    log "Redis setup completed successfully!"
    echo ""
    echo "=== Redis Installation Summary ==="
    echo "Redis Version: $REDIS_VERSION"
    echo "Installation Path: $REDIS_HOME"
    echo "Data Directory: $REDIS_DATA_DIR"
    echo "Configuration Directory: $REDIS_CONFIG_DIR"
    echo "Log Directory: $REDIS_LOG_DIR"
    echo ""
    echo "=== Services ==="
    echo "Redis Service: redis.service"
    echo "Sentinel Service: redis-sentinel.service"
    echo ""
    echo "=== Useful Commands ==="
    echo "Check status: redis-status"
    echo "Monitor performance: redis-monitor"
    echo "Connect with auth: redis-cli-auth"
    echo "View logs: journalctl -u redis -f"
    echo "Configuration file: $REDIS_CONFIG_DIR/redis.conf"
    echo ""
    echo "=== Security Notes ==="
    echo "- Change the default password in production"
    echo "- Configure proper firewall rules"
    echo "- Monitor logs for security events"
    echo "- Regularly update Redis version"
    echo ""
    success "Setup completed! Redis is ready for use."
}

# Main function
main() {
    local role=${1:-"standalone"}
    local master_ip=${2:-""}
    
    log "Starting Redis setup for role: $role"
    
    check_root
    detect_os
    install_dependencies
    setup_user_directories
    install_redis
    configure_system
    generate_redis_config "$role" "$master_ip"
    
    if [[ $role != "slave-only" ]]; then
        generate_sentinel_config "$master_ip"
    fi
    
    create_systemd_services
    create_utility_scripts
    setup_log_rotation
    start_services
    run_tests
    display_summary
}

# Show usage if no arguments provided
if [[ $# -eq 0 ]]; then
    echo "Usage: $0 <role> [master_ip]"
    echo ""
    echo "Roles:"
    echo "  master       - Setup as Redis master"
    echo "  slave        - Setup as Redis slave (requires master_ip)"
    echo "  sentinel     - Setup as Sentinel only"
    echo "  standalone   - Setup as standalone instance"
    echo ""
    echo "Examples:"
    echo "  $0 master"
    echo "  $0 slave 10.0.1.100"
    echo "  $0 standalone"
    echo ""
    exit 1
fi

# Run main function with arguments
main "$@"