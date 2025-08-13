#!/bin/bash

# Oddiya Resource Utilization Performance Test Script
# This script runs comprehensive resource utilization tests including CPU, memory, and thread monitoring
# Usage: ./resource-utilization-test.sh [test_type] [duration] [target_url]

set -euo pipefail

# Default configuration
DEFAULT_TEST_TYPE="all"
DEFAULT_DURATION=1800  # 30 minutes
DEFAULT_TARGET_URL="http://localhost:8080"
DEFAULT_RESULTS_DIR="results/resource-utilization"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Parse command line arguments
TEST_TYPE=${1:-$DEFAULT_TEST_TYPE}
DURATION=${2:-$DEFAULT_DURATION}
TARGET_URL=${3:-$DEFAULT_TARGET_URL}
RESULTS_DIR=${4:-$DEFAULT_RESULTS_DIR}

# Timestamp for unique file names
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$RESULTS_DIR/resource_test_log_$TIMESTAMP.log"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Logging functions
log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1" | tee -a "$LOG_FILE"
}

log_info() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] INFO:${NC} $1" | tee -a "$LOG_FILE"
}

# Check dependencies
check_dependencies() {
    local missing_tools=()
    
    if ! command -v jmeter &> /dev/null; then
        missing_tools+=("jmeter")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_tools+=("curl")
    fi
    
    if ! command -v vmstat &> /dev/null; then
        missing_tools+=("vmstat")
    fi
    
    if ! command -v iostat &> /dev/null; then
        missing_tools+=("iostat")
    fi
    
    if ! command -v netstat &> /dev/null; then
        missing_tools+=("netstat")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log "Please install missing tools before running this script"
        exit 1
    fi
}

# Check if Oddiya application is running
check_application() {
    log_info "Checking if Oddiya application is running..."
    
    local health_check_url="$TARGET_URL/actuator/health"
    local max_retries=5
    local retry_count=0
    
    while [[ $retry_count -lt $max_retries ]]; do
        if curl -sf "$health_check_url" > /dev/null 2>&1; then
            log "✓ Oddiya application is running and healthy"
            return 0
        fi
        
        retry_count=$((retry_count + 1))
        log_warning "Application not ready, attempt $retry_count/$max_retries"
        sleep 5
    done
    
    log_error "Oddiya application is not running or not healthy"
    log "Please start the application before running performance tests"
    exit 1
}

# Get system baseline metrics
collect_baseline_metrics() {
    log_info "Collecting baseline system metrics..."
    
    local baseline_file="$RESULTS_DIR/baseline_metrics_$TIMESTAMP.txt"
    
    {
        echo "==============================================="
        echo "System Baseline Metrics"
        echo "==============================================="
        echo "Timestamp: $(date)"
        echo "Target URL: $TARGET_URL"
        echo "Test Duration: $DURATION seconds"
        echo ""
        
        echo "System Information:"
        echo "-------------------"
        uname -a
        echo ""
        
        echo "CPU Information:"
        echo "----------------"
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            lscpu | grep -E "(Model name|CPU\(s\)|Thread|Core)"
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            sysctl -n machdep.cpu.brand_string
            sysctl -n hw.ncpu
        fi
        echo ""
        
        echo "Memory Information:"
        echo "------------------"
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            free -h
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            vm_stat
        fi
        echo ""
        
        echo "Current Load Average:"
        echo "--------------------"
        uptime
        echo ""
        
        echo "Current Process Count:"
        echo "---------------------"
        ps aux | wc -l
        echo ""
        
        echo "Current Network Connections:"
        echo "---------------------------"
        netstat -an | grep ESTABLISHED | wc -l
        echo ""
        
        echo "Java Processes:"
        echo "--------------"
        pgrep -f java | while read pid; do
            ps -p $pid -o pid,pcpu,pmem,rss,nlwp,cmd
        done 2>/dev/null || echo "No Java processes found"
        echo ""
        
    } > "$baseline_file"
    
    log "Baseline metrics saved to: $baseline_file"
}

