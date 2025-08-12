#!/bin/bash

# Comprehensive mutation testing script for Oddiya
# Usage: ./run-mutation-tests.sh [profile] [options]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="Oddiya"
GRADLE_OPTS="-Xmx4g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m"
REPORTS_DIR="build/reports/pitest"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

print_usage() {
    echo -e "${BLUE}=== $PROJECT_NAME Mutation Testing Script ===${NC}"
    echo ""
    echo "Usage: $0 [PROFILE] [OPTIONS]"
    echo ""
    echo "Profiles:"
    echo "  all         - Run comprehensive mutation tests on all classes (default)"
    echo "  critical    - Run mutation tests on critical business logic (highest threshold)"
    echo "  service     - Run mutation tests on service classes only"
    echo "  controller  - Run mutation tests on controller classes only"
    echo "  repository  - Run mutation tests on repository classes only"
    echo "  fast        - Run fast mutation tests for development (reduced scope)"
    echo ""
    echo "Options:"
    echo "  --no-clean      Skip clean before running tests"
    echo "  --parallel      Run tests in parallel where possible"
    echo "  --html-only     Generate HTML reports only"
    echo "  --incremental   Use incremental analysis for faster runs"
    echo "  --help         Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Run all mutation tests"
    echo "  $0 critical           # Run critical business logic tests"
    echo "  $0 fast --no-clean    # Run fast tests without clean"
    echo "  $0 service --parallel # Run service tests in parallel"
}

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

check_prerequisites() {
    log "Checking prerequisites..."
    
    if ! command -v java &> /dev/null; then
        error "Java is not installed or not in PATH"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        error "Java 21 or higher is required. Current version: $JAVA_VERSION"
        exit 1
    fi
    
    if [ ! -f "gradlew" ]; then
        error "gradlew not found. Make sure you're running from the project root."
        exit 1
    fi
    
    log "Java version: $(java -version 2>&1 | head -n 1)"
}

clean_build() {
    if [ "$SKIP_CLEAN" != "true" ]; then
        log "Cleaning previous builds..."
        GRADLE_OPTS="$GRADLE_OPTS" ./gradlew clean
    else
        log "Skipping clean as requested"
    fi
}

compile_tests() {
    log "Compiling tests..."
    GRADLE_OPTS="$GRADLE_OPTS" ./gradlew compileTestJava
}

run_unit_tests() {
    log "Running unit tests first..."
    GRADLE_OPTS="$GRADLE_OPTS" ./gradlew test --continue
    
    if [ $? -ne 0 ]; then
        warn "Some unit tests failed. Continuing with mutation testing..."
    fi
}

run_mutation_test() {
    local profile=$1
    local task_name=$2
    
    log "Running mutation tests for profile: $profile"
    
    case $profile in
        "all")
            log "Running comprehensive mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitest
            ;;
        "critical")
            log "Running critical business logic mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestCritical
            ;;
        "service")
            log "Running service layer mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestService
            ;;
        "controller")
            log "Running controller layer mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestController
            ;;
        "repository")
            log "Running repository layer mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestRepository
            ;;
        "fast")
            log "Running fast mutation tests..."
            GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestFast
            ;;
        *)
            error "Unknown profile: $profile"
            print_usage
            exit 1
            ;;
    esac
}

