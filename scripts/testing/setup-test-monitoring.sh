#!/bin/bash

# Test Monitoring Setup Script
# Installs and configures all monitoring tools

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}${BOLD}    Oddiya Test Monitoring Setup                       ${NC}"
echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════${NC}"

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print status
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check prerequisites
echo -e "\n${BOLD}Checking prerequisites...${NC}"

if ! command_exists java; then
    print_error "Java is not installed. Please install Java 21+"
    exit 1
else
    print_status "Java installed"
fi

if ! command_exists docker; then
    print_warning "Docker is not installed. Some monitoring features will be limited"
else
    print_status "Docker installed"
fi

if ! command_exists gradle && ! [ -f "./gradlew" ]; then
    print_error "Gradle is not available. Please ensure gradlew is present"
    exit 1
else
    print_status "Gradle available"
fi

# Create monitoring directories
echo -e "\n${BOLD}Creating monitoring directories...${NC}"
mkdir -p monitoring/grafana/dashboards
mkdir -p monitoring/grafana/datasources
mkdir -p monitoring/prometheus
mkdir -p build/reports/test-monitoring
mkdir -p build/reports/test-summary
mkdir -p scripts

print_status "Monitoring directories created"

# Install monitoring scripts
echo -e "\n${BOLD}Installing monitoring scripts...${NC}"

# Make scripts directory if not exists
mkdir -p scripts

# Create parse-coverage.sh
cat > scripts/parse-coverage.sh << 'SCRIPT'
#!/bin/bash
# Parse JaCoCo coverage report