# CPU utilization test
run_cpu_test() {
    log_info "Starting CPU utilization test..."
    
    local cpu_test_file="$RESULTS_DIR/cpu_utilization_test_$TIMESTAMP.jtl"
    local cpu_jmx_file="performance-tests/jmeter/load-tests/oddiya-load-test.jmx"
    
    # Run JMeter test with high concurrency for CPU stress
    jmeter -n \
        -t "$cpu_jmx_file" \
        -l "$cpu_test_file" \
        -Jload.users=100 \
        -Jramp.time=60 \
        -Jtest.duration=$DURATION \
        -Jbase.url="$TARGET_URL" \
        -e \
        -o "$RESULTS_DIR/cpu_test_report_$TIMESTAMP" \
        > "$RESULTS_DIR/cpu_test_output_$TIMESTAMP.log" 2>&1 &
    
    local jmeter_pid=$!
    
    # Monitor CPU usage during test
    local cpu_monitor_file="$RESULTS_DIR/cpu_usage_during_test_$TIMESTAMP.csv"
    echo "timestamp,cpu_user,cpu_system,cpu_idle,cpu_iowait,load_1m,load_5m,load_15m" > "$cpu_monitor_file"
    
    log "CPU test started (PID: $jmeter_pid), monitoring CPU usage..."
    
    local monitor_end_time=$((SECONDS + DURATION + 120))  # Extra time for JMeter startup/shutdown
    
    while [[ $SECONDS -lt $monitor_end_time ]] && kill -0 $jmeter_pid 2>/dev/null; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        local cpu_data=$(vmstat 1 2 | tail -1)
        local cpu_user=$(echo $cpu_data | awk '{print $13}')
        local cpu_system=$(echo $cpu_data | awk '{print $15}')
        local cpu_idle=$(echo $cpu_data | awk '{print $18}')
        local cpu_iowait=$(echo $cpu_data | awk '{print $16}')
        
        local load_avg=$(uptime | awk -F'load average:' '{print $2}' | tr -d ' ')
        local load_1m=$(echo $load_avg | cut -d',' -f1)
        local load_5m=$(echo $load_avg | cut -d',' -f2)
        local load_15m=$(echo $load_avg | cut -d',' -f3)
        
        echo "$timestamp,$cpu_user,$cpu_system,$cpu_idle,$cpu_iowait,$load_1m,$load_5m,$load_15m" >> "$cpu_monitor_file"
        
        sleep 5
    done
    
    wait $jmeter_pid
    local jmeter_exit_code=$?
    
    if [[ $jmeter_exit_code -eq 0 ]]; then
        log "✓ CPU utilization test completed successfully"
    else
        log_warning "CPU utilization test completed with warnings (exit code: $jmeter_exit_code)"
    fi
    
    # Analyze CPU usage
    analyze_cpu_usage "$cpu_monitor_file"
}

