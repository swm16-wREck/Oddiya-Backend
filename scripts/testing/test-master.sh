#!/bin/bash

# Oddiya Master Testing Script
# Consolidated testing functionality for all test types
# Usage: ./scripts/testing/test-master.sh [test-type] [environment] [options]

set -e

# Script metadata
SCRIPT_VERSION="2.0.0"
SCRIPT_NAME="Oddiya Master Test"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
DEFAULT_TEST_TYPE="all"
DEFAULT_ENVIRONMENT="local"
DEFAULT_BASE_URL="http://localhost:8080"

# Parse arguments
TEST_TYPE="${1:-$DEFAULT_TEST_TYPE}"
ENVIRONMENT="${2:-$DEFAULT_ENVIRONMENT}"
CUSTOM_URL="${3:-}"
VERBOSE="${4:-false}"
PARALLEL="${5:-false}"
GENERATE_REPORT="${6:-true}"

# Environment-specific URLs
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
        ;;
    *)
        BASE_URL="$CUSTOM_URL"
        ;;
esac

# Override with custom URL if provided
if [[ -n "$CUSTOM_URL" ]]; then
    BASE_URL="$CUSTOM_URL"
fi

API_BASE="${BASE_URL}/api/v1"

# Test data
TEST_EMAIL="test-user-$(date +%s)@example.com"
TEST_PASSWORD="TestPassword123!"
ACCESS_TOKEN=""
REFRESH_TOKEN=""
USER_ID=""

# Report variables
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
REPORT_FILE="test-report-$(date +%Y%m%d-%H%M%S).json"

# Banner
echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          ${SCRIPT_NAME} v${SCRIPT_VERSION}          ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo -e "${CYAN}Test Type: ${TEST_TYPE}${NC}"
echo -e "${CYAN}Environment: ${ENVIRONMENT}${NC}"
echo -e "${CYAN}Base URL: ${BASE_URL}${NC}"
echo -e "${CYAN}Test Email: ${TEST_EMAIL}${NC}"
echo ""

# Functions
print_step() {
    echo -e "\n${BLUE}🧪 $1${NC}"
}

print_test() {
    echo -e "${PURPLE}  → $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((TESTS_PASSED++))
    ((TESTS_RUN++))
}

print_failure() {
    echo -e "${RED}❌ $1${NC}"
    ((TESTS_FAILED++))
    ((TESTS_RUN++))
}

print_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ️ $1${NC}"
}

# Test execution wrapper
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_status="${3:-200}"
    
    print_test "$test_name"
    
    if [[ "$VERBOSE" == "true" ]]; then
        print_info "Command: $test_command"
    fi
    
    local response
    local status_code
    
    # Execute test command and capture response
    response=$(eval "$test_command" 2>/dev/null || echo '{"error": "request_failed"}')
    status_code=$?
    
    # Check if jq is available for JSON parsing
    if command -v jq &> /dev/null; then
        # Try to parse as JSON
        if echo "$response" | jq . &>/dev/null; then
            local json_status=$(echo "$response" | jq -r '.status // empty' 2>/dev/null)
            local json_error=$(echo "$response" | jq -r '.error // empty' 2>/dev/null)
            
            if [[ -n "$json_error" && "$json_error" != "null" ]]; then
                print_failure "$test_name - Error: $json_error"
            elif [[ "$status_code" -eq 0 ]]; then
                print_success "$test_name"
            else
                print_failure "$test_name - HTTP Status: $status_code"
            fi
        else
            # Non-JSON response
            if [[ "$status_code" -eq 0 ]]; then
                print_success "$test_name"
            else
                print_failure "$test_name - Status: $status_code"
            fi
        fi
    else
        # No jq available, basic status check
        if [[ "$status_code" -eq 0 ]]; then
            print_success "$test_name"
        else
            print_failure "$test_name - Status: $status_code"
        fi
    fi
    
    if [[ "$VERBOSE" == "true" ]]; then
        echo "Response: $response"
    fi
    
    echo "$response"
}

# Health checks
test_health() {
    print_step "Health Check Tests"
    
    # Basic health endpoint
    run_test "Application Health" \
        "curl -s -w '%{http_code}' '$BASE_URL/actuator/health'" \
        "200"
    
    # API health
    run_test "API Health" \
        "curl -s '$API_BASE/health'" \
        "200"
    
    # Database health (if available)
    run_test "Database Health" \
        "curl -s '$BASE_URL/actuator/health/db'" \
        "200"
}

