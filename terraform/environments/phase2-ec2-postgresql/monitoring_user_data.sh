#!/bin/bash
# User Data Script for Monitoring EC2 Instance
# This script sets up Prometheus, Grafana, and PostgreSQL monitoring

set -e

# Variables (passed from Terraform)
DB_HOST="${db_host}"
DB_NAME="${db_name}"
DB_USERNAME="${db_username}"
AWS_REGION="${aws_region}"

# System update
echo "=== Starting system update ==="
yum update -y

# Install required packages
echo "=== Installing required packages ==="
yum install -y \
    docker \
    git \
    wget \
    curl \
    vim \
    htop \
    unzip

# Start and enable Docker
systemctl enable docker
systemctl start docker

# Add ec2-user to docker group
usermod -aG docker ec2-user

# Create monitoring directories
mkdir -p /opt/monitoring/{prometheus,grafana,alertmanager}
mkdir -p /var/lib/prometheus
mkdir -p /var/lib/grafana

# Create Prometheus configuration
cat > /opt/monitoring/prometheus/prometheus.yml << 'PROM_EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "/etc/prometheus/rules/*.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'postgresql'
    static_configs:
      - targets: ['DATABASE_HOST:9187']
    scrape_interval: 30s
    scrape_timeout: 10s

  - job_name: 'node'
    static_configs:
      - targets: ['DATABASE_HOST:9100']
    scrape_interval: 30s

  - job_name: 'monitoring-node'
    static_configs:
      - targets: ['localhost:9100']
    scrape_interval: 30s
PROM_EOF

# Replace DATABASE_HOST placeholder with actual DB host
sed -i "s/DATABASE_HOST/$DB_HOST/g" /opt/monitoring/prometheus/prometheus.yml

# Create Prometheus rules directory and basic rules
mkdir -p /opt/monitoring/prometheus/rules

cat > /opt/monitoring/prometheus/rules/postgresql.yml << 'RULES_EOF'
groups:
  - name: postgresql
    rules:
      - alert: PostgreSQLDown
        expr: pg_up == 0
        for: 0m
        labels:
          severity: critical
        annotations:
          summary: PostgreSQL instance is down
          description: "PostgreSQL instance {{ $labels.instance }} is down"

      - alert: PostgreSQLTooManyConnections
        expr: sum by (instance) (pg_stat_activity_count{state != "idle"}) > 80
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: PostgreSQL has too many connections
          description: "PostgreSQL instance {{ $labels.instance }} has {{ $value }} active connections"

      - alert: PostgreSQLHighQPS
        expr: rate(pg_stat_database_xact_commit_total[5m]) + rate(pg_stat_database_xact_rollback_total[5m]) > 1000
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: PostgreSQL high query rate
          description: "PostgreSQL instance {{ $labels.instance }} has high query rate: {{ $value }}"

      - alert: PostgreSQLSlowQueries
        expr: pg_stat_activity_max_tx_duration{state!="idle"} > 300
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: PostgreSQL slow queries detected
          description: "PostgreSQL instance {{ $labels.instance }} has queries running longer than 5 minutes"
RULES_EOF

# Create Alertmanager configuration
cat > /opt/monitoring/alertmanager/alertmanager.yml << 'AM_EOF'
global:
  smtp_smarthost: 'localhost:587'
  smtp_from: 'monitoring@oddiya.com'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'

receivers:
- name: 'web.hook'
  webhook_configs:
  - url: 'http://127.0.0.1:5001/'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'dev', 'instance']
AM_EOF

# Create Grafana configuration
mkdir -p /opt/monitoring/grafana/provisioning/{dashboards,datasources}

cat > /opt/monitoring/grafana/provisioning/datasources/prometheus.yml << 'DS_EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
DS_EOF

cat > /opt/monitoring/grafana/provisioning/dashboards/dashboard.yml << 'DASH_EOF'
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
DASH_EOF

# Create Docker Compose file for monitoring stack
cat > /opt/monitoring/docker-compose.yml << 'COMPOSE_EOF'
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=30d'
      - '--web.enable-lifecycle'
      - '--web.enable-admin-api'
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus:/etc/prometheus
      - /var/lib/prometheus:/prometheus
    networks:
      - monitoring
    restart: unless-stopped

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    volumes:
      - /var/lib/grafana:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin123
      - GF_USERS_ALLOW_SIGN_UP=false
    networks:
      - monitoring
    restart: unless-stopped

  alertmanager:
    image: prom/alertmanager:latest
    container_name: alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager:/etc/alertmanager
    networks:
      - monitoring
    restart: unless-stopped

  node-exporter:
    image: prom/node-exporter:latest
    container_name: node-exporter
    command:
      - '--path.rootfs=/host'
    ports:
      - "9100:9100"
    volumes:
      - '/:/host:ro,rslave'
    networks:
      - monitoring
    restart: unless-stopped

networks:
  monitoring:
    driver: bridge
COMPOSE_EOF