# Memory utilization test
run_memory_test() {
    log_info "Starting memory utilization test..."
    
    local memory_test_file="$RESULTS_DIR/memory_utilization_test_$TIMESTAMP.jtl"
    local memory_jmx_file="performance-tests/jmeter/load-tests/oddiya-load-test.jmx"
    
    # Run JMeter test with memory-intensive operations
    jmeter -n \
        -t "$memory_jmx_file" \
        -l "$memory_test_file" \
        -Jload.users=50 \
        -Jramp.time=30 \
        -Jtest.duration=$DURATION \
        -Jbase.url="$TARGET_URL" \
        -e \
        -o "$RESULTS_DIR/memory_test_report_$TIMESTAMP" \
        > "$RESULTS_DIR/memory_test_output_$TIMESTAMP.log" 2>&1 &
    
    local jmeter_pid=$!
    
    # Monitor memory usage during test
    local memory_monitor_file="$RESULTS_DIR/memory_usage_during_test_$TIMESTAMP.csv"
    echo "timestamp,total_mb,used_mb,free_mb,available_mb,usage_percent,swap_used_mb,java_heap_mb,java_non_heap_mb" > "$memory_monitor_file"
    
    log "Memory test started (PID: $jmeter_pid), monitoring memory usage..."
    
    local monitor_end_time=$((SECONDS + DURATION + 120))
    
    while [[ $SECONDS -lt $monitor_end_time ]] && kill -0 $jmeter_pid 2>/dev/null; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        
        # System memory
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            local mem_total=$(grep MemTotal /proc/meminfo | awk '{print int($2/1024)}')
            local mem_free=$(grep MemFree /proc/meminfo | awk '{print int($2/1024)}')
            local mem_available=$(grep MemAvailable /proc/meminfo | awk '{print int($2/1024)}')
            local mem_used=$((mem_total - mem_free))
            local mem_usage_percent=$((mem_used * 100 / mem_total))
            local swap_used=$(grep SwapTotal /proc/meminfo | awk '{print int($2/1024)}')
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            local mem_total=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
            local mem_free=0  # Simplified for macOS
            local mem_available=$mem_total
            local mem_used=0
            local mem_usage_percent=0
            local swap_used=0
        fi
        
        # Java heap information if available
        local java_heap_mb="N/A"
        local java_non_heap_mb="N/A"
        local java_pid=$(pgrep -f "java.*oddiya" | head -1)
        
        if [[ -n "$java_pid" ]] && command -v jstat &> /dev/null; then
            local heap_stats=$(jstat -gc $java_pid 2>/dev/null || echo "")
            if [[ -n "$heap_stats" ]]; then
                java_heap_mb=$(echo $heap_stats | tail -1 | awk '{print int(($3+$4+$6+$8)/1024)}')
                java_non_heap_mb=$(echo $heap_stats | tail -1 | awk '{print int(($10+$12)/1024)}')
            fi
        fi
        
        echo "$timestamp,$mem_total,$mem_used,$mem_free,$mem_available,$mem_usage_percent,$swap_used,$java_heap_mb,$java_non_heap_mb" >> "$memory_monitor_file"
        
        sleep 5
    done
    
    wait $jmeter_pid
    local jmeter_exit_code=$?
    
    if [[ $jmeter_exit_code -eq 0 ]]; then
        log "✓ Memory utilization test completed successfully"
    else
        log_warning "Memory utilization test completed with warnings (exit code: $jmeter_exit_code)"
    fi
    
    # Analyze memory usage
    analyze_memory_usage "$memory_monitor_file"
}

# Thread utilization test
run_thread_test() {
    log_info "Starting thread utilization test..."
    
    local thread_test_file="$RESULTS_DIR/thread_utilization_test_$TIMESTAMP.jtl"
    local thread_jmx_file="performance-tests/jmeter/stress-tests/oddiya-stress-test.jmx"
    
    # Run JMeter test with high thread count
    jmeter -n \
        -t "$thread_jmx_file" \
        -l "$thread_test_file" \
        -Jstress.level=1 \
        -Jtest.duration=$DURATION \
        -Jbase.url="$TARGET_URL" \
        -e \
        -o "$RESULTS_DIR/thread_test_report_$TIMESTAMP" \
        > "$RESULTS_DIR/thread_test_output_$TIMESTAMP.log" 2>&1 &
    
    local jmeter_pid=$!
    
    # Monitor thread usage during test
    local thread_monitor_file="$RESULTS_DIR/thread_usage_during_test_$TIMESTAMP.csv"
    echo "timestamp,total_threads,java_threads,running_processes,sleeping_processes,tcp_connections,open_files" > "$thread_monitor_file"
    
    log "Thread test started (PID: $jmeter_pid), monitoring thread usage..."
    
    local monitor_end_time=$((SECONDS + DURATION + 120))
    
    while [[ $SECONDS -lt $monitor_end_time ]] && kill -0 $jmeter_pid 2>/dev/null; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        
        # System thread count
        local total_threads=0
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            total_threads=$(ps -eLf | wc -l)
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            total_threads=$(ps -M | wc -l)
        fi
        
        # Java thread count
        local java_threads="N/A"
        local java_pid=$(pgrep -f "java.*oddiya" | head -1)
        if [[ -n "$java_pid" ]]; then
            if [[ "$OSTYPE" == "linux-gnu"* ]]; then
                java_threads=$(ps -p $java_pid -o nlwp --no-headers 2>/dev/null || echo "N/A")
            elif [[ "$OSTYPE" == "darwin"* ]]; then
                java_threads=$(ps -M -p $java_pid | wc -l 2>/dev/null || echo "N/A")
            fi
        fi
        
        # Process states
        local running_processes=$(ps -eo stat | grep -c '^R' || echo "0")
        local sleeping_processes=$(ps -eo stat | grep -c '^S' || echo "0")
        
        # Network connections
        local tcp_connections=$(netstat -an 2>/dev/null | grep -c ESTABLISHED || echo "0")
        
        # Open files (for Java process)
        local open_files="N/A"
        if [[ -n "$java_pid" ]] && command -v lsof &> /dev/null; then
            open_files=$(lsof -p $java_pid 2>/dev/null | wc -l || echo "N/A")
        fi
        
        echo "$timestamp,$total_threads,$java_threads,$running_processes,$sleeping_processes,$tcp_connections,$open_files" >> "$thread_monitor_file"
        
        sleep 5
    done
    
    wait $jmeter_pid
    local jmeter_exit_code=$?
    
    if [[ $jmeter_exit_code -eq 0 ]]; then
        log "✓ Thread utilization test completed successfully"
    else
        log_warning "Thread utilization test completed with warnings (exit code: $jmeter_exit_code)"
    fi
    
    # Analyze thread usage
    analyze_thread_usage "$thread_monitor_file"
}

