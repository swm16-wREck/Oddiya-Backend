#!/bin/bash

# PITest Mutation Testing Quick Start Script for Oddiya
# This script helps you get started with mutation testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="Oddiya"
GRADLE_OPTS="-Xmx4g -XX:+UseG1GC"

print_header() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║              🧬 PITest Mutation Testing QuickStart            ║"
    echo "║                        for $PROJECT_NAME                           ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_usage() {
    echo -e "${CYAN}Usage: $0 [OPTIONS]${NC}"
    echo ""
    echo "Options:"
    echo "  --setup     Run initial setup and validation"
    echo "  --demo      Run a quick demonstration"
    echo "  --help      Show this help message"
    echo ""
    echo "Without options, runs interactive mode."
}

log() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        error "Java is not installed or not in PATH"
        echo "Please install Java 21 or higher"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        error "Java 21 or higher is required. Current version: $JAVA_VERSION"
        echo "Please upgrade your Java version"
        exit 1
    fi
    
    success "Java version: $(java -version 2>&1 | head -n 1)"
    
    # Check Gradle wrapper
    if [ ! -f "gradlew" ]; then
        error "gradlew not found. Make sure you're in the project root directory"
        exit 1
    fi
    
    success "Gradle wrapper found"
    
    # Check if we're in the right directory
    if [ ! -f "build.gradle" ]; then
        error "build.gradle not found. Make sure you're in the project root"
        exit 1
    fi
    
    success "Project structure validated"
}

