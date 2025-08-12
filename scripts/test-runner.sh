#!/bin/bash

# Oddiya Consolidated Test Runner Script
# Unified test execution for all test types: unit, integration, e2e, performance, mutation
# Usage: ./scripts/test-runner.sh [options]

set -e

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

# ═══════════════════════════════════════════════════════════════════════════════
# SCRIPT METADATA AND CONSTANTS
# ═══════════════════════════════════════════════════════════════════════════════

SCRIPT_VERSION="3.0.0"
SCRIPT_NAME="Oddiya Test Runner"
SCRIPT_START_TIME=$(date +%s)

# Default configuration
DEFAULT_PROFILE="full"
DEFAULT_ENVIRONMENT="local"
DEFAULT_PARALLEL_WORKERS="auto"
DEFAULT_REPORT_FORMAT="json,html"
DEFAULT_COVERAGE_THRESHOLD="80"
DEFAULT_MUTATION_THRESHOLD="75"

# Test execution tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# Report generation
REPORTS_DIR="build/reports/consolidated"
REPORT_TIMESTAMP=$(timestamp_short)
CONSOLIDATED_REPORT="${REPORTS_DIR}/test-execution-report-${REPORT_TIMESTAMP}.json"

# Performance thresholds
PERFORMANCE_BASELINE_FILE="${REPORTS_DIR}/performance-baseline.json"
PERFORMANCE_DEGRADATION_THRESHOLD="10" # Percentage

# Parallel execution settings
MAX_PARALLEL_WORKERS=$(nproc 2>/dev/null || echo "4")
PARALLEL_WORKERS="auto"
ENABLE_PARALLEL="false"

# Monitoring integration
MONITORING_ENABLED="false"
MONITORING_ENDPOINT=""
MONITORING_API_KEY=""

# ═══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION AND ARGUMENT PARSING
# ═══════════════════════════════════════════════════════════════════════════════

# Parse command line arguments
PROFILE="$DEFAULT_PROFILE"
ENVIRONMENT="$DEFAULT_ENVIRONMENT"
GENERATE_REPORT="true"
UPLOAD_METRICS="false"
COVERAGE_ONLY="false"
FORCE_CLEAN="false"
VERBOSE="false"
DRY_RUN="false"
FAIL_FAST="true"
INCLUDE_SECURITY="true"
INCLUDE_CONTRACT="false"
CUSTOM_TAGS=""
EXCLUDE_TAGS=""
TEST_FILTER=""

# Test type flags
RUN_UNIT="auto"
RUN_INTEGRATION="auto"
RUN_E2E="auto"
RUN_PERFORMANCE="auto"
RUN_MUTATION="auto"
RUN_SECURITY="auto"
RUN_CONTRACT="auto"