# Analyze CPU usage results
analyze_cpu_usage() {
    local cpu_file=$1
    local analysis_file="$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt"
    
    {
        echo "CPU Usage Analysis"
        echo "=================="
        echo ""
        
        # Calculate averages
        local avg_user=$(awk -F',' 'NR>1 {sum+=$2} END {printf "%.1f", sum/(NR-1)}' "$cpu_file")
        local avg_system=$(awk -F',' 'NR>1 {sum+=$3} END {printf "%.1f", sum/(NR-1)}' "$cpu_file")
        local avg_idle=$(awk -F',' 'NR>1 {sum+=$4} END {printf "%.1f", sum/(NR-1)}' "$cpu_file")
        local avg_iowait=$(awk -F',' 'NR>1 {sum+=$5} END {printf "%.1f", sum/(NR-1)}' "$cpu_file")
        local avg_load_1m=$(awk -F',' 'NR>1 {sum+=$6} END {printf "%.2f", sum/(NR-1)}' "$cpu_file")
        
        echo "Average CPU Usage:"
        echo "  User:     ${avg_user}%"
        echo "  System:   ${avg_system}%"
        echo "  I/O Wait: ${avg_iowait}%"
        echo "  Idle:     ${avg_idle}%"
        echo "  Total:    $(echo "100 - $avg_idle" | bc -l | awk '{printf "%.1f", $1}')%"
        echo ""
        
        echo "Average Load:"
        echo "  1-minute: $avg_load_1m"
        echo ""
        
        # Find peak usage
        local max_usage=$(awk -F',' 'NR>1 {usage=100-$4; if(usage>max) max=usage} END {printf "%.1f", max}' "$cpu_file")
        local max_load=$(awk -F',' 'NR>1 {if($6>max) max=$6} END {printf "%.2f", max}' "$cpu_file")
        
        echo "Peak Usage:"
        echo "  CPU:  ${max_usage}%"
        echo "  Load: $max_load"
        echo ""
        
        # Performance thresholds
        echo "Performance Assessment:"
        local total_cpu=$(echo "100 - $avg_idle" | bc -l)
        if (( $(echo "$total_cpu > 80" | bc -l) )); then
            echo "  ⚠️  HIGH CPU usage detected (${total_cpu}% avg)"
        elif (( $(echo "$total_cpu > 60" | bc -l) )); then
            echo "  ⚠️  MODERATE CPU usage (${total_cpu}% avg)"
        else
            echo "  ✓  ACCEPTABLE CPU usage (${total_cpu}% avg)"
        fi
        
        if (( $(echo "$avg_load_1m > 4" | bc -l) )); then
            echo "  ⚠️  HIGH load average ($avg_load_1m)"
        elif (( $(echo "$avg_load_1m > 2" | bc -l) )); then
            echo "  ⚠️  MODERATE load average ($avg_load_1m)"
        else
            echo "  ✓  ACCEPTABLE load average ($avg_load_1m)"
        fi
        
    } > "$analysis_file"
    
    log "CPU analysis saved to: $analysis_file"
    cat "$analysis_file"
}