setup_pitest() {
    log "Setting up PITest configuration..."
    
    # Ensure scripts directory exists and is executable
    mkdir -p scripts
    chmod +x scripts/*.sh 2>/dev/null || true
    
    # Ensure config directory exists
    mkdir -p config/pitest
    
    # Ensure reports directory exists
    mkdir -p build/reports/pitest
    
    success "PITest directories configured"
}

run_demo() {
    log "Running PITest demonstration..."
    
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                    🎬 DEMO MODE STARTED                        ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    
    echo -e "\n${YELLOW}Step 1: Compiling project and tests...${NC}"
    GRADLE_OPTS="$GRADLE_OPTS" ./gradlew compileJava compileTestJava
    
    echo -e "\n${YELLOW}Step 2: Running unit tests to ensure they pass...${NC}"
    GRADLE_OPTS="$GRADLE_OPTS" ./gradlew test --continue || {
        warn "Some unit tests failed, but continuing with demo..."
    }
    
    echo -e "\n${YELLOW}Step 3: Running fast mutation tests (this may take a few minutes)...${NC}"
    echo "This will test a subset of your service classes with basic mutation operators."
    
    GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestFast || {
        warn "Fast mutation tests encountered issues, but demo continues..."
    }
    
    echo -e "\n${YELLOW}Step 4: Generating reports...${NC}"
    
    # Find generated reports
    REPORT_DIRS=$(find build/reports/pitest -name "index.html" 2>/dev/null | head -3)
    
    if [ -n "$REPORT_DIRS" ]; then
        success "Mutation testing reports generated!"
        echo -e "\n${CYAN}📊 View your reports:${NC}"
        for REPORT in $REPORT_DIRS; do
            echo -e "  🔗 file://$PWD/$REPORT"
        done
        
        echo -e "\n${CYAN}🎯 Key Metrics to Look For:${NC}"
        echo "  • Mutation Score: Percentage of mutations killed by tests"
        echo "  • Test Strength: How well tests detect changes"
        echo "  • Line Coverage: Traditional code coverage"
        
        echo -e "\n${CYAN}📈 Interpreting Results:${NC}"
        echo "  • 90-100%: Excellent test quality"
        echo "  • 80-89%:  Good test quality"
        echo "  • 70-79%:  Acceptable test quality"
        echo "  • <70%:    Needs improvement"
        
    else
        warn "No reports found. This might be due to configuration issues."
        echo "Try running: ./gradlew pitest --info"
    fi
    
    echo -e "\n${GREEN}✨ Demo completed! ✨${NC}"
    echo -e "\n${YELLOW}Next Steps:${NC}"
    echo "1. Review the generated HTML reports"
    echo "2. Identify classes with low mutation scores"
    echo "3. Add tests for uncovered scenarios"
    echo "4. Run './scripts/run-mutation-tests.sh critical' for critical components"
    echo "5. Integrate mutation testing into your CI/CD pipeline"
}

interactive_menu() {
    while true; do
        echo -e "\n${CYAN}🧬 PITest Mutation Testing Menu${NC}"
        echo "─────────────────────────────────────────"
        echo "1. 🚀 Run Quick Demo (Fast Profile)"
        echo "2. 🎯 Test Critical Components"
        echo "3. 🏢 Test Service Layer"
        echo "4. 🌐 Test Controller Layer"
        echo "5. 💾 Test Repository Layer"
        echo "6. 🔍 View Available Tasks"
        echo "7. 📚 Open Documentation"
        echo "8. 🛠️  Run Setup Check"
        echo "9. ❌ Exit"
        echo ""
        read -p "Select an option (1-9): " choice
        
        case $choice in
            1)
                log "Running quick demo with fast profile..."
                GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestFast
                show_reports "fast"
                ;;
            2)
                log "Running critical component tests..."
                GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestCritical
                show_reports "critical"
                ;;
            3)
                log "Running service layer tests..."
                GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestService
                show_reports "service"
                ;;
            4)
                log "Running controller layer tests..."
                GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestController
                show_reports "controller"
                ;;
            5)
                log "Running repository layer tests..."
                GRADLE_OPTS="$GRADLE_OPTS" ./gradlew pitestRepository
                show_reports "repository"
                ;;
            6)
                show_available_tasks
                ;;
            7)
                show_documentation
                ;;
            8)
                check_prerequisites
                setup_pitest
                success "Setup check completed!"
                ;;
            9)
                log "Goodbye! Happy testing! 🧬"
                exit 0
                ;;
            *)
                error "Invalid option. Please select 1-9."
                ;;
        esac
        
        echo ""
        read -p "Press Enter to continue..."
    done
}

show_reports() {
    local profile=$1
    
    # Find reports for the specific profile
    local report_pattern="build/reports/pitest"
    if [ "$profile" != "fast" ]; then
        report_pattern="$report_pattern/$profile"
    fi
    
    local reports=$(find "$report_pattern" -name "index.html" 2>/dev/null || true)
    
    if [ -n "$reports" ]; then
        success "Reports generated for $profile profile!"
        echo -e "\n${CYAN}📊 Open these reports in your browser:${NC}"
        for report in $reports; do
            echo -e "  🔗 file://$PWD/$report"
        done
    else
        warn "No reports found for $profile profile"
        echo "Check the console output for any error messages"
    fi
}

show_available_tasks() {
    echo -e "\n${CYAN}🛠️  Available PITest Tasks:${NC}"
    echo "─────────────────────────────────────"
    echo "• pitest          - Run all mutation tests"
    echo "• pitestFast      - Quick tests for development"
    echo "• pitestCritical  - Test critical business logic"
    echo "• pitestService   - Test service layer"
    echo "• pitestController- Test controller layer"
    echo "• pitestRepository- Test repository layer"
    echo ""
    echo -e "${CYAN}📜 Usage Examples:${NC}"
    echo "• ./gradlew pitestFast --info"
    echo "• ./gradlew pitestCritical --parallel"
    echo "• ./scripts/run-mutation-tests.sh critical"
}

show_documentation() {
    echo -e "\n${CYAN}📚 Documentation Resources:${NC}"
    echo "─────────────────────────────────────"
    
    if [ -f "MUTATION_TESTING_GUIDE.md" ]; then
        echo "• Local Guide: MUTATION_TESTING_GUIDE.md"
    fi
    
    echo "• PITest Official: https://pitest.org/"
    echo "• Gradle Plugin:  https://gradle-pitest-plugin.solidsoft.info/"
    echo "• Best Practices: https://pitest.org/quickstart/basic_concepts/"
    echo ""
    echo -e "${CYAN}🔧 Configuration Files:${NC}"
    echo "• build.gradle (main configuration)"
    echo "• config/pitest/pitest.properties (advanced settings)"
}

# Main execution
main() {
    print_header
    
    case "${1:-interactive}" in
        --setup)
            check_prerequisites
            setup_pitest
            success "Setup completed successfully!"
            ;;
        --demo)
            check_prerequisites
            setup_pitest
            run_demo
            ;;
        --help|-h)
            print_usage
            ;;
        interactive)
            check_prerequisites
            setup_pitest
            interactive_menu
            ;;
        *)
            error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
}

# Handle Ctrl+C gracefully
trap 'echo -e "\n${YELLOW}Interrupted by user${NC}"; exit 1' INT

# Execute main function
main "$@"