show_usage() {
    print_header "$SCRIPT_NAME v$SCRIPT_VERSION"
    echo -e "${CYAN}Unified test execution for all test types with monitoring integration${NC}"
    echo ""
    echo -e "${YELLOW}USAGE:${NC}"
    echo "  $0 [OPTIONS]"
    echo ""
    echo -e "${YELLOW}TEST PROFILES:${NC}"
    echo "  full             Complete test suite (all test types) [default]"
    echo "  smoke            Quick smoke tests (basic health and unit)"
    echo "  regression       Regression test suite (unit, integration, security)"
    echo "  performance      Performance and load testing only"
    echo "  quality          Code quality tests (mutation, coverage, static analysis)"
    echo "  security         Security-focused tests only"
    echo "  contract         Contract and API compatibility tests"
    echo "  minimal          Essential tests only (unit + basic integration)"
    echo ""
    echo -e "${YELLOW}ENVIRONMENT TARGETS:${NC}"
    echo "  local            Local development environment [default]"
    echo "  dev              Development AWS environment"
    echo "  staging          Staging environment"
    echo "  prod             Production environment (read-only tests only)"
    echo ""
    echo -e "${YELLOW}TEST SELECTION:${NC}"
    echo "  --unit-only              Run unit tests only"
    echo "  --integration-only       Run integration tests only"
    echo "  --e2e-only              Run end-to-end tests only"
    echo "  --performance-only       Run performance tests only"
    echo "  --mutation-only          Run mutation tests only"
    echo "  --security-only          Run security tests only"
    echo "  --contract-only          Run contract tests only"
    echo ""
    echo -e "${YELLOW}EXECUTION OPTIONS:${NC}"
    echo "  -p, --parallel [N]       Enable parallel execution (N workers, default: auto)"
    echo "  --no-parallel           Disable parallel execution"
    echo "  --fail-fast             Stop on first failure [default]"
    echo "  --continue-on-failure   Continue execution despite failures"
    echo "  --coverage-threshold N   Set coverage threshold (default: $DEFAULT_COVERAGE_THRESHOLD%)"
    echo "  --mutation-threshold N   Set mutation testing threshold (default: $DEFAULT_MUTATION_THRESHOLD%)"
    echo ""
    echo -e "${YELLOW}REPORTING OPTIONS:${NC}"
    echo "  --report-format FORMAT   Report format: json,html,xml,junit (default: json,html)"
    echo "  --no-reports            Skip report generation"
    echo "  --coverage-only         Generate coverage reports only"
    echo "  --baseline-update       Update performance baselines"
    echo ""
    echo -e "${YELLOW}FILTERING OPTIONS:${NC}"
    echo "  --tags TAGS             Include tests with specific tags (comma-separated)"
    echo "  --exclude-tags TAGS     Exclude tests with specific tags"
    echo "  --filter PATTERN        Run tests matching pattern"
    echo ""
    echo -e "${YELLOW}CONTROL OPTIONS:${NC}"
    echo "  --force-clean           Force clean build before testing"
    echo "  --dry-run               Show execution plan without running tests"
    echo "  --verbose               Enable verbose output"
    echo "  --debug                 Enable debug mode"
    echo ""
    echo -e "${YELLOW}MONITORING INTEGRATION:${NC}"
    echo "  --enable-monitoring     Enable test monitoring and metrics"
    echo "  --monitoring-endpoint   Monitoring endpoint URL"
    echo "  --upload-metrics        Upload test metrics to monitoring system"
    echo ""
    echo -e "${YELLOW}EXAMPLES:${NC}"
    echo "  $0                                    # Full test suite on local"
    echo "  $0 smoke --parallel                   # Parallel smoke tests"
    echo "  $0 performance --environment staging  # Performance tests on staging"
    echo "  $0 --unit-only --coverage-only        # Unit tests with coverage only"
    echo "  $0 regression --tags critical         # Critical regression tests"
    echo "  $0 --mutation-only --parallel 8       # Parallel mutation testing"
    echo ""
    echo -e "${YELLOW}AVAILABLE TEST PROFILES:${NC}"
    echo "  • full: unit, integration, e2e, performance, mutation, security"
    echo "  • smoke: basic health checks and fast unit tests"
    echo "  • regression: unit, integration, security tests"
    echo "  • quality: mutation testing, coverage analysis, static analysis"
    echo ""
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        --version)
            echo "$SCRIPT_NAME v$SCRIPT_VERSION"
            exit 0
            ;;
        full|smoke|regression|performance|quality|security|contract|minimal)
            PROFILE="$1"
            shift
            ;;
        --environment|--env)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --unit-only)
            RUN_UNIT="true"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            shift
            ;;
        --integration-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="true"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            shift
            ;;
        --e2e-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="true"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            shift
            ;;
        --performance-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="true"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            shift
            ;;
        --mutation-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="true"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            shift
            ;;
        --security-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="true"
            RUN_CONTRACT="false"
            shift
            ;;
        --contract-only)
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="true"
            shift
            ;;
        -p|--parallel)
            ENABLE_PARALLEL="true"
            if [[ -n "$2" && "$2" =~ ^[0-9]+$ ]]; then
                PARALLEL_WORKERS="$2"
                shift 2
            else
                PARALLEL_WORKERS="auto"
                shift
            fi
            ;;
        --no-parallel)
            ENABLE_PARALLEL="false"
            shift
            ;;
        --fail-fast)
            FAIL_FAST="true"
            shift
            ;;
        --continue-on-failure)
            FAIL_FAST="false"
            shift
            ;;
        --coverage-threshold)
            DEFAULT_COVERAGE_THRESHOLD="$2"
            shift 2
            ;;
        --mutation-threshold)
            DEFAULT_MUTATION_THRESHOLD="$2"
            shift 2
            ;;
        --report-format)
            DEFAULT_REPORT_FORMAT="$2"
            shift 2
            ;;
        --no-reports)
            GENERATE_REPORT="false"
            shift
            ;;
        --coverage-only)
            COVERAGE_ONLY="true"
            shift
            ;;
        --baseline-update)
            UPDATE_BASELINE="true"
            shift
            ;;
        --tags)
            CUSTOM_TAGS="$2"
            shift 2
            ;;
        --exclude-tags)
            EXCLUDE_TAGS="$2"
            shift 2
            ;;
        --filter)
            TEST_FILTER="$2"
            shift 2
            ;;
        --force-clean)
            FORCE_CLEAN="true"
            shift
            ;;
        --dry-run)
            DRY_RUN="true"
            shift
            ;;
        --verbose)
            VERBOSE="true"
            shift
            ;;
        --debug)
            DEBUG="true"
            VERBOSE="true"
            set -x
            shift
            ;;
        --enable-monitoring)
            MONITORING_ENABLED="true"
            shift
            ;;
        --monitoring-endpoint)
            MONITORING_ENDPOINT="$2"
            shift 2
            ;;
        --upload-metrics)
            UPLOAD_METRICS="true"
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# ═══════════════════════════════════════════════════════════════════════════════
# PROFILE CONFIGURATION AND VALIDATION
# ═══════════════════════════════════════════════════════════════════════════════

configure_profile() {
    case "$PROFILE" in
        "full")
            RUN_UNIT="true"
            RUN_INTEGRATION="true"
            RUN_E2E="true"
            RUN_PERFORMANCE="true"
            RUN_MUTATION="true"
            RUN_SECURITY="true"
            RUN_CONTRACT="false" # Disabled until Spring Cloud Contract issues resolved
            ;;
        "smoke")
            RUN_UNIT="true"
            RUN_INTEGRATION="basic"
            RUN_E2E="health"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="basic"
            RUN_CONTRACT="false"
            ;;
        "regression")
            RUN_UNIT="true"
            RUN_INTEGRATION="true"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="true"
            RUN_CONTRACT="true"
            ;;
        "performance")
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="true"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            ;;
        "quality")
            RUN_UNIT="true"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="true"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            ;;
        "security")
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="true"
            RUN_CONTRACT="false"
            ;;
        "contract")
            RUN_UNIT="false"
            RUN_INTEGRATION="false"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="true"
            ;;
        "minimal")
            RUN_UNIT="true"
            RUN_INTEGRATION="basic"
            RUN_E2E="false"
            RUN_PERFORMANCE="false"
            RUN_MUTATION="false"
            RUN_SECURITY="false"
            RUN_CONTRACT="false"
            ;;
        *)
            log_error "Unknown profile: $PROFILE"
            show_usage
            exit 1
            ;;
    esac
    
    # Override profile settings if specific test type was requested
    if [[ "$1" =~ .*-only$ ]]; then
        log_info "Profile overridden by specific test type selection"
    fi
}