# Analyze memory usage results
analyze_memory_usage() {
    local memory_file=$1
    local analysis_file="$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt"
    
    {
        echo "Memory Usage Analysis"
        echo "===================="
        echo ""
        
        # Calculate averages
        local avg_total=$(awk -F',' 'NR>1 {sum+=$2} END {printf "%.0f", sum/(NR-1)}' "$memory_file")
        local avg_used=$(awk -F',' 'NR>1 {sum+=$3} END {printf "%.0f", sum/(NR-1)}' "$memory_file")
        local avg_usage_percent=$(awk -F',' 'NR>1 {sum+=$6} END {printf "%.1f", sum/(NR-1)}' "$memory_file")
        local avg_java_heap=$(awk -F',' 'NR>1 && $8!="N/A" {sum+=$8; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' "$memory_file")
        
        echo "Average Memory Usage:"
        echo "  Total:       ${avg_total} MB"
        echo "  Used:        ${avg_used} MB"
        echo "  Usage:       ${avg_usage_percent}%"
        if [[ "$avg_java_heap" != "N/A" ]]; then
            echo "  Java Heap:   ${avg_java_heap} MB"
        fi
        echo ""
        
        # Find peak usage
        local max_usage_percent=$(awk -F',' 'NR>1 {if($6>max) max=$6} END {printf "%.1f", max}' "$memory_file")
        local max_java_heap=$(awk -F',' 'NR>1 && $8!="N/A" {if($8>max) max=$8} END {if(max>0) printf "%.0f", max; else print "N/A"}' "$memory_file")
        
        echo "Peak Usage:"
        echo "  Memory: ${max_usage_percent}%"
        if [[ "$max_java_heap" != "N/A" ]]; then
            echo "  Java Heap: ${max_java_heap} MB"
        fi
        echo ""
        
        # Performance assessment
        echo "Performance Assessment:"
        if (( $(echo "$avg_usage_percent > 85" | bc -l) )); then
            echo "  ⚠️  HIGH memory usage detected (${avg_usage_percent}% avg)"
        elif (( $(echo "$avg_usage_percent > 70" | bc -l) )); then
            echo "  ⚠️  MODERATE memory usage (${avg_usage_percent}% avg)"
        else
            echo "  ✓  ACCEPTABLE memory usage (${avg_usage_percent}% avg)"
        fi
        
        # Check for memory growth (potential leak)
        local first_usage=$(head -2 "$memory_file" | tail -1 | cut -d',' -f6)
        local last_usage=$(tail -1 "$memory_file" | cut -d',' -f6)
        local growth=$(echo "$last_usage - $first_usage" | bc -l)
        
        if (( $(echo "$growth > 10" | bc -l) )); then
            echo "  ⚠️  MEMORY GROWTH detected (+${growth}% during test)"
        else
            echo "  ✓  STABLE memory usage (${growth}% change)"
        fi
        
    } > "$analysis_file"
    
    log "Memory analysis saved to: $analysis_file"
    cat "$analysis_file"
}