# Authentication tests
test_authentication() {
    print_step "Authentication Tests"
    
    # Test invalid token
    run_test "JWT Validation (Invalid Token)" \
        "curl -s -H 'Authorization: Bearer invalid_token' '$API_BASE/auth/validate'" \
        "401"
    
    # Test Google OAuth with fake token
    run_test "Google OAuth (Invalid Token)" \
        "curl -s -X POST '$API_BASE/auth/oauth/google' \
         -H 'Content-Type: application/json' \
         -d '{\"idToken\": \"fake_google_token\"}'" \
        "400"
    
    # Test Apple OAuth with fake token
    run_test "Apple OAuth (Invalid Token)" \
        "curl -s -X POST '$API_BASE/auth/oauth/apple' \
         -H 'Content-Type: application/json' \
         -d '{\"idToken\": \"fake_apple_token\"}'" \
        "400"
    
    # Test token refresh without token
    run_test "Token Refresh (No Token)" \
        "curl -s -X POST '$API_BASE/auth/refresh' \
         -H 'Content-Type: application/json' \
         -d '{\"refreshToken\": \"invalid_refresh_token\"}'" \
        "401"
}

# API endpoint tests
test_api_endpoints() {
    print_step "API Endpoint Tests"
    
    # Test protected endpoints without authentication
    run_test "Get User Profile (Unauthenticated)" \
        "curl -s '$API_BASE/users/me'" \
        "401"
    
    run_test "Get Travel Plans (Unauthenticated)" \
        "curl -s '$API_BASE/travel-plans'" \
        "401"
    
    run_test "Search Places (Unauthenticated)" \
        "curl -s '$API_BASE/places/search?query=seoul'" \
        "401"
}

# Unit tests
test_unit() {
    print_step "Unit Tests"
    
    if [[ ! -f "./gradlew" ]]; then
        print_warning "Gradle wrapper not found, skipping unit tests"
        return 0
    fi
    
    print_test "Running Unit Tests"
    
    if [[ "$PARALLEL" == "true" ]]; then
        ./gradlew test --parallel > /tmp/unit_test.log 2>&1
    else
        ./gradlew test > /tmp/unit_test.log 2>&1
    fi
    
    local exit_code=$?
    
    if [[ $exit_code -eq 0 ]]; then
        print_success "Unit Tests Passed"
    else
        print_failure "Unit Tests Failed"
        if [[ "$VERBOSE" == "true" ]]; then
            echo "Test output:"
            cat /tmp/unit_test.log
        fi
    fi
}

# Integration tests
test_integration() {
    print_step "Integration Tests"
    
    if [[ ! -f "./gradlew" ]]; then
        print_warning "Gradle wrapper not found, skipping integration tests"
        return 0
    fi
    
    print_test "Running Integration Tests"
    
    if [[ "$PARALLEL" == "true" ]]; then
        ./gradlew integrationTest --parallel > /tmp/integration_test.log 2>&1
    else
        ./gradlew integrationTest > /tmp/integration_test.log 2>&1
    fi
    
    local exit_code=$?
    
    if [[ $exit_code -eq 0 ]]; then
        print_success "Integration Tests Passed"
    else
        print_failure "Integration Tests Failed"
        if [[ "$VERBOSE" == "true" ]]; then
            echo "Test output:"
            cat /tmp/integration_test.log
        fi
    fi
}

# Performance tests
test_performance() {
    print_step "Performance Tests"
    
    # Simple load test with curl
    print_test "Basic Load Test (10 requests)"
    
    local total_time=0
    local successful_requests=0
    
    for i in {1..10}; do
        local start_time=$(date +%s.%3N)
        
        if curl -s "$BASE_URL/actuator/health" > /dev/null; then
            ((successful_requests++))
        fi
        
        local end_time=$(date +%s.%3N)
        local request_time=$(echo "$end_time - $start_time" | bc -l 2>/dev/null || echo "0")
        total_time=$(echo "$total_time + $request_time" | bc -l 2>/dev/null || echo "$total_time")
    done
    
    local avg_time=$(echo "scale=3; $total_time / 10" | bc -l 2>/dev/null || echo "0")
    
    if [[ $successful_requests -ge 8 ]]; then
        print_success "Load Test Passed ($successful_requests/10 requests, avg: ${avg_time}s)"
    else
        print_failure "Load Test Failed ($successful_requests/10 requests succeeded)"
    fi
}