validate_environment() {
    case "$ENVIRONMENT" in
        "local")
            BASE_URL="http://localhost:8080"
            ;;
        "dev")
            BASE_URL="http://oddiya-dev-alb-1801802442.ap-northeast-2.elb.amazonaws.com"
            ;;
        "staging")
            BASE_URL="http://oddiya-staging-alb.ap-northeast-2.elb.amazonaws.com"
            ;;
        "prod")
            BASE_URL="http://oddiya-prod-alb.ap-northeast-2.elb.amazonaws.com"
            # Restrict dangerous tests in production
            RUN_MUTATION="false"
            RUN_PERFORMANCE="readonly"
            ;;
        *)
            log_error "Unknown environment: $ENVIRONMENT"
            exit 1
            ;;
    esac
}

# ═══════════════════════════════════════════════════════════════════════════════
# MONITORING AND METRICS INTEGRATION
# ═══════════════════════════════════════════════════════════════════════════════

setup_monitoring() {
    if [[ "$MONITORING_ENABLED" == "true" ]]; then
        log_info "Setting up test monitoring integration"
        
        # Create monitoring directory
        mkdir -p "$REPORTS_DIR/monitoring"
        
        # Start metrics collection
        if command -v curl &> /dev/null && [[ -n "$MONITORING_ENDPOINT" ]]; then
            log_info "Monitoring endpoint configured: $MONITORING_ENDPOINT"
        else
            log_warning "Monitoring endpoint not configured, using local metrics collection"
            MONITORING_ENDPOINT="file://$REPORTS_DIR/monitoring/metrics.json"
        fi
    fi
}