# Analyze thread usage results
analyze_thread_usage() {
    local thread_file=$1
    local analysis_file="$RESULTS_DIR/thread_analysis_$TIMESTAMP.txt"
    
    {
        echo "Thread Usage Analysis"
        echo "===================="
        echo ""
        
        # Calculate averages
        local avg_total_threads=$(awk -F',' 'NR>1 {sum+=$2} END {printf "%.0f", sum/(NR-1)}' "$thread_file")
        local avg_java_threads=$(awk -F',' 'NR>1 && $3!="N/A" {sum+=$3; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' "$thread_file")
        local avg_tcp_connections=$(awk -F',' 'NR>1 {sum+=$6} END {printf "%.0f", sum/(NR-1)}' "$thread_file")
        local avg_open_files=$(awk -F',' 'NR>1 && $7!="N/A" {sum+=$7; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' "$thread_file")
        
        echo "Average Thread Usage:"
        echo "  Total Threads:     $avg_total_threads"
        if [[ "$avg_java_threads" != "N/A" ]]; then
            echo "  Java Threads:      $avg_java_threads"
        fi
        echo "  TCP Connections:   $avg_tcp_connections"
        if [[ "$avg_open_files" != "N/A" ]]; then
            echo "  Open Files:        $avg_open_files"
        fi
        echo ""
        
        # Find peak usage
        local max_total_threads=$(awk -F',' 'NR>1 {if($2>max) max=$2} END {printf "%.0f", max}' "$thread_file")
        local max_java_threads=$(awk -F',' 'NR>1 && $3!="N/A" {if($3>max) max=$3} END {if(max>0) printf "%.0f", max; else print "N/A"}' "$thread_file")
        local max_tcp_connections=$(awk -F',' 'NR>1 {if($6>max) max=$6} END {printf "%.0f", max}' "$thread_file")
        
        echo "Peak Usage:"
        echo "  Total Threads:   $max_total_threads"
        if [[ "$max_java_threads" != "N/A" ]]; then
            echo "  Java Threads:    $max_java_threads"
        fi
        echo "  TCP Connections: $max_tcp_connections"
        echo ""
        
        # Performance assessment
        echo "Performance Assessment:"
        
        if [[ "$avg_java_threads" != "N/A" ]]; then
            if (( avg_java_threads > 500 )); then
                echo "  ⚠️  HIGH Java thread count (${avg_java_threads} avg)"
            elif (( avg_java_threads > 200 )); then
                echo "  ⚠️  MODERATE Java thread count (${avg_java_threads} avg)"
            else
                echo "  ✓  ACCEPTABLE Java thread count (${avg_java_threads} avg)"
            fi
        fi
        
        if (( avg_tcp_connections > 1000 )); then
            echo "  ⚠️  HIGH TCP connection count (${avg_tcp_connections} avg)"
        elif (( avg_tcp_connections > 500 )); then
            echo "  ⚠️  MODERATE TCP connection count (${avg_tcp_connections} avg)"
        else
            echo "  ✓  ACCEPTABLE TCP connection count (${avg_tcp_connections} avg)"
        fi
        
        # Check for thread growth
        if [[ "$avg_java_threads" != "N/A" ]]; then
            local first_threads=$(head -2 "$thread_file" | tail -1 | cut -d',' -f3)
            local last_threads=$(tail -1 "$thread_file" | cut -d',' -f3)
            if [[ "$first_threads" != "N/A" ]] && [[ "$last_threads" != "N/A" ]]; then
                local thread_growth=$((last_threads - first_threads))
                if (( thread_growth > 50 )); then
                    echo "  ⚠️  THREAD GROWTH detected (+${thread_growth} threads)"
                else
                    echo "  ✓  STABLE thread count (${thread_growth} change)"
                fi
            fi
        fi
        
    } > "$analysis_file"
    
    log "Thread analysis saved to: $analysis_file"
    cat "$analysis_file"
}

