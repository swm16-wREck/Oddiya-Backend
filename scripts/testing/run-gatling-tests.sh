#!/bin/bash

# Oddiya Gatling Performance Test Runner
# This script provides easy execution of Gatling performance tests

set -euo pipefail

# Configuration
GATLING_VERSION="3.9.5"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATLING_HOME="${GATLING_HOME:-$SCRIPT_DIR}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="$SCRIPT_DIR/results"
REPORTS_DIR="$SCRIPT_DIR/reports"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1"
}

log_info() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] INFO:${NC} $1"
}

# Help function
show_help() {
    cat << EOF
Oddiya Gatling Performance Test Runner

USAGE:
    $0 [OPTIONS] [TEST_TYPE]

TEST_TYPES:
    load        Run load test simulation (100 users, 30 min)
    stress      Run stress test simulation (100/500/1000 users)
    spike       Run spike test simulation (baseline → spike → recovery)
    soak        Run soak test simulation (50 users, 8 hours)
    benchmark   Run API benchmark simulation (SLA validation)
    all         Run all test types sequentially

OPTIONS:
    -u, --base-url URL      Base URL for testing (default: $BASE_URL)
    -d, --duration MIN      Test duration in minutes (default: varies by test)
    -U, --users COUNT       Number of concurrent users (default: varies by test)
    -r, --ramp MIN          Ramp-up duration in minutes (default: varies by test)
    -s, --stress-level NUM  Stress test level: 1=100, 2=500, 3=1000 users (default: 1)
    -o, --output-dir DIR    Results output directory (default: $RESULTS_DIR)
    -R, --reports-dir DIR   Reports directory (default: $REPORTS_DIR)
    -v, --verbose           Enable verbose output
    -n, --no-reports        Skip HTML report generation
    -h, --help             Show this help

EXAMPLES:
    # Run basic load test
    $0 load

    # Run stress test with 500 users against staging environment
    $0 stress -u https://staging.oddiya.com -s 2

    # Run 4-hour soak test with custom user count
    $0 soak -d 240 -U 30

    # Run benchmark test with verbose output
    $0 benchmark -v

    # Run all tests against production-like environment
    $0 all -u https://perf-test.oddiya.com

ENVIRONMENT VARIABLES:
    GATLING_HOME           Gatling installation directory
    BASE_URL              Default base URL for testing
    JAVA_OPTS             JVM options for Gatling
    
REQUIREMENTS:
    - Java 8+ installed and in PATH
    - Gatling $GATLING_VERSION (auto-downloaded if not found)
    - Target application running and accessible
EOF
}

# Check Java installation
check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        log_error "Please install Java 8 or higher"
        exit 1
    fi
    
    local java_version
    java_version=$(java -version 2>&1 | grep version | cut -d'"' -f2 | cut -d'.' -f1-2)
    log_info "Using Java version: $java_version"
}