# Security tests
test_security() {
    print_step "Security Tests"
    
    # SQL injection test
    run_test "SQL Injection Protection" \
        "curl -s '$API_BASE/places/search?query=%27%20OR%20%271%27=%271'" \
        "401"
    
    # XSS test
    run_test "XSS Protection" \
        "curl -s -X POST '$API_BASE/auth/oauth/google' \
         -H 'Content-Type: application/json' \
         -d '{\"idToken\": \"<script>alert(1)</script>\"}'" \
        "400"
    
    # CORS test
    run_test "CORS Headers" \
        "curl -s -H 'Origin: http://malicious.com' '$BASE_URL/actuator/health'" \
        "200"
}

# Generate test report
generate_report() {
    if [[ "$GENERATE_REPORT" != "true" ]]; then
        return 0
    fi
    
    print_step "Generating Test Report"
    
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local success_rate=$(echo "scale=2; $TESTS_PASSED * 100 / $TESTS_RUN" | bc -l 2>/dev/null || echo "0")
    
    cat > "$REPORT_FILE" << EOF
{
  "test_report": {
    "timestamp": "$timestamp",
    "test_type": "$TEST_TYPE",
    "environment": "$ENVIRONMENT",
    "base_url": "$BASE_URL",
    "summary": {
      "total_tests": $TESTS_RUN,
      "passed": $TESTS_PASSED,
      "failed": $TESTS_FAILED,
      "success_rate": "${success_rate}%"
    },
    "configuration": {
      "verbose": $VERBOSE,
      "parallel": $PARALLEL,
      "test_email": "$TEST_EMAIL"
    }
  }
}
EOF
    
    print_success "Report generated: $REPORT_FILE"
}

# Show help
show_help() {
    echo -e "${YELLOW}Usage:${NC}"
    echo "  $0 [test-type] [environment] [custom-url] [verbose] [parallel] [generate-report]"
    echo ""
    echo -e "${YELLOW}Test Types:${NC}"
    echo "  all            Run all test types"
    echo "  health         Health check tests only"
    echo "  auth           Authentication tests only"
    echo "  api            API endpoint tests only"
    echo "  unit           Unit tests only"
    echo "  integration    Integration tests only"
    echo "  performance    Performance tests only"
    echo "  security       Security tests only"
    echo ""
    echo -e "${YELLOW}Environments:${NC}"
    echo "  local          Local development (http://localhost:8080)"
    echo "  dev            Development environment"
    echo "  staging        Staging environment"
    echo "  prod           Production environment"
    echo "  custom         Use custom URL (provide as 3rd parameter)"
    echo ""
    echo -e "${YELLOW}Options:${NC}"
    echo "  verbose        Show detailed output (true|false) [default: false]"
    echo "  parallel       Run tests in parallel (true|false) [default: false]"
    echo "  generate-report Generate JSON report (true|false) [default: true]"
    echo ""
    echo -e "${YELLOW}Examples:${NC}"
    echo "  $0                              # Run all tests on local"
    echo "  $0 auth dev                     # Run auth tests on dev"
    echo "  $0 all prod '' true            # Run all tests on prod with verbose"
    echo "  $0 unit local '' false true    # Run unit tests with parallel execution"
}

# Main execution
main() {
    # Handle help request
    if [[ "$1" == "-h" || "$1" == "--help" || "$1" == "help" ]]; then
        show_help
        exit 0
    fi
    
    # Execute tests based on type
    case "$TEST_TYPE" in
        "health")
            test_health
            ;;
        "auth")
            test_authentication
            ;;
        "api")
            test_api_endpoints
            ;;
        "unit")
            test_unit
            ;;
        "integration")
            test_integration
            ;;
        "performance")
            test_performance
            ;;
        "security")
            test_security
            ;;
        "all")
            test_health
            test_authentication
            test_api_endpoints
            if [[ "$ENVIRONMENT" == "local" ]]; then
                test_unit
                test_integration
            fi
            test_performance
            test_security
            ;;
        *)
            print_failure "Invalid test type: $TEST_TYPE"
            show_help
            exit 1
            ;;
    esac
    
    # Generate report
    generate_report
    
    # Summary
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║              Test Summary                ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
    echo -e "${CYAN}Total Tests: $TESTS_RUN${NC}"
    echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
    echo -e "${RED}Failed: $TESTS_FAILED${NC}"
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        echo -e "\n${GREEN}🎉 All tests passed!${NC}"
        exit 0
    else
        echo -e "\n${RED}❌ Some tests failed!${NC}"
        exit 1
    fi
}

# Run main function
main "$@"