generate_summary_report() {
    local profile=$1
    log "Generating mutation testing summary for profile: $profile"
    
    # Create summary directory
    mkdir -p "$REPORTS_DIR/summary"
    
    # Find HTML reports
    local html_reports=$(find "$REPORTS_DIR" -name "index.html" 2>/dev/null || true)
    
    if [ -z "$html_reports" ]; then
        warn "No HTML reports found"
        return
    fi
    
    # Create summary HTML
    cat > "$REPORTS_DIR/summary/mutation-summary-$profile-$TIMESTAMP.html" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Oddiya Mutation Testing Summary</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .header { background: #f4f4f4; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; }
        .metric { display: inline-block; margin: 10px 20px; padding: 10px; 
                 background: #e8f4fd; border-radius: 5px; }
        .critical { background: #ffe6e6; }
        .good { background: #e6ffe6; }
        .warning { background: #fff3cd; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🧬 Oddiya Mutation Testing Summary</h1>
        <p>Profile: <strong>PROFILE_PLACEHOLDER</strong></p>
        <p>Generated: <strong>TIMESTAMP_PLACEHOLDER</strong></p>
    </div>
    
    <div class="section">
        <h2>📊 Key Metrics</h2>
        <div class="metric">
            <strong>Mutation Score:</strong><br>
            <span style="font-size: 2em; color: #007acc;">N/A%</span>
        </div>
        <div class="metric">
            <strong>Test Strength:</strong><br>
            <span style="font-size: 2em; color: #28a745;">N/A%</span>
        </div>
        <div class="metric">
            <strong>Code Coverage:</strong><br>
            <span style="font-size: 2em; color: #ffc107;">N/A%</span>
        </div>
    </div>
    
    <div class="section">
        <h2>📁 Report Links</h2>
        <p>Detailed mutation testing reports are available in the build/reports/pitest directory.</p>
        <p>Open the index.html files in each subdirectory to view detailed results.</p>
    </div>
    
    <div class="section">
        <h2>🎯 Recommendations</h2>
        <ul>
            <li>Focus on improving tests for classes with low mutation scores (&lt;80%)</li>
            <li>Review survived mutations to identify missing test cases</li>
            <li>Consider adding boundary value tests for mathematical operations</li>
            <li>Add negative test cases for conditional logic</li>
        </ul>
    </div>
</body>
</html>
EOF
    
    # Replace placeholders
    sed -i.bak "s/PROFILE_PLACEHOLDER/$profile/g" "$REPORTS_DIR/summary/mutation-summary-$profile-$TIMESTAMP.html"
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$(date)/g" "$REPORTS_DIR/summary/mutation-summary-$profile-$TIMESTAMP.html"
    rm -f "$REPORTS_DIR/summary/mutation-summary-$profile-$TIMESTAMP.html.bak"
    
    log "Summary report generated: $REPORTS_DIR/summary/mutation-summary-$profile-$TIMESTAMP.html"
}

print_results() {
    local profile=$1
    
    log "Mutation testing completed for profile: $profile"
    echo ""
    echo -e "${BLUE}=== RESULTS ===${NC}"
    
    # Find and display report locations
    if [ -d "$REPORTS_DIR" ]; then
        echo -e "${GREEN}📊 Reports generated in:${NC}"
        find "$REPORTS_DIR" -name "index.html" -exec echo "  🔗 file://$PWD/{}" \;
        echo ""
    fi
    
    echo -e "${YELLOW}💡 Next Steps:${NC}"
    echo "1. Open the HTML reports to analyze mutation testing results"
    echo "2. Focus on classes with low mutation scores (<80%)"
    echo "3. Review survived mutations to identify missing tests"
    echo "4. Add tests for uncovered edge cases and boundary conditions"
    echo ""
    
    if [ "$profile" = "critical" ]; then
        echo -e "${RED}⚠️  Critical Profile Notice:${NC}"
        echo "This profile has the highest quality thresholds (90% mutation score)."
        echo "Consider improving test coverage for any failing classes."
    fi
}

# Parse command line arguments
PROFILE="all"
SKIP_CLEAN="false"
PARALLEL="false"
HTML_ONLY="false"
INCREMENTAL="false"

while [[ $# -gt 0 ]]; do
    case $1 in
        --help|-h)
            print_usage
            exit 0
            ;;
        --no-clean)
            SKIP_CLEAN="true"
            shift
            ;;
        --parallel)
            PARALLEL="true"
            GRADLE_OPTS="$GRADLE_OPTS --parallel"
            shift
            ;;
        --html-only)
            HTML_ONLY="true"
            shift
            ;;
        --incremental)
            INCREMENTAL="true"
            shift
            ;;
        all|critical|service|controller|repository|fast)
            PROFILE=$1
            shift
            ;;
        *)
            error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    log "Starting $PROJECT_NAME mutation testing with profile: $PROFILE"
    
    check_prerequisites
    clean_build
    compile_tests
    run_unit_tests
    run_mutation_test "$PROFILE"
    generate_summary_report "$PROFILE"
    print_results "$PROFILE"
}

# Execute main function
main "$@"