# Download and setup Gatling if not present
setup_gatling() {
    local gatling_bin="$GATLING_HOME/bin/gatling.sh"
    
    if [[ ! -f "$gatling_bin" ]]; then
        log_info "Gatling not found, downloading Gatling $GATLING_VERSION..."
        
        local gatling_zip="gatling-charts-highcharts-bundle-$GATLING_VERSION.zip"
        local download_url="https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/$GATLING_VERSION/$gatling_zip"
        
        # Create temporary directory
        local temp_dir=$(mktemp -d)
        
        # Download Gatling
        if command -v curl &> /dev/null; then
            curl -L -o "$temp_dir/$gatling_zip" "$download_url"
        elif command -v wget &> /dev/null; then
            wget -O "$temp_dir/$gatling_zip" "$download_url"
        else
            log_error "Neither curl nor wget found. Please install one to download Gatling"
            exit 1
        fi
        
        # Extract Gatling
        unzip -q "$temp_dir/$gatling_zip" -d "$temp_dir"
        
        # Move to Gatling home
        local extracted_dir="$temp_dir/gatling-charts-highcharts-bundle-$GATLING_VERSION"
        if [[ -d "$extracted_dir" ]]; then
            cp -r "$extracted_dir"/* "$GATLING_HOME/"
            chmod +x "$GATLING_HOME/bin"/*.sh
        else
            log_error "Failed to extract Gatling"
            exit 1
        fi
        
        # Cleanup
        rm -rf "$temp_dir"
        
        log "Gatling $GATLING_VERSION installed successfully"
    else
        log_info "Using existing Gatling installation"
    fi
}

# Prepare test environment
prepare_environment() {
    # Create necessary directories
    mkdir -p "$RESULTS_DIR" "$REPORTS_DIR" "$SCRIPT_DIR/data" "$SCRIPT_DIR/simulations"
    
    # Set Gatling environment variables
    export GATLING_HOME
    export GATLING_CONF="$SCRIPT_DIR/conf"
    export JAVA_OPTS="${JAVA_OPTS:--Xmx4G -XX:+UseG1GC -XX:+UseStringDeduplication}"
    
    # Verify test data files exist
    local required_files=("$SCRIPT_DIR/data/user-credentials.csv" "$SCRIPT_DIR/data/search-queries.csv")
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            log_error "Required data file not found: $file"
            log_error "Please ensure all test data files are present"
            exit 1
        fi
    done
    
    log_info "Test environment prepared"
}

# Run Gatling simulation
run_simulation() {
    local simulation_class="$1"
    local test_name="$2"
    local additional_opts="$3"
    
    log "Starting $test_name simulation..."
    log_info "Simulation: $simulation_class"
    log_info "Base URL: $BASE_URL"
    log_info "Additional options: $additional_opts"
    
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local run_description="$test_name test - $timestamp"
    
    # Prepare Gatling command
    local gatling_cmd=(
        "$GATLING_HOME/bin/gatling.sh"
        "--simulation" "$simulation_class"
        "--results-folder" "$RESULTS_DIR"
        "--run-description" "$run_description"
    )
    
    # Add no-reports flag if specified
    if [[ "${NO_REPORTS:-false}" == "true" ]]; then
        gatling_cmd+=("--no-reports")
    fi
    
    # Set system properties
    export JAVA_OPTS="$JAVA_OPTS -Dbase.url=$BASE_URL $additional_opts"
    
    # Run Gatling
    if [[ "${VERBOSE:-false}" == "true" ]]; then
        "${gatling_cmd[@]}"
    else
        "${gatling_cmd[@]}" 2>&1 | grep -E "(Please open the following file|Reports Generated|Simulation|Error|Exception)" || true
    fi
    
    local exit_code=${PIPESTATUS[0]}
    
    if [[ $exit_code -eq 0 ]]; then
        log "$test_name simulation completed successfully"
        
        # Move reports to organized location
        local latest_report=$(find "$RESULTS_DIR" -name "*$timestamp*" -type d | head -1)
        if [[ -n "$latest_report" && -d "$latest_report" ]]; then
            local report_dest="$REPORTS_DIR/${test_name,,}_$timestamp"
            mv "$latest_report" "$report_dest"
            log_info "Report moved to: $report_dest"
            
            if [[ "${NO_REPORTS:-false}" != "true" ]]; then
                local index_file="$report_dest/index.html"
                if [[ -f "$index_file" ]]; then
                    log_info "Report available at: file://$index_file"
                fi
            fi
        fi
    else
        log_error "$test_name simulation failed with exit code: $exit_code"
        return $exit_code
    fi
}

# Individual test functions
run_load_test() {
    local duration="${DURATION:-30}"
    local users="${USERS:-100}"
    local ramp="${RAMP:-5}"
    
    local opts="-Dduration=$duration -Dusers=$users -Dramp=$ramp"
    run_simulation "OddiyaLoadTestSimulation" "Load Test" "$opts"
}

run_stress_test() {
    local stress_level="${STRESS_LEVEL:-1}"
    local duration="${DURATION:-15}"
    
    local opts="-Dstress.level=$stress_level -Dduration=$duration"
    run_simulation "OddiyaStressTestSimulation" "Stress Test" "$opts"
}

run_spike_test() {
    local baseline_users="${USERS:-10}"
    local spike_users="${SPIKE_USERS:-500}"
    local baseline_duration="${BASELINE_DURATION:-5}"
    local spike_duration="${SPIKE_DURATION:-2}"
    local recovery_duration="${RECOVERY_DURATION:-10}"
    
    local opts="-Dbaseline.users=$baseline_users -Dspike.users=$spike_users"
    opts="$opts -Dbaseline.duration=$baseline_duration -Dspike.duration=$spike_duration"
    opts="$opts -Drecovery.duration=$recovery_duration"
    
    run_simulation "OddiyaSpikeTestSimulation" "Spike Test" "$opts"
}

run_soak_test() {
    local duration="${DURATION:-480}"  # 8 hours default
    local users="${USERS:-50}"
    local ramp="${RAMP:-10}"
    local warmup="${WARMUP:-30}"
    
    local opts="-Dsoak.duration=$duration -Dsoak.users=$users"
    opts="$opts -Dramp.duration=$ramp -Dwarmup.duration=$warmup"
    
    run_simulation "OddiyaSoakTestSimulation" "Soak Test" "$opts"
}

run_benchmark_test() {
    local duration="${DURATION:-10}"
    local users="${USERS:-20}"
    local warmup="${WARMUP:-2}"
    
    local opts="-Dbenchmark.duration=$duration -Dusers.per.endpoint=$users -Dwarmup.duration=$warmup"
    run_simulation "OddiyaApiBenchmarkSimulation" "API Benchmark" "$opts"
}

# Run all tests
run_all_tests() {
    log "Starting comprehensive performance test suite..."
    
    # Run tests in order of increasing intensity
    run_benchmark_test
    sleep 30  # Brief pause between tests
    
    run_load_test
    sleep 60  # Longer pause for system recovery
    
    run_spike_test
    sleep 90  # Longer pause after spike test
    
    run_stress_test
    sleep 120 # Recovery time after stress
    
    # Soak test runs last due to its duration
    log_warning "Starting 8-hour soak test - this will take a significant time"
    run_soak_test
    
    log "All performance tests completed!"
}

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -u|--base-url)
                BASE_URL="$2"
                shift 2
                ;;
            -d|--duration)
                DURATION="$2"
                shift 2
                ;;
            -U|--users)
                USERS="$2"
                shift 2
                ;;
            -r|--ramp)
                RAMP="$2"
                shift 2
                ;;
            -s|--stress-level)
                STRESS_LEVEL="$2"
                shift 2
                ;;
            -o|--output-dir)
                RESULTS_DIR="$2"
                shift 2
                ;;
            -R|--reports-dir)
                REPORTS_DIR="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -n|--no-reports)
                NO_REPORTS=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            load|stress|spike|soak|benchmark|all)
                TEST_TYPE="$1"
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Default to load test if no test type specified
    TEST_TYPE="${TEST_TYPE:-load}"
}

# Validate configuration
validate_configuration() {
    # Check if target application is accessible
    log_info "Validating target application accessibility..."
    
    if command -v curl &> /dev/null; then
        if ! curl -f -s --connect-timeout 10 "$BASE_URL/actuator/health" > /dev/null; then
            log_warning "Target application may not be accessible at $BASE_URL"
            log_warning "Health check endpoint returned an error"
            log_warning "Continuing anyway, but tests may fail..."
        else
            log "Target application is accessible"
        fi
    else
        log_warning "curl not available, skipping connectivity check"
    fi
    
    # Validate numeric parameters
    if [[ -n "${DURATION:-}" && ! "$DURATION" =~ ^[0-9]+$ ]]; then
        log_error "Duration must be a positive integer"
        exit 1
    fi
    
    if [[ -n "${USERS:-}" && ! "$USERS" =~ ^[0-9]+$ ]]; then
        log_error "Users must be a positive integer"
        exit 1
    fi
    
    if [[ -n "${STRESS_LEVEL:-}" && ! "$STRESS_LEVEL" =~ ^[1-3]$ ]]; then
        log_error "Stress level must be 1, 2, or 3"
        exit 1
    fi
}

# Main execution
main() {
    log "Oddiya Gatling Performance Test Runner"
    log "========================================"
    
    # Parse command line arguments
    parse_arguments "$@"
    
    # Validate environment and configuration
    check_java
    validate_configuration
    setup_gatling
    prepare_environment
    
    # Run the specified test
    case "$TEST_TYPE" in
        load)
            run_load_test
            ;;
        stress)
            run_stress_test
            ;;
        spike)
            run_spike_test
            ;;
        soak)
            run_soak_test
            ;;
        benchmark)
            run_benchmark_test
            ;;
        all)
            run_all_tests
            ;;
        *)
            log_error "Unknown test type: $TEST_TYPE"
            show_help
            exit 1
            ;;
    esac
    
    log "Performance testing completed!"
    log_info "Results available in: $RESULTS_DIR"
    log_info "Reports available in: $REPORTS_DIR"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi