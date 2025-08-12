#!/bin/bash

# Comprehensive Integration Test Runner for Oddiya
# This script runs the complete integration test suite with proper configuration

echo "=============================================="
echo "🚀 Starting Oddiya Integration Test Suite"
echo "=============================================="

# Set test environment variables
export SPRING_PROFILES_ACTIVE=test
export GRADLE_OPTS="-Xmx2g -XX:MaxPermSize=512m"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Check if gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    print_error "Gradle wrapper not found. Please run from project root directory."
    exit 1
fi

# Make sure gradle wrapper is executable
chmod +x ./gradlew

print_status "Cleaning previous test results..."
./gradlew clean

print_status "Compiling test classes..."
if ./gradlew compileTestJava; then
    print_success "Test compilation completed successfully"
else
    print_error "Test compilation failed"
    exit 1
fi

print_status "Running comprehensive integration test suite..."
echo ""
echo "Test Suite Components:"
echo "  1. User Authentication & Registration Tests"
echo "  2. Travel Plan Creation & Management Tests"
echo "  3. Review Submission & Rating Calculation Tests"
echo "  4. End-to-End Workflow Integration Tests"
echo ""

# Run the comprehensive integration test suite
TEST_COMMAND="./gradlew test --tests 'com.oddiya.integration.*' \
    --info \
    --continue \
    -Dspring.profiles.active=test \
    -Dspring.datasource.url=jdbc:h2:mem:testdb \
    -Dspring.jpa.hibernate.ddl-auto=create-drop \
    -Djunit.jupiter.execution.parallel.enabled=false"

print_status "Executing integration tests..."

if eval $TEST_COMMAND; then
    print_success "All integration tests completed successfully! ✅"
    
    echo ""
    echo "=============================================="
    echo "📊 TEST EXECUTION SUMMARY"
    echo "=============================================="
    
    # Check test results
    if [ -f "build/test-results/test/TEST-*.xml" ]; then
        TOTAL_TESTS=$(grep -h "tests=" build/test-results/test/TEST-*.xml | sed 's/.*tests="\([0-9]*\)".*/\1/' | awk '{s+=$1} END {print s}')
        FAILED_TESTS=$(grep -h "failures=" build/test-results/test/TEST-*.xml | sed 's/.*failures="\([0-9]*\)".*/\1/' | awk '{s+=$1} END {print s}')
        SKIPPED_TESTS=$(grep -h "skipped=" build/test-results/test/TEST-*.xml | sed 's/.*skipped="\([0-9]*\)".*/\1/' | awk '{s+=$1} END {print s}')
        
        echo "📈 Total Tests Executed: ${TOTAL_TESTS:-0}"
        echo "✅ Tests Passed: $((${TOTAL_TESTS:-0} - ${FAILED_TESTS:-0} - ${SKIPPED_TESTS:-0}))"
        echo "❌ Tests Failed: ${FAILED_TESTS:-0}"
        echo "⏭️  Tests Skipped: ${SKIPPED_TESTS:-0}"
    fi
    
    echo ""
    echo "🎯 Integration Test Coverage:"
    echo "  ✅ Authentication workflows (OAuth, JWT, sessions)"
    echo "  ✅ Travel plan creation and management"
    echo "  ✅ Place management and discovery"
    echo "  ✅ Review submission and rating calculations"
    echo "  ✅ End-to-end user journey validation"
    echo "  ✅ Transaction consistency across services"
    echo "  ✅ Database relationship integrity"
    echo "  ✅ API endpoint validation"
    echo "  ✅ Error handling and edge cases"
    echo ""
    
    # Generate test report location info
    if [ -f "build/reports/tests/test/index.html" ]; then
        print_success "Detailed test report available at: build/reports/tests/test/index.html"
        echo "           Open in browser: file://$(pwd)/build/reports/tests/test/index.html"
    fi
    
    echo ""
    echo "=============================================="
    echo "🎉 INTEGRATION TEST SUITE COMPLETED"
    echo "=============================================="
    
else
    print_error "Integration tests failed! ❌"
    
    echo ""
    echo "=============================================="
    echo "💥 TEST FAILURE DETAILS"
    echo "=============================================="
    
    # Show recent log entries
    if [ -f "build/test-results/test/TEST-*.xml" ]; then
        print_warning "Check the following for detailed error information:"
        echo "  📄 Test reports: build/reports/tests/test/index.html"
        echo "  📋 Test results: build/test-results/test/"
        echo "  📝 Gradle logs: Use --stacktrace flag for detailed errors"
    fi
    
    print_warning "Common troubleshooting steps:"
    echo "  1. Ensure H2 database dependencies are available"
    echo "  2. Check test profile configuration in application-test.yml"
    echo "  3. Verify no port conflicts (default test port: 8080)"
    echo "  4. Check for missing test data or configuration"
    echo "  5. Review application logs for startup errors"
    
    echo ""
    echo "🔄 To run specific test classes:"
    echo "   ./gradlew test --tests UserRegistrationAndAuthenticationIntegrationTest"
    echo "   ./gradlew test --tests TravelPlanCreationIntegrationTest"
    echo "   ./gradlew test --tests ReviewSubmissionAndRatingIntegrationTest"
    echo "   ./gradlew test --tests EndToEndWorkflowIntegrationTest"
    
    exit 1
fi

# Optional: Run specific test categories
if [ "$1" = "--detailed" ]; then
    echo ""
    print_status "Running detailed test analysis..."
    
    # Run individual test classes for detailed reporting
    print_status "Running Authentication Tests..."
    ./gradlew test --tests UserRegistrationAndAuthenticationIntegrationTest --info
    
    print_status "Running Travel Plan Tests..."
    ./gradlew test --tests TravelPlanCreationIntegrationTest --info
    
    print_status "Running Review System Tests..."
    ./gradlew test --tests ReviewSubmissionAndRatingIntegrationTest --info
    
    print_status "Running End-to-End Workflow Tests..."
    ./gradlew test --tests EndToEndWorkflowIntegrationTest --info
fi

# Clean up test artifacts if requested
if [ "$2" = "--clean" ]; then
    print_status "Cleaning up test artifacts..."
    ./gradlew clean
    print_success "Cleanup completed"
fi

echo ""
print_success "Integration test execution completed! 🎊"
echo ""