# Create installation script for PostgreSQL exporter on DB server
cat > /opt/monitoring/install-postgres-exporter.sh << 'PG_EXPORTER_EOF'
#!/bin/bash
# Script to install PostgreSQL exporter on database server
# This should be run on the PostgreSQL EC2 instance

set -e

DB_HOST="$1"
DB_NAME="$2"
DB_USERNAME="$3"

# Install PostgreSQL exporter
wget https://github.com/prometheus-community/postgres_exporter/releases/download/v0.11.1/postgres_exporter-0.11.1.linux-amd64.tar.gz
tar xvfz postgres_exporter-0.11.1.linux-amd64.tar.gz
sudo mv postgres_exporter-0.11.1.linux-amd64/postgres_exporter /usr/local/bin/
rm -rf postgres_exporter-*

# Create postgres_exporter user
sudo useradd --no-create-home --shell /bin/false postgres_exporter

# Create environment file
sudo tee /etc/default/postgres_exporter > /dev/null << ENV_EOF
DATA_SOURCE_NAME="postgresql://$DB_USERNAME@$DB_HOST:5432/$DB_NAME?sslmode=require"
ENV_EOF

# Create systemd service
sudo tee /etc/systemd/system/postgres_exporter.service > /dev/null << SERVICE_EOF
[Unit]
Description=PostgreSQL Exporter
After=network.target

[Service]
Type=simple
Restart=always
User=postgres_exporter
Group=postgres_exporter
EnvironmentFile=/etc/default/postgres_exporter
ExecStart=/usr/local/bin/postgres_exporter

[Install]
WantedBy=multi-user.target
SERVICE_EOF

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable postgres_exporter
sudo systemctl start postgres_exporter

echo "PostgreSQL exporter installed and started successfully"
PG_EXPORTER_EOF

chmod +x /opt/monitoring/install-postgres-exporter.sh

# Set proper permissions
chown -R ec2-user:ec2-user /opt/monitoring
chown -R 472:472 /var/lib/grafana  # Grafana user ID in container
chown -R 65534:65534 /var/lib/prometheus  # Nobody user ID in container

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Start monitoring stack
cd /opt/monitoring
/usr/local/bin/docker-compose up -d

# Wait for services to start
sleep 30

# Create a basic PostgreSQL dashboard JSON file
mkdir -p /var/lib/grafana/dashboards

cat > /var/lib/grafana/dashboards/postgresql-dashboard.json << 'DASHBOARD_EOF'
{
  "dashboard": {
    "id": null,
    "title": "PostgreSQL Database",
    "tags": ["postgresql"],
    "style": "dark",
    "timezone": "browser",
    "refresh": "30s",
    "panels": [
      {
        "id": 1,
        "title": "Database Connections",
        "type": "stat",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0},
        "targets": [
          {
            "expr": "pg_stat_activity_count{state!=\"idle\"}",
            "refId": "A"
          }
        ]
      },
      {
        "id": 2,
        "title": "Query Rate",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0},
        "targets": [
          {
            "expr": "rate(pg_stat_database_xact_commit_total[5m])",
            "refId": "A",
            "legendFormat": "Commits/sec"
          },
          {
            "expr": "rate(pg_stat_database_xact_rollback_total[5m])",
            "refId": "B",
            "legendFormat": "Rollbacks/sec"
          }
        ]
      }
    ],
    "time": {"from": "now-1h", "to": "now"},
    "timepicker": {},
    "schemaVersion": 30,
    "version": 0
  }
}
DASHBOARD_EOF

# Create health check script
cat > /opt/monitoring/health-check.sh << 'HEALTH_EOF'
#!/bin/bash
# Health check script for monitoring services

echo "=== Monitoring Stack Health Check ==="

# Check Docker
if systemctl is-active docker >/dev/null 2>&1; then
    echo "✓ Docker is running"
else
    echo "✗ Docker is not running"
    exit 1
fi

# Check Prometheus
if curl -s http://localhost:9090/-/ready >/dev/null 2>&1; then
    echo "✓ Prometheus is ready"
else
    echo "✗ Prometheus is not ready"
fi

# Check Grafana
if curl -s http://localhost:3000/api/health >/dev/null 2>&1; then
    echo "✓ Grafana is healthy"
else
    echo "✗ Grafana is not healthy"
fi

# Check Alertmanager
if curl -s http://localhost:9093/-/ready >/dev/null 2>&1; then
    echo "✓ Alertmanager is ready"
else
    echo "✗ Alertmanager is not ready"
fi

# Check Node Exporter
if curl -s http://localhost:9100/metrics >/dev/null 2>&1; then
    echo "✓ Node Exporter is working"
else
    echo "✗ Node Exporter is not working"
fi

echo "=== Health Check Complete ==="
HEALTH_EOF

chmod +x /opt/monitoring/health-check.sh

# Create completion marker
echo "=== Monitoring setup completed successfully ==="
echo "$(date): Monitoring setup completed" > /opt/monitoring/setup-complete.log

echo "Monitoring stack setup completed successfully."
echo "Access Grafana at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):3000"
echo "Default login: admin/admin123"
echo "Access Prometheus at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):9090"