# Generate comprehensive report
generate_comprehensive_report() {
    local report_file="$RESULTS_DIR/comprehensive_resource_report_$TIMESTAMP.html"
    
    {
        cat << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Oddiya Resource Utilization Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .header { text-align: center; color: #333; border-bottom: 2px solid #007bff; padding-bottom: 20px; margin-bottom: 30px; }
        .section { margin: 30px 0; }
        .section h2 { color: #007bff; border-left: 4px solid #007bff; padding-left: 10px; }
        .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
        .metric-card { background: #f8f9fa; padding: 15px; border-radius: 8px; border-left: 4px solid #28a745; }
        .metric-title { font-weight: bold; color: #333; margin-bottom: 10px; }
        .metric-value { font-size: 1.2em; color: #007bff; }
        .warning { border-left-color: #ffc107; background-color: #fff3cd; }
        .error { border-left-color: #dc3545; background-color: #f8d7da; }
        .success { border-left-color: #28a745; background-color: #d4edda; }
        .timestamp { color: #6c757d; font-size: 0.9em; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #007bff; color: white; }
        tr:hover { background-color: #f5f5f5; }
        .footer { text-align: center; margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; color: #6c757d; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 Oddiya Resource Utilization Test Report</h1>
            <div class="timestamp">Generated: $(date)</div>
            <div class="timestamp">Test Duration: $DURATION seconds</div>
            <div class="timestamp">Target: $TARGET_URL</div>
        </div>
EOF

        # Test Summary Section
        echo '        <div class="section">'
        echo '            <h2>📊 Test Summary</h2>'
        echo '            <div class="metrics-grid">'
        
        # Add summary metrics based on available data
        if [[ -f "$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt" ]]; then
            local avg_cpu=$(grep "Total:" "$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt" | awk '{print $2}' | tr -d '%')
            local cpu_class="success"
            if (( $(echo "$avg_cpu > 80" | bc -l 2>/dev/null || echo 0) )); then
                cpu_class="error"
            elif (( $(echo "$avg_cpu > 60" | bc -l 2>/dev/null || echo 0) )); then
                cpu_class="warning"
            fi
            
            echo "                <div class=\"metric-card $cpu_class\">"
            echo "                    <div class=\"metric-title\">Average CPU Usage</div>"
            echo "                    <div class=\"metric-value\">${avg_cpu}%</div>"
            echo "                </div>"
        fi
        
        if [[ -f "$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt" ]]; then
            local avg_memory=$(grep "Usage:" "$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt" | awk '{print $2}' | tr -d '%')
            local memory_class="success"
            if (( $(echo "$avg_memory > 85" | bc -l 2>/dev/null || echo 0) )); then
                memory_class="error"
            elif (( $(echo "$avg_memory > 70" | bc -l 2>/dev/null || echo 0) )); then
                memory_class="warning"
            fi
            
            echo "                <div class=\"metric-card $memory_class\">"
            echo "                    <div class=\"metric-title\">Average Memory Usage</div>"
            echo "                    <div class=\"metric-value\">${avg_memory}%</div>"
            echo "                </div>"
        fi
        
        echo '            </div>'
        echo '        </div>'
        
        # Files Generated Section
        echo '        <div class="section">'
        echo '            <h2>📁 Generated Files</h2>'
        echo '            <table>'
        echo '                <thead>'
        echo '                    <tr><th>File Name</th><th>Size</th><th>Description</th></tr>'
        echo '                </thead>'
        echo '                <tbody>'
        
        ls -la "$RESULTS_DIR"/*_$TIMESTAMP.* 2>/dev/null | while read perm links owner group size month day time filename; do
            local basename_file=$(basename "$filename")
            local description=""
            case "$basename_file" in
                *cpu*) description="CPU utilization monitoring data" ;;
                *memory*) description="Memory utilization monitoring data" ;;
                *thread*) description="Thread utilization monitoring data" ;;
                *baseline*) description="System baseline metrics" ;;
                *analysis*) description="Performance analysis report" ;;
                *) description="Test result file" ;;
            esac
            
            echo "                    <tr>"
            echo "                        <td>$basename_file</td>"
            echo "                        <td>$size</td>"
            echo "                        <td>$description</td>"
            echo "                    </tr>"
        done
        
        echo '                </tbody>'
        echo '            </table>'
        echo '        </div>'
        
        # Recommendations Section
        echo '        <div class="section">'
        echo '            <h2>💡 Performance Recommendations</h2>'
        echo '            <ul>'
        
        # Add recommendations based on analysis
        if [[ -f "$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt" ]]; then
            if grep -q "HIGH CPU usage" "$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🔴 <strong>High CPU Usage Detected:</strong> Consider optimizing CPU-intensive operations, adding caching, or scaling horizontally.</li>'
            fi
            if grep -q "HIGH load average" "$RESULTS_DIR/cpu_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🔴 <strong>High Load Average:</strong> System may be overloaded. Consider increasing server capacity or optimizing request processing.</li>'
            fi
        fi
        
        if [[ -f "$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt" ]]; then
            if grep -q "HIGH memory usage" "$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🔴 <strong>High Memory Usage:</strong> Monitor for memory leaks and consider increasing heap size or optimizing memory usage.</li>'
            fi
            if grep -q "MEMORY GROWTH detected" "$RESULTS_DIR/memory_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🟡 <strong>Memory Growth Detected:</strong> Investigate potential memory leaks and implement proper resource cleanup.</li>'
            fi
        fi
        
        if [[ -f "$RESULTS_DIR/thread_analysis_$TIMESTAMP.txt" ]]; then
            if grep -q "HIGH.*thread count" "$RESULTS_DIR/thread_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🔴 <strong>High Thread Count:</strong> Review thread pool configurations and consider using async processing.</li>'
            fi
            if grep -q "THREAD GROWTH detected" "$RESULTS_DIR/thread_analysis_$TIMESTAMP.txt"; then
                echo '                <li>🟡 <strong>Thread Growth Detected:</strong> Check for thread leaks and ensure proper thread pool management.</li>'
            fi
        fi
        
        echo '                <li>✅ <strong>General:</strong> Continue monitoring these metrics in production and set up automated alerting for threshold violations.</li>'
        echo '            </ul>'
        echo '        </div>'
        
        echo '        <div class="footer">'
        echo '            <p>Generated by Oddiya Performance Testing Suite</p>'
        echo '        </div>'
        echo '    </div>'
        echo '</body>'
        echo '</html>'
        
    } > "$report_file"
    
    log "Comprehensive HTML report generated: $report_file"
}

# Print usage information
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Run comprehensive resource utilization tests for Oddiya application.

OPTIONS:
    -t, --test-type TYPE     Test type: cpu, memory, thread, or all (default: $DEFAULT_TEST_TYPE)
    -d, --duration SECONDS   Test duration in seconds (default: $DEFAULT_DURATION)
    -u, --url URL           Target application URL (default: $DEFAULT_TARGET_URL)
    -o, --output-dir DIR    Results output directory (default: $DEFAULT_RESULTS_DIR)
    -h, --help              Show this help message

TEST TYPES:
    cpu      CPU utilization test with high concurrency
    memory   Memory utilization test with memory-intensive operations
    thread   Thread utilization test with connection pooling
    all      Run all resource utilization tests (default)

EXAMPLES:
    $0                                    # Run all tests with default settings
    $0 -t cpu -d 900                     # Run only CPU test for 15 minutes
    $0 -t all -u http://staging.oddiya.com -d 3600  # Run all tests against staging for 1 hour

NOTES:
    - Requires JMeter and system monitoring tools (vmstat, iostat, netstat)
    - Application must be running and healthy before starting tests
    - Generates detailed CSV files and analysis reports
    - Creates comprehensive HTML report with recommendations
EOF
}

# Parse command line options
parse_options() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--test-type)
                TEST_TYPE="$2"
                shift 2
                ;;
            -d|--duration)
                DURATION="$2"
                shift 2
                ;;
            -u|--url)
                TARGET_URL="$2"
                shift 2
                ;;
            -o|--output-dir)
                RESULTS_DIR="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done
}

# Validate test type
validate_test_type() {
    case "$TEST_TYPE" in
        cpu|memory|thread|all)
            log_info "Test type: $TEST_TYPE"
            ;;
        *)
            log_error "Invalid test type: $TEST_TYPE"
            log "Valid options: cpu, memory, thread, all"
            exit 1
            ;;
    esac
}

# Main execution function
main() {
    # Parse command line options if provided
    if [[ $# -gt 0 ]]; then
        parse_options "$@"
    fi
    
    # Validate inputs
    validate_test_type
    
    # Initialize
    log "=========================================="
    log "Oddiya Resource Utilization Testing"
    log "=========================================="
    log "Test Type: $TEST_TYPE"
    log "Duration: $DURATION seconds"
    log "Target URL: $TARGET_URL"
    log "Results Directory: $RESULTS_DIR"
    log "=========================================="
    
    # Check dependencies and application
    check_dependencies
    check_application
    
    # Collect baseline metrics
    collect_baseline_metrics
    
    # Run tests based on type
    case "$TEST_TYPE" in
        cpu)
            run_cpu_test
            ;;
        memory)
            run_memory_test
            ;;
        thread)
            run_thread_test
            ;;
        all)
            log_info "Running all resource utilization tests..."
            run_cpu_test
            sleep 30  # Brief pause between tests
            run_memory_test
            sleep 30
            run_thread_test
            ;;
    esac
    
    # Generate comprehensive report
    generate_comprehensive_report
    
    log "=========================================="
    log "✅ Resource utilization testing completed successfully"
    log "📁 All results saved in: $RESULTS_DIR"
    log "📊 Open the HTML report for detailed analysis"
    log "=========================================="
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi