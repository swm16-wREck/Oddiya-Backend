#!/bin/bash
# Redis Performance Testing Script
# Agent 6 - Monitoring & Operations Engineer

set -e

# Configuration variables
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
REDIS_BENCHMARK="${REDIS_BENCHMARK:-redis-benchmark}"
ENVIRONMENT="${ENVIRONMENT:-dev}"

# Test configuration
TEST_DURATION="${TEST_DURATION:-60}"      # seconds
CONCURRENT_CLIENTS="${CONCURRENT_CLIENTS:-50}"
REQUESTS_PER_CLIENT="${REQUESTS_PER_CLIENT:-10000}"
KEY_SPACE_SIZE="${KEY_SPACE_SIZE:-1000000}"
DATA_SIZE="${DATA_SIZE:-64}"             # bytes

# Output configuration
RESULTS_DIR="${RESULTS_DIR:-/var/log/redis/performance}"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")
HOSTNAME=$(hostname -s)
RESULTS_FILE="$RESULTS_DIR/performance_test_${HOSTNAME}_${TIMESTAMP}.json"
LOG_FILE="$RESULTS_DIR/performance_test_${HOSTNAME}_${TIMESTAMP}.log"

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
    exit 1
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if redis-cli is available
    if ! command -v "$REDIS_CLI" &> /dev/null; then
        error "redis-cli not found. Please install Redis CLI tools."
    fi
    
    # Check if redis-benchmark is available
    if ! command -v "$REDIS_BENCHMARK" &> /dev/null; then
        error "redis-benchmark not found. Please install Redis tools."
    fi
    
    # Check if Redis is accessible
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    if ! $redis_cmd ping &>/dev/null; then
        error "Cannot connect to Redis at $REDIS_HOST:$REDIS_PORT"
    fi
    
    # Create results directory
    mkdir -p "$RESULTS_DIR"
    if [[ ! -w "$RESULTS_DIR" ]]; then
        error "Results directory $RESULTS_DIR is not writable"
    fi
    
    success "Prerequisites check completed"
}

# Get Redis system information
get_system_info() {
    log "Gathering system information..."
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get Redis info
    REDIS_VERSION=$($redis_cmd info server | grep redis_version | cut -d: -f2 | tr -d '\r')
    REDIS_MODE=$($redis_cmd info server | grep redis_mode | cut -d: -f2 | tr -d '\r')
    USED_MEMORY=$($redis_cmd info memory | grep used_memory_human | cut -d: -f2 | tr -d '\r')
    MAX_MEMORY=$($redis_cmd info memory | grep maxmemory_human | cut -d: -f2 | tr -d '\r')
    CONNECTED_CLIENTS=$($redis_cmd info clients | grep connected_clients | cut -d: -f2 | tr -d '\r')
    
    # Get system info
    CPU_COUNT=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo "unknown")
    TOTAL_MEMORY=$(free -h 2>/dev/null | awk '/^Mem:/ {print $2}' || echo "unknown")
    LOAD_AVERAGE=$(uptime | awk -F'load average:' '{print $2}' | tr -d ' ')
    
    log "System Information:"
    log "  Redis Version: $REDIS_VERSION"
    log "  Redis Mode: $REDIS_MODE"
    log "  Memory Usage: $USED_MEMORY / $MAX_MEMORY"
    log "  Connected Clients: $CONNECTED_CLIENTS"
    log "  CPU Cores: $CPU_COUNT"
    log "  Total Memory: $TOTAL_MEMORY"
    log "  Load Average: $LOAD_AVERAGE"
}

# Run benchmark test
run_benchmark() {
    local test_name="$1"
    local test_command="$2"
    
    log "Running $test_name test..."
    
    local benchmark_cmd="$REDIS_BENCHMARK -h $REDIS_HOST -p $REDIS_PORT -c $CONCURRENT_CLIENTS -n $REQUESTS_PER_CLIENT"
    [[ -n "$REDIS_PASSWORD" ]] && benchmark_cmd="$benchmark_cmd -a $REDIS_PASSWORD"
    
    # Add test-specific parameters
    benchmark_cmd="$benchmark_cmd $test_command"
    
    log "Command: $benchmark_cmd"
    
    # Run the benchmark and capture output
    local start_time=$(date +%s)
    local benchmark_output
    
    if benchmark_output=$($benchmark_cmd 2>&1); then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        # Parse benchmark results
        local requests_per_second=$(echo "$benchmark_output" | grep "requests per second" | tail -1 | awk '{print $1}')
        local avg_latency=$(echo "$benchmark_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
        local p99_latency=$(echo "$benchmark_output" | grep "99.00%" | awk '{print $2}')
        
        log "$test_name Results:"
        log "  Duration: ${duration}s"
        log "  Requests/sec: $requests_per_second"
        log "  Avg Latency: ${avg_latency}ms"
        log "  P99 Latency: ${p99_latency}ms"
        
        # Store results
        echo "$test_name,$requests_per_second,$avg_latency,$p99_latency,$duration" >> "$RESULTS_DIR/benchmark_results.csv"
        
        # Return the full output for JSON compilation
        echo "$benchmark_output"
        
    else
        error "Failed to run $test_name benchmark: $benchmark_output"
    fi
}

# Run comprehensive performance tests
run_performance_tests() {
    log "Starting comprehensive performance tests..."
    
    # Initialize results file
    cat > "$RESULTS_FILE" << EOF
{
    "test_metadata": {
        "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
        "hostname": "$HOSTNAME",
        "environment": "$ENVIRONMENT",
        "redis_host": "$REDIS_HOST:$REDIS_PORT",
        "test_duration": $TEST_DURATION,
        "concurrent_clients": $CONCURRENT_CLIENTS,
        "requests_per_client": $REQUESTS_PER_CLIENT,
        "key_space_size": $KEY_SPACE_SIZE,
        "data_size": $DATA_SIZE
    },
    "system_info": {
        "redis_version": "$REDIS_VERSION",
        "redis_mode": "$REDIS_MODE",
        "used_memory": "$USED_MEMORY",
        "max_memory": "$MAX_MEMORY",
        "connected_clients": "$CONNECTED_CLIENTS",
        "cpu_count": "$CPU_COUNT",
        "total_memory": "$TOTAL_MEMORY",
        "load_average": "$LOAD_AVERAGE"
    },
    "test_results": {
EOF

    # Create CSV header
    echo "test_name,requests_per_second,avg_latency_ms,p99_latency_ms,duration_seconds" > "$RESULTS_DIR/benchmark_results.csv"
    
    local test_count=0
    
    # SET operations test
    log "=== SET Operations Test ==="
    local set_output=$(run_benchmark "SET" "-t set -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local set_rps=$(echo "$set_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local set_latency=$(echo "$set_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # GET operations test
    log "=== GET Operations Test ==="
    local get_output=$(run_benchmark "GET" "-t get -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local get_rps=$(echo "$get_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local get_latency=$(echo "$get_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # INCR operations test
    log "=== INCR Operations Test ==="
    local incr_output=$(run_benchmark "INCR" "-t incr -r $KEY_SPACE_SIZE")
    local incr_rps=$(echo "$incr_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local incr_latency=$(echo "$incr_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # LPUSH operations test
    log "=== LPUSH Operations Test ==="
    local lpush_output=$(run_benchmark "LPUSH" "-t lpush -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local lpush_rps=$(echo "$lpush_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local lpush_latency=$(echo "$lpush_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # LPOP operations test
    log "=== LPOP Operations Test ==="
    local lpop_output=$(run_benchmark "LPOP" "-t lpop -r $KEY_SPACE_SIZE")
    local lpop_rps=$(echo "$lpop_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local lpop_latency=$(echo "$lpop_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # SADD operations test
    log "=== SADD Operations Test ==="
    local sadd_output=$(run_benchmark "SADD" "-t sadd -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local sadd_rps=$(echo "$sadd_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local sadd_latency=$(echo "$sadd_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # HSET operations test
    log "=== HSET Operations Test ==="
    local hset_output=$(run_benchmark "HSET" "-t hset -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local hset_rps=$(echo "$hset_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local hset_latency=$(echo "$hset_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # SPOP operations test
    log "=== SPOP Operations Test ==="
    local spop_output=$(run_benchmark "SPOP" "-t spop -r $KEY_SPACE_SIZE")
    local spop_rps=$(echo "$spop_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local spop_latency=$(echo "$spop_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # ZADD operations test
    log "=== ZADD Operations Test ==="
    local zadd_output=$(run_benchmark "ZADD" "-t zadd -d $DATA_SIZE -r $KEY_SPACE_SIZE")
    local zadd_rps=$(echo "$zadd_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local zadd_latency=$(echo "$zadd_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # ZPOPMIN operations test
    log "=== ZPOPMIN Operations Test ==="
    local zpopmin_output=$(run_benchmark "ZPOPMIN" "-t zpopmin -r $KEY_SPACE_SIZE")
    local zpopmin_rps=$(echo "$zpopmin_output" | grep "requests per second" | tail -1 | awk '{print $1}')
    local zpopmin_latency=$(echo "$zpopmin_output" | grep "latency" | head -1 | awk '{print $2}' | tr -d 'ms')
    
    # Add results to JSON
    cat >> "$RESULTS_FILE" << EOF
        "set_operations": {
            "requests_per_second": $set_rps,
            "avg_latency_ms": $set_latency
        },
        "get_operations": {
            "requests_per_second": $get_rps,
            "avg_latency_ms": $get_latency
        },
        "incr_operations": {
            "requests_per_second": $incr_rps,
            "avg_latency_ms": $incr_latency
        },
        "lpush_operations": {
            "requests_per_second": $lpush_rps,
            "avg_latency_ms": $lpush_latency
        },
        "lpop_operations": {
            "requests_per_second": $lpop_rps,
            "avg_latency_ms": $lpop_latency
        },
        "sadd_operations": {
            "requests_per_second": $sadd_rps,
            "avg_latency_ms": $sadd_latency
        },
        "hset_operations": {
            "requests_per_second": $hset_rps,
            "avg_latency_ms": $hset_latency
        },
        "spop_operations": {
            "requests_per_second": $spop_rps,
            "avg_latency_ms": $spop_latency
        },
        "zadd_operations": {
            "requests_per_second": $zadd_rps,
            "avg_latency_ms": $zadd_latency
        },
        "zpopmin_operations": {
            "requests_per_second": $zpopmin_rps,
            "avg_latency_ms": $zpopmin_latency
        }
EOF
}

# Run memory usage test
run_memory_test() {
    log "=== Memory Usage Test ==="
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Get initial memory usage
    local initial_memory=$($redis_cmd info memory | grep used_memory: | cut -d: -f2 | tr -d '\r')
    
    # Create test data
    log "Creating test data for memory analysis..."
    for i in {1..10000}; do
        $redis_cmd set "memory_test:$i" "$(openssl rand -base64 100)" &>/dev/null
    done
    
    # Get memory usage after data creation
    local final_memory=$($redis_cmd info memory | grep used_memory: | cut -d: -f2 | tr -d '\r')
    
    local memory_diff=$((final_memory - initial_memory))
    local avg_key_size=$((memory_diff / 10000))
    
    log "Memory test results:"
    log "  Initial memory: $initial_memory bytes"
    log "  Final memory: $final_memory bytes"
    log "  Memory increase: $memory_diff bytes"
    log "  Average key size: $avg_key_size bytes"
    
    # Clean up test data
    $redis_cmd eval "for i=1,10000 do redis.call('del', 'memory_test:' .. i) end" 0 &>/dev/null
    
    cat >> "$RESULTS_FILE" << EOF
    },
    "memory_test": {
        "initial_memory_bytes": $initial_memory,
        "final_memory_bytes": $final_memory,
        "memory_increase_bytes": $memory_diff,
        "average_key_size_bytes": $avg_key_size
EOF
}

# Run latency test
run_latency_test() {
    log "=== Latency Test ==="
    
    local redis_cmd="$REDIS_CLI -h $REDIS_HOST -p $REDIS_PORT"
    [[ -n "$REDIS_PASSWORD" ]] && redis_cmd="$redis_cmd -a $REDIS_PASSWORD"
    
    # Run latency test for 30 seconds
    log "Running intrinsic latency test for 30 seconds..."
    
    local latency_output
    if latency_output=$($redis_cmd --latency -i 1 2>&1 &); then
        local latency_pid=$!
        sleep 30
        kill $latency_pid 2>/dev/null || true
        wait $latency_pid 2>/dev/null || true
        
        # Parse latency results
        local min_latency=$(echo "$latency_output" | tail -1 | awk '{print $4}')
        local max_latency=$(echo "$latency_output" | tail -1 | awk '{print $6}')
        local avg_latency=$(echo "$latency_output" | tail -1 | awk '{print $8}')
        
        log "Latency test results:"
        log "  Min latency: ${min_latency}ms"
        log "  Max latency: ${max_latency}ms"
        log "  Avg latency: ${avg_latency}ms"
        
        cat >> "$RESULTS_FILE" << EOF
    },
    "latency_test": {
        "min_latency_ms": $min_latency,
        "max_latency_ms": $max_latency,
        "avg_latency_ms": $avg_latency
EOF
    else
        warning "Latency test failed or unavailable"
        cat >> "$RESULTS_FILE" << EOF
    },
    "latency_test": {
        "min_latency_ms": 0,
        "max_latency_ms": 0,
        "avg_latency_ms": 0
EOF
    fi
}

# Generate performance report
generate_report() {
    log "Generating performance report..."
    
    # Close JSON structure
    cat >> "$RESULTS_FILE" << EOF
    }
}
EOF

    # Calculate overall performance score
    local total_rps=0
    local test_count=0
    
    # Read CSV results and calculate average
    if [[ -f "$RESULTS_DIR/benchmark_results.csv" ]]; then
        while IFS=',' read -r test_name rps latency p99 duration; do
            if [[ "$test_name" != "test_name" && "$rps" =~ ^[0-9]+$ ]]; then
                total_rps=$((total_rps + rps))
                ((test_count++))
            fi
        done < "$RESULTS_DIR/benchmark_results.csv"
    fi
    
    local avg_rps=0
    if [[ $test_count -gt 0 ]]; then
        avg_rps=$((total_rps / test_count))
    fi
    
    local report="Redis Performance Test Report

Test Configuration:
- Host: $REDIS_HOST:$REDIS_PORT
- Environment: $ENVIRONMENT
- Concurrent Clients: $CONCURRENT_CLIENTS
- Requests per Client: $REQUESTS_PER_CLIENT
- Key Space Size: $KEY_SPACE_SIZE
- Data Size: $DATA_SIZE bytes

System Information:
- Redis Version: $REDIS_VERSION
- Redis Mode: $REDIS_MODE
- Memory Usage: $USED_MEMORY / $MAX_MEMORY
- CPU Cores: $CPU_COUNT
- Total Memory: $TOTAL_MEMORY

Performance Summary:
- Total Tests Run: $test_count
- Average Requests/sec: $avg_rps
- Results File: $RESULTS_FILE
- CSV Results: $RESULTS_DIR/benchmark_results.csv

Performance Rating:"

    # Calculate performance rating
    if [[ $avg_rps -gt 100000 ]]; then
        report="$report EXCELLENT (>100K req/s)"
    elif [[ $avg_rps -gt 50000 ]]; then
        report="$report GOOD (50K-100K req/s)"
    elif [[ $avg_rps -gt 20000 ]]; then
        report="$report FAIR (20K-50K req/s)"
    elif [[ $avg_rps -gt 10000 ]]; then
        report="$report POOR (10K-20K req/s)"
    else
        report="$report VERY POOR (<10K req/s)"
    fi
    
    echo "$report"
    success "Performance test completed successfully!"
}

# Main execution function
main() {
    local start_time=$(date +%s)
    
    log "Starting Redis performance test..."
    log "Host: $REDIS_HOST:$REDIS_PORT, Environment: $ENVIRONMENT"
    
    # Check prerequisites
    check_prerequisites
    
    # Get system information
    get_system_info
    
    # Run performance tests
    run_performance_tests
    
    # Run memory test
    run_memory_test
    
    # Run latency test
    run_latency_test
    
    # Generate report
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Performance testing completed in ${duration} seconds"
    generate_report
}

# Show usage information
show_usage() {
    cat << EOF
Redis Performance Testing Script

Usage: $0 [OPTIONS]

Options:
    -h, --help              Show this help message
    -H, --host HOST         Redis host (default: localhost)
    -p, --port PORT         Redis port (default: 6379)
    -a, --auth PASSWORD     Redis password
    -e, --environment ENV   Environment name (default: dev)
    -c, --clients CLIENTS   Concurrent clients (default: 50)
    -n, --requests REQUESTS Requests per client (default: 10000)
    -d, --data-size SIZE    Data size in bytes (default: 64)
    -r, --results-dir DIR   Results directory (default: /var/log/redis/performance)

Environment Variables:
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, ENVIRONMENT,
    CONCURRENT_CLIENTS, REQUESTS_PER_CLIENT, DATA_SIZE, RESULTS_DIR

Examples:
    $0                                          # Basic performance test
    $0 --host redis.example.com --auth secret  # Remote Redis with auth
    $0 --clients 100 --requests 50000          # High-load test
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
            -c|--clients)
                CONCURRENT_CLIENTS="$2"
                shift 2
                ;;
            -n|--requests)
                REQUESTS_PER_CLIENT="$2"
                shift 2
                ;;
            -d|--data-size)
                DATA_SIZE="$2"
                shift 2
                ;;
            -r|--results-dir)
                RESULTS_DIR="$2"
                shift 2
                ;;
            *)
                error "Unknown argument: $1"
                ;;
        esac
    done
}

# Trap signals for cleanup
trap 'error "Performance test interrupted"' INT TERM

# Parse arguments and run main function
parse_arguments "$@"
main

exit 0