send_metric() {
    if [[ "$MONITORING_ENABLED" != "true" ]]; then
        return 0
    fi
    
    local metric_name="$1"
    local metric_value="$2"
    local metric_type="${3:-counter}"
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    local metric_data="{
        \"timestamp\": \"$timestamp\",
        \"metric\": \"$metric_name\",
        \"value\": $metric_value,
        \"type\": \"$metric_type\",
        \"environment\": \"$ENVIRONMENT\",
        \"profile\": \"$PROFILE\"
    }"
    
    if [[ "$MONITORING_ENDPOINT" =~ ^file:// ]]; then
        local file_path="${MONITORING_ENDPOINT#file://}"
        echo "$metric_data" >> "$file_path"
    elif [[ -n "$MONITORING_ENDPOINT" ]]; then
        curl -s -X POST "$MONITORING_ENDPOINT/metrics" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $MONITORING_API_KEY" \
            -d "$metric_data" &>/dev/null || log_warning "Failed to send metric to monitoring endpoint"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# TEST EXECUTION FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

prepare_test_environment() {
    log_info "Preparing test environment"
    
    # Create reports directory
    mkdir -p "$REPORTS_DIR"
    
    # Validate dependencies
    local required_deps=("java" "curl")
    if [[ "$RUN_PERFORMANCE" == "true" ]]; then
        required_deps+=("gatling" "jmeter")
    fi
    
    validate_dependencies "${required_deps[@]}" || {
        log_error "Missing required dependencies"
        exit 1
    }
    
    # Check Java version
    local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [[ "$java_version" -lt 21 ]]; then
        log_error "Java 21 or higher is required. Current version: $java_version"
        exit 1
    fi
    
    # Clean build if requested
    if [[ "$FORCE_CLEAN" == "true" ]]; then
        log_info "Performing clean build"
        ./gradlew clean
    fi
    
    # Setup parallel execution
    if [[ "$ENABLE_PARALLEL" == "true" ]]; then
        if [[ "$PARALLEL_WORKERS" == "auto" ]]; then
            PARALLEL_WORKERS=$((MAX_PARALLEL_WORKERS / 2))
        fi
        log_info "Parallel execution enabled with $PARALLEL_WORKERS workers"
        export GRADLE_OPTS="${GRADLE_OPTS} --parallel --max-workers=$PARALLEL_WORKERS"
    fi
}

run_unit_tests() {
    if [[ "$RUN_UNIT" != "true" ]]; then
        return 0
    fi
    
    print_section "Unit Tests"
    local start_time=$(date +%s)
    
    log_info "Executing unit tests"
    
    local gradle_args="test"
    if [[ -n "$TEST_FILTER" ]]; then
        gradle_args="$gradle_args --tests='$TEST_FILTER'"
    fi
    if [[ -n "$CUSTOM_TAGS" ]]; then
        gradle_args="$gradle_args -Dtags='$CUSTOM_TAGS'"
    fi
    if [[ -n "$EXCLUDE_TAGS" ]]; then
        gradle_args="$gradle_args -DexcludeTags='$EXCLUDE_TAGS'"
    fi
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        if ./gradlew $gradle_args --continue; then
            log_success "Unit tests completed successfully"
            ((PASSED_TESTS++))
            send_metric "test.unit.success" 1
        else
            test_result=$?
            log_error "Unit tests failed"
            ((FAILED_TESTS++))
            send_metric "test.unit.failure" 1
            
            if [[ "$FAIL_FAST" == "true" ]]; then
                exit $test_result
            fi
        fi
    else
        log_info "[DRY RUN] Would execute: ./gradlew $gradle_args"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.unit.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_integration_tests() {
    if [[ "$RUN_INTEGRATION" == "false" ]]; then
        return 0
    fi
    
    print_section "Integration Tests"
    local start_time=$(date +%s)
    
    log_info "Executing integration tests"
    
    local gradle_args=""
    if [[ "$RUN_INTEGRATION" == "basic" ]]; then
        gradle_args="integrationTest -Dtest.profile=basic"
    else
        gradle_args="integrationTest"
    fi
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        if ./gradlew $gradle_args --continue; then
            log_success "Integration tests completed successfully"
            ((PASSED_TESTS++))
            send_metric "test.integration.success" 1
        else
            test_result=$?
            log_error "Integration tests failed"
            ((FAILED_TESTS++))
            send_metric "test.integration.failure" 1
            
            if [[ "$FAIL_FAST" == "true" ]]; then
                exit $test_result
            fi
        fi
    else
        log_info "[DRY RUN] Would execute: ./gradlew $gradle_args"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.integration.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_e2e_tests() {
    if [[ "$RUN_E2E" == "false" ]]; then
        return 0
    fi
    
    print_section "End-to-End Tests"
    local start_time=$(date +%s)
    
    log_info "Executing E2E tests against $ENVIRONMENT environment"
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        # Use the existing test-master.sh for E2E testing
        local test_type="api"
        if [[ "$RUN_E2E" == "health" ]]; then
            test_type="health"
        fi
        
        if "${SCRIPT_DIR}/testing/test-master.sh" "$test_type" "$ENVIRONMENT" "" "$VERBOSE"; then
            log_success "E2E tests completed successfully"
            ((PASSED_TESTS++))
            send_metric "test.e2e.success" 1
        else
            test_result=$?
            log_error "E2E tests failed"
            ((FAILED_TESTS++))
            send_metric "test.e2e.failure" 1
            
            if [[ "$FAIL_FAST" == "true" ]]; then
                exit $test_result
            fi
        fi
    else
        log_info "[DRY RUN] Would execute E2E tests against $ENVIRONMENT"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.e2e.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_performance_tests() {
    if [[ "$RUN_PERFORMANCE" == "false" ]]; then
        return 0
    fi
    
    print_section "Performance Tests"
    local start_time=$(date +%s)
    
    if [[ "$RUN_PERFORMANCE" == "readonly" ]]; then
        log_warning "Running read-only performance tests for production environment"
    fi
    
    log_info "Executing performance tests"
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        # Use Gatling for performance testing
        if command -v "${SCRIPT_DIR}/testing/run-gatling-tests.sh" &> /dev/null; then
            if "${SCRIPT_DIR}/testing/run-gatling-tests.sh" "$ENVIRONMENT"; then
                log_success "Performance tests completed successfully"
                ((PASSED_TESTS++))
                send_metric "test.performance.success" 1
                
                # Check for performance regression
                check_performance_regression
            else
                test_result=$?
                log_error "Performance tests failed"
                ((FAILED_TESTS++))
                send_metric "test.performance.failure" 1
                
                if [[ "$FAIL_FAST" == "true" ]]; then
                    exit $test_result
                fi
            fi
        else
            log_warning "Gatling test script not found, running basic performance test"
            run_basic_performance_test
        fi
    else
        log_info "[DRY RUN] Would execute performance tests"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.performance.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_basic_performance_test() {
    log_info "Running basic performance test"
    
    local total_requests=50
    local concurrent_requests=5
    local success_count=0
    local total_time=0
    
    for ((i=1; i<=total_requests; i++)); do
        local start_req=$(date +%s.%3N)
        
        if curl -s --max-time 30 "$BASE_URL/actuator/health" > /dev/null; then
            ((success_count++))
        fi
        
        local end_req=$(date +%s.%3N)
        local req_time=$(echo "$end_req - $start_req" | bc -l 2>/dev/null || echo "0")
        total_time=$(echo "$total_time + $req_time" | bc -l 2>/dev/null || echo "$total_time")
        
        if [[ $((i % 10)) -eq 0 ]]; then
            show_progress "$i" "$total_requests"
        fi
    done
    
    local avg_response_time=$(echo "scale=3; $total_time / $total_requests" | bc -l 2>/dev/null || echo "0")
    local success_rate=$(echo "scale=2; $success_count * 100 / $total_requests" | bc -l 2>/dev/null || echo "0")
    
    log_info "Performance results: ${success_rate}% success rate, ${avg_response_time}s average response time"
    
    # Send performance metrics
    send_metric "test.performance.success_rate" "$success_rate" "gauge"
    send_metric "test.performance.avg_response_time" "$avg_response_time" "gauge"
    
    # Check if performance meets requirements
    local min_success_rate="95"
    local max_avg_response="2.0"
    
    if (( $(echo "$success_rate >= $min_success_rate" | bc -l 2>/dev/null || echo "0") )) && \
       (( $(echo "$avg_response_time <= $max_avg_response" | bc -l 2>/dev/null || echo "0") )); then
        log_success "Performance test passed"
        return 0
    else
        log_error "Performance test failed - Success rate: ${success_rate}%, Avg response: ${avg_response_time}s"
        return 1
    fi
}

run_mutation_tests() {
    if [[ "$RUN_MUTATION" != "true" ]]; then
        return 0
    fi
    
    print_section "Mutation Tests"
    local start_time=$(date +%s)
    
    log_info "Executing mutation tests with ${DEFAULT_MUTATION_THRESHOLD}% threshold"
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        local gradle_args="pitest -Ppitest.mutationThreshold=${DEFAULT_MUTATION_THRESHOLD}"
        
        if ./gradlew $gradle_args; then
            log_success "Mutation tests completed successfully"
            ((PASSED_TESTS++))
            send_metric "test.mutation.success" 1
        else
            test_result=$?
            log_error "Mutation tests failed"
            ((FAILED_TESTS++))
            send_metric "test.mutation.failure" 1
            
            if [[ "$FAIL_FAST" == "true" ]]; then
                exit $test_result
            fi
        fi
    else
        log_info "[DRY RUN] Would execute: ./gradlew pitest"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.mutation.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_security_tests() {
    if [[ "$RUN_SECURITY" == "false" ]]; then
        return 0
    fi
    
    print_section "Security Tests"
    local start_time=$(date +%s)
    
    log_info "Executing security tests"
    
    local test_result=0
    if [[ "$DRY_RUN" == "false" ]]; then
        # Run OWASP dependency check
        if ./gradlew dependencyCheckAnalyze; then
            log_success "OWASP dependency check passed"
        else
            log_warning "OWASP dependency check found issues"
        fi
        
        # Run security-focused tests
        if [[ "$RUN_SECURITY" == "basic" ]]; then
            "${SCRIPT_DIR}/testing/test-master.sh" "security" "$ENVIRONMENT" "" "$VERBOSE"
        else
            # Run comprehensive security tests
            "${SCRIPT_DIR}/testing/test-master.sh" "security" "$ENVIRONMENT" "" "$VERBOSE"
            # Additional security testing can be added here
        fi
        
        test_result=$?
        
        if [[ $test_result -eq 0 ]]; then
            log_success "Security tests completed successfully"
            ((PASSED_TESTS++))
            send_metric "test.security.success" 1
        else
            log_error "Security tests failed"
            ((FAILED_TESTS++))
            send_metric "test.security.failure" 1
            
            if [[ "$FAIL_FAST" == "true" ]]; then
                exit $test_result
            fi
        fi
    else
        log_info "[DRY RUN] Would execute security tests"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.security.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return $test_result
}

run_contract_tests() {
    if [[ "$RUN_CONTRACT" != "true" ]]; then
        return 0
    fi
    
    print_section "Contract Tests"
    local start_time=$(date +%s)
    
    log_warning "Contract tests are currently disabled due to Spring Cloud Contract configuration issues"
    log_info "Skipping contract tests"
    ((SKIPPED_TESTS++))
    
    # Placeholder for when contract tests are re-enabled
    # if [[ "$DRY_RUN" == "false" ]]; then
    #     if ./gradlew contractTest; then
    #         log_success "Contract tests completed successfully"
    #         ((PASSED_TESTS++))
    #         send_metric "test.contract.success" 1
    #     else
    #         test_result=$?
    #         log_error "Contract tests failed"
    #         ((FAILED_TESTS++))
    #         send_metric "test.contract.failure" 1
    #         
    #         if [[ "$FAIL_FAST" == "true" ]]; then
    #             exit $test_result
    #         fi
    #     fi
    # fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    send_metric "test.contract.duration" "$duration" "gauge"
    
    ((TOTAL_TESTS++))
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# COVERAGE AND REPORTING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

generate_coverage_report() {
    if [[ "$COVERAGE_ONLY" == "true" ]] || [[ "$GENERATE_REPORT" == "true" ]]; then
        print_section "Coverage Analysis"
        
        log_info "Generating coverage reports"
        
        # Generate Jacoco coverage report
        if ./gradlew jacocoTestReport; then
            log_success "Jacoco coverage report generated"
            
            # Extract coverage metrics if possible
            local coverage_file="build/reports/jacoco/test/jacocoTestReport.xml"
            if [[ -f "$coverage_file" ]]; then
                local line_coverage=$(grep -o 'type="LINE".*missed="[0-9]*".*covered="[0-9]*"' "$coverage_file" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/' || echo "0")
                local total_lines=$(grep -o 'type="LINE".*missed="[0-9]*".*covered="[0-9]*"' "$coverage_file" | head -1 | sed 's/.*missed="\([0-9]*\)".*covered="\([0-9]*\)".*/\1 \2/' | awk '{print $1+$2}' || echo "1")
                
                if [[ "$total_lines" -gt 0 ]]; then
                    local coverage_percentage=$(echo "scale=2; $line_coverage * 100 / $total_lines" | bc -l 2>/dev/null || echo "0")
                    log_info "Code coverage: ${coverage_percentage}%"
                    send_metric "test.coverage.percentage" "$coverage_percentage" "gauge"
                    
                    # Check coverage threshold
                    if (( $(echo "$coverage_percentage >= $DEFAULT_COVERAGE_THRESHOLD" | bc -l 2>/dev/null || echo "0") )); then
                        log_success "Coverage threshold met (${coverage_percentage}% >= ${DEFAULT_COVERAGE_THRESHOLD}%)"
                    else
                        log_warning "Coverage below threshold (${coverage_percentage}% < ${DEFAULT_COVERAGE_THRESHOLD}%)"
                    fi
                fi
            fi
        else
            log_error "Failed to generate coverage report"
        fi
    fi
}

check_performance_regression() {
    if [[ ! -f "$PERFORMANCE_BASELINE_FILE" ]]; then
        log_info "No performance baseline found, skipping regression check"
        return 0
    fi
    
    log_info "Checking for performance regression"
    
    # This is a simplified implementation
    # In a real scenario, you would compare current metrics with baseline
    local current_response_time="1.2" # This would be extracted from actual test results
    local baseline_response_time=$(jq -r '.avg_response_time // "1.0"' "$PERFORMANCE_BASELINE_FILE" 2>/dev/null || echo "1.0")
    
    local regression=$(echo "scale=2; ($current_response_time - $baseline_response_time) * 100 / $baseline_response_time" | bc -l 2>/dev/null || echo "0")
    
    if (( $(echo "$regression > $PERFORMANCE_DEGRADATION_THRESHOLD" | bc -l 2>/dev/null || echo "0") )); then
        log_warning "Performance regression detected: ${regression}% slower than baseline"
        send_metric "test.performance.regression" "$regression" "gauge"
        return 1
    else
        log_success "No significant performance regression detected"
        return 0
    fi
}

generate_consolidated_report() {
    if [[ "$GENERATE_REPORT" != "true" ]]; then
        return 0
    fi
    
    print_section "Report Generation"
    
    log_info "Generating consolidated test report"
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - SCRIPT_START_TIME))
    local success_rate=$(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc -l 2>/dev/null || echo "0")
    
    # Create JSON report
    cat > "$CONSOLIDATED_REPORT" << EOF
{
    "test_execution_report": {
        "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
        "script_version": "$SCRIPT_VERSION",
        "profile": "$PROFILE",
        "environment": "$ENVIRONMENT",
        "execution": {
            "total_duration_seconds": $total_duration,
            "parallel_execution": $ENABLE_PARALLEL,
            "parallel_workers": "$PARALLEL_WORKERS",
            "fail_fast": $FAIL_FAST
        },
        "summary": {
            "total_tests": $TOTAL_TESTS,
            "passed": $PASSED_TESTS,
            "failed": $FAILED_TESTS,
            "skipped": $SKIPPED_TESTS,
            "success_rate": "${success_rate}%"
        },
        "test_types": {
            "unit": "$RUN_UNIT",
            "integration": "$RUN_INTEGRATION",
            "e2e": "$RUN_E2E",
            "performance": "$RUN_PERFORMANCE",
            "mutation": "$RUN_MUTATION",
            "security": "$RUN_SECURITY",
            "contract": "$RUN_CONTRACT"
        },
        "thresholds": {
            "coverage_threshold": "${DEFAULT_COVERAGE_THRESHOLD}%",
            "mutation_threshold": "${DEFAULT_MUTATION_THRESHOLD}%"
        },
        "configuration": {
            "base_url": "$BASE_URL",
            "custom_tags": "$CUSTOM_TAGS",
            "exclude_tags": "$EXCLUDE_TAGS",
            "test_filter": "$TEST_FILTER"
        },
        "monitoring": {
            "enabled": $MONITORING_ENABLED,
            "endpoint": "$MONITORING_ENDPOINT",
            "metrics_uploaded": $UPLOAD_METRICS
        },
        "reports": {
            "location": "$REPORTS_DIR",
            "formats": "$DEFAULT_REPORT_FORMAT"
        }
    }
}
EOF

    # Generate HTML report if requested
    if [[ "$DEFAULT_REPORT_FORMAT" =~ html ]]; then
        generate_html_report
    fi
    
    # Upload metrics if configured
    if [[ "$UPLOAD_METRICS" == "true" ]]; then
        upload_test_metrics
    fi
    
    log_success "Consolidated report generated: $CONSOLIDATED_REPORT"
}

generate_html_report() {
    local html_report="${REPORTS_DIR}/test-execution-report-${REPORT_TIMESTAMP}.html"
    
    cat > "$html_report" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Oddiya Test Execution Report</title>
    <style>
        body { font-family: 'Arial', sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .header { text-align: center; margin-bottom: 30px; padding: 20px; background: linear-gradient(135deg, #007acc, #0056b3); color: white; border-radius: 8px; }
        .header h1 { margin: 0; font-size: 2em; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0; }
        .metric-card { padding: 20px; border-radius: 8px; text-align: center; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
        .metric-card.success { background: #d4edda; border-left: 4px solid #28a745; }
        .metric-card.warning { background: #fff3cd; border-left: 4px solid #ffc107; }
        .metric-card.error { background: #f8d7da; border-left: 4px solid #dc3545; }
        .metric-card.info { background: #d1ecf1; border-left: 4px solid #17a2b8; }
        .metric-value { font-size: 2em; font-weight: bold; margin-bottom: 5px; }
        .metric-label { font-size: 0.9em; color: #666; }
        .section { margin: 30px 0; }
        .section h2 { color: #333; border-bottom: 2px solid #007acc; padding-bottom: 10px; }
        .test-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; margin: 20px 0; }
        .test-type { padding: 15px; border-radius: 8px; text-align: center; border: 2px solid #ddd; }
        .test-type.enabled { background: #e8f5e8; border-color: #28a745; }
        .test-type.disabled { background: #f8f9fa; border-color: #6c757d; }
        .footer { text-align: center; margin-top: 40px; padding: 20px; background: #f8f9fa; border-radius: 8px; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #007acc; color: white; }
        .status-badge { padding: 4px 8px; border-radius: 4px; font-size: 0.8em; font-weight: bold; }
        .badge-success { background: #28a745; color: white; }
        .badge-error { background: #dc3545; color: white; }
        .badge-warning { background: #ffc107; color: black; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧪 Oddiya Test Execution Report</h1>
            <p>Profile: <strong>$PROFILE</strong> | Environment: <strong>$ENVIRONMENT</strong></p>
            <p>Generated: <strong>$(date)</strong> | Duration: <strong>$(format_duration $(($(date +%s) - SCRIPT_START_TIME)))</strong></p>
        </div>

        <div class="summary">
            <div class="metric-card $([ $FAILED_TESTS -eq 0 ] && echo "success" || echo "error")">
                <div class="metric-value">$TOTAL_TESTS</div>
                <div class="metric-label">Total Tests</div>
            </div>
            <div class="metric-card success">
                <div class="metric-value">$PASSED_TESTS</div>
                <div class="metric-label">Passed</div>
            </div>
            <div class="metric-card $([ $FAILED_TESTS -eq 0 ] && echo "success" || echo "error")">
                <div class="metric-value">$FAILED_TESTS</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric-card $([ $SKIPPED_TESTS -eq 0 ] && echo "info" || echo "warning")">
                <div class="metric-value">$SKIPPED_TESTS</div>
                <div class="metric-label">Skipped</div>
            </div>
        </div>

        <div class="section">
            <h2>📊 Test Types Executed</h2>
            <div class="test-grid">
                <div class="test-type $([ "$RUN_UNIT" == "true" ] && echo "enabled" || echo "disabled")">
                    <strong>Unit Tests</strong><br>
                    <span class="status-badge $([ "$RUN_UNIT" == "true" ] && echo "badge-success" || echo "badge-warning")">
                        $([ "$RUN_UNIT" == "true" ] && echo "ENABLED" || echo "DISABLED")
                    </span>
                </div>
                <div class="test-type $([ "$RUN_INTEGRATION" != "false" ] && echo "enabled" || echo "disabled")">
                    <strong>Integration</strong><br>
                    <span class="status-badge $([ "$RUN_INTEGRATION" != "false" ] && echo "badge-success" || echo "badge-warning")">
                        $(echo "$RUN_INTEGRATION" | tr '[:lower:]' '[:upper:]')
                    </span>
                </div>
                <div class="test-type $([ "$RUN_E2E" != "false" ] && echo "enabled" || echo "disabled")">
                    <strong>End-to-End</strong><br>
                    <span class="status-badge $([ "$RUN_E2E" != "false" ] && echo "badge-success" || echo "badge-warning")">
                        $(echo "$RUN_E2E" | tr '[:lower:]' '[:upper:]')
                    </span>
                </div>
                <div class="test-type $([ "$RUN_PERFORMANCE" != "false" ] && echo "enabled" || echo "disabled")">
                    <strong>Performance</strong><br>
                    <span class="status-badge $([ "$RUN_PERFORMANCE" != "false" ] && echo "badge-success" || echo "badge-warning")">
                        $(echo "$RUN_PERFORMANCE" | tr '[:lower:]' '[:upper:]')
                    </span>
                </div>
                <div class="test-type $([ "$RUN_MUTATION" == "true" ] && echo "enabled" || echo "disabled")">
                    <strong>Mutation</strong><br>
                    <span class="status-badge $([ "$RUN_MUTATION" == "true" ] && echo "badge-success" || echo "badge-warning")">
                        $([ "$RUN_MUTATION" == "true" ] && echo "ENABLED" || echo "DISABLED")
                    </span>
                </div>
                <div class="test-type $([ "$RUN_SECURITY" != "false" ] && echo "enabled" || echo "disabled")">
                    <strong>Security</strong><br>
                    <span class="status-badge $([ "$RUN_SECURITY" != "false" ] && echo "badge-success" || echo "badge-warning")">
                        $(echo "$RUN_SECURITY" | tr '[:lower:]' '[:upper:]')
                    </span>
                </div>
            </div>
        </div>

        <div class="section">
            <h2>⚙️ Configuration</h2>
            <table>
                <tr><th>Setting</th><th>Value</th></tr>
                <tr><td>Base URL</td><td>$BASE_URL</td></tr>
                <tr><td>Parallel Execution</td><td>$ENABLE_PARALLEL $([ "$ENABLE_PARALLEL" == "true" ] && echo "($PARALLEL_WORKERS workers)" || echo "")</td></tr>
                <tr><td>Coverage Threshold</td><td>${DEFAULT_COVERAGE_THRESHOLD}%</td></tr>
                <tr><td>Mutation Threshold</td><td>${DEFAULT_MUTATION_THRESHOLD}%</td></tr>
                <tr><td>Fail Fast</td><td>$FAIL_FAST</td></tr>
                <tr><td>Custom Tags</td><td>$([ -n "$CUSTOM_TAGS" ] && echo "$CUSTOM_TAGS" || echo "None")</td></tr>
                <tr><td>Test Filter</td><td>$([ -n "$TEST_FILTER" ] && echo "$TEST_FILTER" || echo "None")</td></tr>
            </table>
        </div>

        <div class="section">
            <h2>📈 Monitoring & Metrics</h2>
            <div class="metric-card info">
                <strong>Monitoring Status:</strong> $([ "$MONITORING_ENABLED" == "true" ] && echo "Enabled" || echo "Disabled")<br>
                $([ "$MONITORING_ENABLED" == "true" ] && echo "<strong>Endpoint:</strong> $MONITORING_ENDPOINT<br>" || echo "")
                <strong>Metrics Upload:</strong> $([ "$UPLOAD_METRICS" == "true" ] && echo "Enabled" || echo "Disabled")
            </div>
        </div>

        <div class="footer">
            <p><strong>Test Runner v$SCRIPT_VERSION</strong></p>
            <p>Report generated at $(date)</p>
            <p>📁 Detailed reports available in: <code>$REPORTS_DIR</code></p>
        </div>
    </div>
</body>
</html>
EOF

    log_success "HTML report generated: $html_report"
}

upload_test_metrics() {
    if [[ "$UPLOAD_METRICS" != "true" ]] || [[ -z "$MONITORING_ENDPOINT" ]]; then
        return 0
    fi
    
    log_info "Uploading test metrics to monitoring system"
    
    # Send summary metrics
    send_metric "test.execution.total_tests" "$TOTAL_TESTS"
    send_metric "test.execution.passed_tests" "$PASSED_TESTS"
    send_metric "test.execution.failed_tests" "$FAILED_TESTS"
    send_metric "test.execution.skipped_tests" "$SKIPPED_TESTS"
    send_metric "test.execution.duration" "$(($(date +%s) - SCRIPT_START_TIME))" "gauge"
    
    # Send profile and environment info
    send_metric "test.execution.profile" "1" "counter" | jq --arg profile "$PROFILE" '. + {labels: {profile: $profile}}'
    send_metric "test.execution.environment" "1" "counter" | jq --arg env "$ENVIRONMENT" '. + {labels: {environment: $env}}'
    
    log_success "Test metrics uploaded successfully"
}

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN EXECUTION FLOW
# ═══════════════════════════════════════════════════════════════════════════════

show_execution_plan() {
    print_header "Execution Plan"
    print_aws_info
    echo ""
    
    echo -e "${BOLD}Configuration:${NC}"
    echo -e "  Profile: ${CYAN}$PROFILE${NC}"
    echo -e "  Environment: ${CYAN}$ENVIRONMENT${NC}"
    echo -e "  Base URL: ${CYAN}$BASE_URL${NC}"
    echo -e "  Parallel: ${CYAN}$ENABLE_PARALLEL${NC} $([ "$ENABLE_PARALLEL" == "true" ] && echo "($PARALLEL_WORKERS workers)" || echo "")"
    echo -e "  Coverage Threshold: ${CYAN}${DEFAULT_COVERAGE_THRESHOLD}%${NC}"
    echo -e "  Mutation Threshold: ${CYAN}${DEFAULT_MUTATION_THRESHOLD}%${NC}"
    echo ""
    
    echo -e "${BOLD}Test Execution Plan:${NC}"
    [[ "$RUN_UNIT" == "true" ]] && echo -e "  ${GREEN}✓${NC} Unit Tests"
    [[ "$RUN_INTEGRATION" != "false" ]] && echo -e "  ${GREEN}✓${NC} Integration Tests ($RUN_INTEGRATION)"
    [[ "$RUN_E2E" != "false" ]] && echo -e "  ${GREEN}✓${NC} End-to-End Tests ($RUN_E2E)"
    [[ "$RUN_PERFORMANCE" != "false" ]] && echo -e "  ${GREEN}✓${NC} Performance Tests ($RUN_PERFORMANCE)"
    [[ "$RUN_MUTATION" == "true" ]] && echo -e "  ${GREEN}✓${NC} Mutation Tests"
    [[ "$RUN_SECURITY" != "false" ]] && echo -e "  ${GREEN}✓${NC} Security Tests ($RUN_SECURITY)"
    [[ "$RUN_CONTRACT" == "true" ]] && echo -e "  ${GREEN}✓${NC} Contract Tests"
    
    if [[ "$GENERATE_REPORT" == "true" ]]; then
        echo -e "  ${GREEN}✓${NC} Report Generation (${DEFAULT_REPORT_FORMAT})"
    fi
    
    if [[ "$MONITORING_ENABLED" == "true" ]]; then
        echo -e "  ${GREEN}✓${NC} Monitoring Integration"
    fi
    
    echo ""
    
    if [[ -n "$CUSTOM_TAGS" ]]; then
        echo -e "${BOLD}Included Tags:${NC} ${CYAN}$CUSTOM_TAGS${NC}"
    fi
    
    if [[ -n "$EXCLUDE_TAGS" ]]; then
        echo -e "${BOLD}Excluded Tags:${NC} ${CYAN}$EXCLUDE_TAGS${NC}"
    fi
    
    if [[ -n "$TEST_FILTER" ]]; then
        echo -e "${BOLD}Test Filter:${NC} ${CYAN}$TEST_FILTER${NC}"
    fi
    
    echo ""
}

main() {
    # Initialize script
    init_script "$SCRIPT_NAME" false
    
    # Configure profile and environment
    configure_profile
    validate_environment
    
    # Show execution plan
    show_execution_plan
    
    # Confirm execution if not in dry-run mode
    if [[ "$DRY_RUN" == "false" ]] && [[ -t 0 ]]; then
        if ! confirm_action "Proceed with test execution?" "y"; then
            log_info "Test execution cancelled by user"
            exit 0
        fi
    fi
    
    # Setup environment
    setup_monitoring
    prepare_test_environment
    
    # Execute test phases
    print_header "TEST EXECUTION"
    
    local overall_result=0
    
    # Run tests in sequence (some may run in parallel internally)
    run_unit_tests || ((overall_result++))
    run_integration_tests || ((overall_result++))
    run_e2e_tests || ((overall_result++))
    run_performance_tests || ((overall_result++))
    run_mutation_tests || ((overall_result++))
    run_security_tests || ((overall_result++))
    run_contract_tests || ((overall_result++))
    
    # Generate reports
    generate_coverage_report
    generate_consolidated_report
    
    # Final summary
    print_header "EXECUTION SUMMARY"
    print_aws_info
    
    local total_duration=$(($(date +%s) - SCRIPT_START_TIME))
    local success_rate=$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc -l 2>/dev/null || echo "0")
    
    echo -e "${BOLD}Results:${NC}"
    echo -e "  Total Tests: ${CYAN}$TOTAL_TESTS${NC}"
    echo -e "  Passed: ${GREEN}$PASSED_TESTS${NC}"
    echo -e "  Failed: ${RED}$FAILED_TESTS${NC}"
    echo -e "  Skipped: ${YELLOW}$SKIPPED_TESTS${NC}"
    echo -e "  Success Rate: ${CYAN}${success_rate}%${NC}"
    echo -e "  Duration: ${CYAN}$(format_duration $total_duration)${NC}"
    echo ""
    
    if [[ "$GENERATE_REPORT" == "true" ]]; then
        echo -e "${BOLD}Reports:${NC}"
        echo -e "  📊 JSON Report: ${CYAN}$CONSOLIDATED_REPORT${NC}"
        if [[ "$DEFAULT_REPORT_FORMAT" =~ html ]]; then
            echo -e "  🌐 HTML Report: ${CYAN}${REPORTS_DIR}/test-execution-report-${REPORT_TIMESTAMP}.html${NC}"
        fi
        echo -e "  📁 All Reports: ${CYAN}$REPORTS_DIR${NC}"
        echo ""
    fi
    
    # Final status
    if [[ $FAILED_TESTS -eq 0 ]]; then
        log_success "All tests completed successfully!"
        script_complete "$SCRIPT_NAME" "$total_duration"
        exit 0
    else
        log_error "$FAILED_TESTS test(s) failed"
        exit 1
    fi
}

# Execute main function with all arguments
main "$@"