CSV_FILE="build/reports/jacoco/test/jacocoTestReport.csv"
if [ -f "$CSV_FILE" ]; then
    # Calculate coverage from CSV
    LINES=$(tail -n +2 "$CSV_FILE" | awk -F',' '{missed+=$4; covered+=$5} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
    BRANCHES=$(tail -n +2 "$CSV_FILE" | awk -F',' '{missed+=$6; covered+=$7} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
    
    echo "  Line Coverage:   ${LINES}%"
    echo "  Branch Coverage: ${BRANCHES}%"
else
    echo "  No coverage data found"
fi
SCRIPT

# Create parse-test-results.sh
cat > scripts/parse-test-results.sh << 'SCRIPT'
#!/bin/bash
# Parse test execution results

if [ -d "build/test-results/test" ]; then
    TOTAL=$(find build/test-results/test -name "*.xml" -exec grep -c '<testcase' {} \; | awk '{sum+=$1} END {print sum}')
    FAILURES=$(find build/test-results/test -name "*.xml" -exec grep -c '<failure' {} \; | awk '{sum+=$1} END {print sum}')
    ERRORS=$(find build/test-results/test -name "*.xml" -exec grep -c '<error' {} \; | awk '{sum+=$1} END {print sum}')
    SKIPPED=$(find build/test-results/test -name "*.xml" -exec grep -c '<skipped' {} \; | awk '{sum+=$1} END {print sum}')
    
    PASSED=$((TOTAL - FAILURES - ERRORS - SKIPPED))
    if [ "$TOTAL" -gt 0 ]; then
        SUCCESS_RATE=$((PASSED * 100 / TOTAL))
    else
        SUCCESS_RATE=0
    fi
    
    echo "  Total Tests:   $TOTAL"
    echo "  Passed:        $PASSED"
    echo "  Failed:        $((FAILURES + ERRORS))"
    echo "  Skipped:       $SKIPPED"
    echo "  Success Rate:  ${SUCCESS_RATE}%"
else
    echo "  No test results found"
fi
SCRIPT

# Create parse-security-report.sh
cat > scripts/parse-security-report.sh << 'SCRIPT'
#!/bin/bash
# Parse OWASP dependency check report

REPORT="build/reports/dependency-check-report.html"
if [ -f "$REPORT" ]; then
    CRITICAL=$(grep -o "Critical</td>" "$REPORT" | wc -l || echo "0")
    HIGH=$(grep -o "High</td>" "$REPORT" | wc -l || echo "0")
    MEDIUM=$(grep -o "Medium</td>" "$REPORT" | wc -l || echo "0")
    LOW=$(grep -o "Low</td>" "$REPORT" | wc -l || echo "0")
    
    echo "  Critical: $CRITICAL"
    echo "  High:     $HIGH"
    echo "  Medium:   $MEDIUM"
    echo "  Low:      $LOW"
    
    if [ "$CRITICAL" -gt 0 ]; then
        echo "  ⚠️  Critical vulnerabilities found!"
    fi
else
    echo "  No security report found"
fi
SCRIPT

# Create parse-performance-results.sh
cat > scripts/parse-performance-results.sh << 'SCRIPT'
#!/bin/bash
# Parse performance test results

JMETER_DIR="build/reports/jmeter"
GATLING_DIR="build/reports/gatling"

if [ -d "$JMETER_DIR" ]; then
    echo "  JMeter Results:"
    # Parse JMeter CSV results
    if [ -f "$JMETER_DIR/results.csv" ]; then
        AVG_RESPONSE=$(awk -F',' '{sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}' "$JMETER_DIR/results.csv")
        echo "    Avg Response: ${AVG_RESPONSE}ms"
    fi
elif [ -d "$GATLING_DIR" ]; then
    echo "  Gatling Results:"
    # Parse Gatling stats
    STATS_FILE=$(find "$GATLING_DIR" -name "stats.json" | head -1)
    if [ -f "$STATS_FILE" ]; then
        AVG_RESPONSE=$(grep -o '"mean":[0-9]*' "$STATS_FILE" | head -1 | cut -d':' -f2)
        echo "    Avg Response: ${AVG_RESPONSE}ms"
    fi
else
    echo "  No performance test results"
fi
SCRIPT

# Create start-monitoring.sh
cat > scripts/start-monitoring.sh << 'SCRIPT'
#!/bin/bash
# Start monitoring dashboard

set -e

echo "Starting test monitoring dashboard..."

# Check if docker-compose.monitoring.yml exists
if [ -f "docker-compose.monitoring.yml" ]; then
    docker-compose -f docker-compose.monitoring.yml up -d
    echo "Monitoring stack started!"
    echo "Grafana: http://localhost:3000 (admin/admin)"
    echo "Prometheus: http://localhost:9090"
    echo "InfluxDB: http://localhost:8086"
else
    echo "docker-compose.monitoring.yml not found. Running in standalone mode..."
    ./scripts/monitor-tests-continuous.sh
fi
SCRIPT

# Make all scripts executable
chmod +x scripts/*.sh

print_status "Monitoring scripts installed"

# Create Docker Compose file for monitoring stack
echo -e "\n${BOLD}Creating Docker monitoring configuration...${NC}"

cat > docker-compose.monitoring.yml << 'DOCKER'
version: '3.8'

services:
  grafana:
    image: grafana/grafana:latest
    container_name: oddiya-grafana
    ports:
      - "3000:3000"
    volumes:
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
      - grafana-storage:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_INSTALL_PLUGINS=grafana-piechart-panel,grafana-clock-panel
    networks:
      - monitoring

  prometheus:
    image: prom/prometheus:latest
    container_name: oddiya-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - monitoring

  influxdb:
    image: influxdb:1.8
    container_name: oddiya-influxdb
    ports:
      - "8086:8086"
    environment:
      - INFLUXDB_DB=testmetrics
      - INFLUXDB_ADMIN_USER=admin
      - INFLUXDB_ADMIN_PASSWORD=admin123
      - INFLUXDB_HTTP_AUTH_ENABLED=true
    volumes:
      - influxdb-data:/var/lib/influxdb
    networks:
      - monitoring

volumes:
  grafana-storage:
  prometheus-data:
  influxdb-data:

networks:
  monitoring:
    driver: bridge
DOCKER

print_status "Docker compose configuration created"

# Create Prometheus configuration
echo -e "\n${BOLD}Creating Prometheus configuration...${NC}"

cat > monitoring/prometheus/prometheus.yml << 'PROM'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-actuator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    
  - job_name: 'test-metrics'
    static_configs:
      - targets: ['host.docker.internal:9091']
PROM

print_status "Prometheus configuration created"

# Create Grafana datasource configuration
echo -e "\n${BOLD}Creating Grafana datasources...${NC}"

cat > monitoring/grafana/datasources/prometheus.yml << 'DATASOURCE'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    
  - name: InfluxDB
    type: influxdb
    access: proxy
    url: http://influxdb:8086
    database: testmetrics
    user: admin
    secureJsonData:
      password: admin123
    editable: true
DATASOURCE

print_status "Grafana datasources configured"

# Create Grafana dashboard
echo -e "\n${BOLD}Creating Grafana dashboard...${NC}"

cat > monitoring/grafana/dashboards/dashboard.yml << 'DASHBOARD_CONFIG'
apiVersion: 1

providers:
  - name: 'Test Monitoring'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
DASHBOARD_CONFIG

cat > monitoring/grafana/dashboards/test-monitoring.json << 'DASHBOARD'
{
  "dashboard": {
    "id": null,
    "title": "Oddiya Test Monitoring",
    "tags": ["testing", "quality"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0},
        "type": "graph",
        "title": "Test Coverage Trend",
        "targets": [
          {
            "expr": "test_coverage_line",
            "legendFormat": "Line Coverage"
          },
          {
            "expr": "test_coverage_branch",
            "legendFormat": "Branch Coverage"
          }
        ]
      },
      {
        "id": 2,
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0},
        "type": "graph",
        "title": "Test Execution Time",
        "targets": [
          {
            "expr": "test_duration_seconds",
            "legendFormat": "{{test_suite}}"
          }
        ]
      },
      {
        "id": 3,
        "gridPos": {"h": 8, "w": 8, "x": 0, "y": 8},
        "type": "stat",
        "title": "Total Tests",
        "targets": [
          {
            "expr": "test_total"
          }
        ]
      },
      {
        "id": 4,
        "gridPos": {"h": 8, "w": 8, "x": 8, "y": 8},
        "type": "stat",
        "title": "Test Success Rate",
        "targets": [
          {
            "expr": "test_success_rate"
          }
        ]
      },
      {
        "id": 5,
        "gridPos": {"h": 8, "w": 8, "x": 16, "y": 8},
        "type": "stat",
        "title": "Mutation Score",
        "targets": [
          {
            "expr": "mutation_score"
          }
        ]
      }
    ],
    "refresh": "30s",
    "version": 1
  },
  "overwrite": true
}
DASHBOARD

print_status "Grafana dashboard created"

# Create gradle configuration for test monitoring
echo -e "\n${BOLD}Updating Gradle configuration...${NC}"

# Check if build.gradle exists
if [ -f "build.gradle" ]; then
    # Add test monitoring configuration if not already present
    if ! grep -q "testLogging" build.gradle; then
        cat >> build.gradle << 'GRADLE'

// Test Monitoring Configuration
test {
    testLogging {
        events "passed", "skipped", "failed", "standardOut", "standardError"
        showExceptions true
        showCauses true
        showStackTraces true
        
        afterSuite { desc, result ->
            if (!desc.parent) {
                println "\n════════════════════════════════════════"
                println "Test Results Summary:"
                println "  Tests run: ${result.testCount}"
                println "  Passed: ${result.successfulTestCount}"
                println "  Failed: ${result.failedTestCount}"
                println "  Skipped: ${result.skippedTestCount}"
                println "  Success rate: ${result.testCount > 0 ? (result.successfulTestCount * 100 / result.testCount) : 0}%"
                println "  Time: ${result.endTime - result.startTime}ms"
                println "════════════════════════════════════════\n"
            }
        }
    }
}

jacocoTestReport {
    dependsOn test
    
    reports {
        xml.required = true
        html.required = true
        csv.required = true
    }
    
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/config/**',
                '**/entity/**',
                '**/dto/**',
                '**/*Application*'
            ])
        }))
    }
    
    doLast {
        def csvFile = file("${buildDir}/reports/jacoco/test/jacocoTestReport.csv")
        if (csvFile.exists()) {
            def lines = csvFile.readLines()
            if (lines.size() > 1) {
                def totals = lines.drop(1).collect { it.split(',') }
                def missed = totals.sum { it[3] as int } ?: 0
                def covered = totals.sum { it[4] as int } ?: 0
                def coverage = (missed + covered) > 0 ? (covered * 100 / (missed + covered)) : 0
                println "Line coverage: ${coverage.round(2)}%"
            }
        }
    }
}
GRADLE
        print_status "Gradle test configuration added"
    else
        print_status "Gradle test configuration already exists"
    fi
else
    print_warning "build.gradle not found"
fi

# Create sample monitoring properties
echo -e "\n${BOLD}Creating monitoring properties...${NC}"

cat > monitoring.properties << 'PROPS'
# Test Monitoring Configuration
monitoring.enabled=true
monitoring.coverage.threshold=80
monitoring.test.success.threshold=98
monitoring.performance.threshold=200
monitoring.security.critical.threshold=0

# Alert Configuration
alert.email.enabled=false
alert.email.recipients=team@oddiya.com
alert.slack.enabled=false
alert.slack.webhook=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Report Generation
report.auto.generate=true
report.format=html,json
report.output.dir=build/reports/test-summary
PROPS

print_status "Monitoring properties created"

# Final summary
echo -e "\n${BLUE}${BOLD}════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}✅ Test Monitoring Setup Complete!${NC}"
echo -e "${BLUE}${BOLD}════════════════════════════════════════════════════════${NC}"

echo -e "\n${BOLD}Next Steps:${NC}"
echo -e "1. Run tests with coverage: ${YELLOW}./gradlew test jacocoTestReport${NC}"
echo -e "2. Start monitoring dashboard: ${YELLOW}./scripts/start-monitoring.sh${NC}"
echo -e "3. View continuous monitoring: ${YELLOW}./scripts/monitor-tests-continuous.sh${NC}"
echo -e "4. Generate test report: ${YELLOW}./scripts/generate-test-report.sh${NC}"
echo -e "5. Check test health: ${YELLOW}./scripts/test-health-check.sh${NC}"

echo -e "\n${BOLD}Available Dashboards:${NC}"
echo -e "  📊 Grafana: ${BLUE}http://localhost:3000${NC} (admin/admin)"
echo -e "  📈 Coverage: ${BLUE}build/reports/jacoco/test/html/index.html${NC}"
echo -e "  🧪 Tests: ${BLUE}build/reports/tests/test/index.html${NC}"

echo -e "\n${GREEN}Happy Testing! 🚀${NC}"