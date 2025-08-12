#!/bin/bash

# AWS Integration Tests Runner Script
# This script runs the AWS service integration tests with LocalStack

set -e

echo "🚀 Starting AWS Integration Tests with LocalStack"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
LOCALSTACK_TIMEOUT=60
TEST_TIMEOUT=300
CLEANUP_ON_EXIT=true

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to cleanup resources
cleanup() {
    if [ "$CLEANUP_ON_EXIT" = true ]; then
        print_status "Cleaning up resources..."
        
        # Stop and remove Docker containers
        docker-compose -f docker-compose-localstack.yml down -v 2>/dev/null || true
        
        # Remove temporary files
        rm -rf tmp/localstack 2>/dev/null || true
        
        print_success "Cleanup completed"
    fi
}

# Set trap for cleanup on exit
trap cleanup EXIT

# Function to wait for service to be ready
wait_for_service() {
    local service=$1
    local url=$2
    local timeout=$3
    local counter=0
    
    print_status "Waiting for $service to be ready..."
    
    while [ $counter -lt $timeout ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            print_success "$service is ready!"
            return 0
        fi
        
        sleep 2
        counter=$((counter + 2))
        
        if [ $((counter % 10)) -eq 0 ]; then
            print_status "Still waiting for $service... (${counter}s elapsed)"
        fi
    done
    
    print_error "$service failed to start within ${timeout}s"
    return 1
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    
    # Check if Java version is 21+
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        print_error "Java 21 or higher is required. Current version: $JAVA_VERSION"
        exit 1
    fi
    
    print_success "All prerequisites are met"
}

# Function to start LocalStack
start_localstack() {
    print_status "Starting LocalStack services..."
    
    # Create necessary directories
    mkdir -p tmp/localstack
    mkdir -p localstack-init
    
    # Make initialization script executable
    chmod +x localstack-init/01-setup-aws-resources.sh
    
    # Start LocalStack with Docker Compose
    docker-compose -f docker-compose-localstack.yml up -d localstack
    
    # Wait for LocalStack to be ready
    if wait_for_service "LocalStack" "http://localhost:4566/health" $LOCALSTACK_TIMEOUT; then
        print_success "LocalStack is ready!"
        
        # Give a bit more time for initialization
        sleep 5
        
        # Check LocalStack services
        print_status "Checking LocalStack services..."
        curl -s http://localhost:4566/health | jq '.' || print_warning "Could not parse LocalStack health status"
        
        return 0
    else
        print_error "LocalStack failed to start"
        return 1
    fi
}

# Function to run specific test suite
run_test_suite() {
    local test_name=$1
    local test_profile=$2
    local test_class=$3
    
    print_status "Running $test_name tests..."
    
    # Set environment variables for the test
    export SPRING_PROFILES_ACTIVE=$test_profile
    export AWS_ACCESS_KEY_ID=test
    export AWS_SECRET_ACCESS_KEY=test
    export AWS_DEFAULT_REGION=ap-northeast-2
    
    # Run the test
    if timeout $TEST_TIMEOUT ./gradlew test --tests "$test_class" \
        -Dspring.profiles.active=$test_profile \
        -Dtest.localstack.endpoint=http://localhost:4566 \
        --info --stacktrace; then
        print_success "$test_name tests passed!"
        return 0
    else
        print_error "$test_name tests failed!"
        return 1
    fi
}

# Function to run all AWS integration tests
run_all_tests() {
    local failed_tests=()
    
    print_status "Running all AWS integration tests..."
    
    # Run DynamoDB tests
    if ! run_test_suite "DynamoDB" "dynamodb-test" "com.oddiya.aws.DynamoDBIntegrationTest"; then
        failed_tests+=("DynamoDB")
    fi
    
    # Run S3 tests
    if ! run_test_suite "S3" "s3-test" "com.oddiya.aws.S3IntegrationTest"; then
        failed_tests+=("S3")
    fi
    
    # Run SQS tests
    if ! run_test_suite "SQS" "sqs-test" "com.oddiya.aws.SQSIntegrationTest"; then
        failed_tests+=("SQS")
    fi
    
    # Run Profile Switching tests
    if ! run_test_suite "Profile Switching" "test" "com.oddiya.aws.ProfileSwitchingIntegrationTest"; then
        failed_tests+=("Profile Switching")
    fi
    
    # Report results
    if [ ${#failed_tests[@]} -eq 0 ]; then
        print_success "All AWS integration tests passed! 🎉"
        return 0
    else
        print_error "The following test suites failed: ${failed_tests[*]}"
        return 1
    fi
}

# Function to run performance benchmarks
run_performance_tests() {
    print_status "Running performance benchmarks..."
    
    export SPRING_PROFILES_ACTIVE=integration-test
    
    if timeout $TEST_TIMEOUT ./gradlew test --tests "*PerformanceTest" \
        -Dspring.profiles.active=integration-test \
        -Dtest.performance.enabled=true \
        --info; then
        print_success "Performance tests completed!"
    else
        print_warning "Performance tests had issues, but continuing..."
    fi
}

# Function to generate test report
generate_test_report() {
    print_status "Generating test reports..."
    
    ./gradlew jacocoTestReport
    
    if [ -f build/reports/jacoco/test/html/index.html ]; then
        print_success "Test coverage report generated: build/reports/jacoco/test/html/index.html"
    fi
    
    if [ -f build/reports/tests/test/index.html ]; then
        print_success "Test results report generated: build/reports/tests/test/index.html"
    fi
}

# Main execution function
main() {
    print_status "AWS Integration Tests Runner"
    print_status "============================="
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-cleanup)
                CLEANUP_ON_EXIT=false
                shift
                ;;
            --timeout=*)
                TEST_TIMEOUT="${1#*=}"
                shift
                ;;
            --localstack-timeout=*)
                LOCALSTACK_TIMEOUT="${1#*=}"
                shift
                ;;
            --performance)
                RUN_PERFORMANCE=true
                shift
                ;;
            --report-only)
                REPORT_ONLY=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --no-cleanup              Don't cleanup Docker containers on exit"
                echo "  --timeout=SECONDS          Test timeout (default: 300)"
                echo "  --localstack-timeout=SECONDS  LocalStack startup timeout (default: 60)"
                echo "  --performance              Run performance tests"
                echo "  --report-only              Only generate reports (skip tests)"
                echo "  --help, -h                 Show this help message"
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
    
    # Skip tests if report-only mode
    if [ "$REPORT_ONLY" = true ]; then
        generate_test_report
        exit 0
    fi
    
    # Check prerequisites
    check_prerequisites
    
    # Start LocalStack
    if ! start_localstack; then
        print_error "Failed to start LocalStack"
        exit 1
    fi
    
    # Run tests
    if ! run_all_tests; then
        print_error "Some tests failed"
        exit 1
    fi
    
    # Run performance tests if requested
    if [ "$RUN_PERFORMANCE" = true ]; then
        run_performance_tests
    fi
    
    # Generate test reports
    generate_test_report
    
    print_success "AWS Integration Tests completed successfully! 🎉"
}

# Run main function with all arguments
main "$@"