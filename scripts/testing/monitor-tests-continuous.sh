#!/bin/bash
# Continuous Test Monitoring Script
# Provides real-time test metrics dashboard

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m'

# Configuration
REPORT_DIR="build/reports/test-monitoring"
MONITORING_INTERVAL=${1:-30}  # Default 30 seconds, can be overridden
TIMESTAMP_FORMAT="%Y-%m-%d %H:%M:%S"

# Create report directory
mkdir -p "$REPORT_DIR"

# Function to get terminal width
get_terminal_width() {
    echo "${COLUMNS:-$(tput cols 2>/dev/null || echo 80)}"
}

# Function to print centered text
print_centered() {
    local text="$1"
    local width=$(get_terminal_width)
    local padding=$(( (width - ${#text}) / 2 ))
    printf "%*s%s%*s\n" $padding "" "$text" $padding ""
}

# Function to print separator
print_separator() {
    local char="${1:-═}"
    local width=$(get_terminal_width)
    printf "${BLUE}%${width}s${NC}\n" | tr ' ' "$char"
}

# Function to format percentage with color
format_percentage() {
    local value=$1
    local threshold=$2
    local warning_threshold=$3
    
    if (( $(echo "$value >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${GREEN}${value}%${NC}"
    elif (( $(echo "$value >= $warning_threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${YELLOW}${value}%${NC}"
    else
        echo -e "${RED}${value}%${NC}"
    fi
}

# Function to get coverage metrics
get_coverage_metrics() {
    local csv_file="build/reports/jacoco/test/jacocoTestReport.csv"
    
    if [ -f "$csv_file" ]; then
        local lines=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$4; covered+=$5} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        local branches=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$6; covered+=$7} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        local complexity=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{sum+=$8} END {print sum}' || echo "0")
        
        echo -e "  📊 Line Coverage:      $(format_percentage "$lines" 80 60) (Target: 85%)"
        echo -e "  🌳 Branch Coverage:    $(format_percentage "$branches" 75 50) (Target: 80%)"
        echo -e "  🔀 Complexity:         ${complexity}"
    else
        echo -e "  ${YELLOW}No coverage data available${NC}"
        echo -e "  ${CYAN}Run: ./gradlew test jacocoTestReport${NC}"
    fi
}

# Function to get test execution metrics
get_test_metrics() {
    if [ -d "build/test-results/test" ]; then
        local total=$(find build/test-results/test -name "*.xml" -exec grep -c '<testcase' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local failures=$(find build/test-results/test -name "*.xml" -exec grep -c '<failure' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local errors=$(find build/test-results/test -name "*.xml" -exec grep -c '<error' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        local skipped=$(find build/test-results/test -name "*.xml" -exec grep -c '<skipped' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
        
        local passed=$((total - failures - errors - skipped))
        local success_rate=0
        if [ "$total" -gt 0 ]; then
            success_rate=$((passed * 100 / total))
        fi
        
        echo -e "  📝 Total Tests:        ${BOLD}${total}${NC}"
        echo -e "  ✅ Passed:            ${GREEN}${passed}${NC}"
        if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
            echo -e "  ❌ Failed:            ${RED}$((failures + errors))${NC}"
        else
            echo -e "  ❌ Failed:            ${GREEN}0${NC}"
        fi
        if [ "$skipped" -gt 0 ]; then
            echo -e "  ⏭️  Skipped:           ${YELLOW}${skipped}${NC}"
        else
            echo -e "  ⏭️  Skipped:           ${GREEN}0${NC}"
        fi
        echo -e "  📈 Success Rate:       $(format_percentage "$success_rate" 98 95)"
        
        # Get execution time
        local exec_time=$(find build/test-results/test -name "*.xml" -exec grep -o 'time="[^"]*"' {} \; 2>/dev/null | sed 's/time="//' | sed 's/"//' | awk '{sum+=$1} END {printf "%.2f", sum}' || echo "0")
        echo -e "  ⏱️  Execution Time:    ${exec_time}s"
    else
        echo -e "  ${YELLOW}No test results available${NC}"
        echo -e "  ${CYAN}Run: ./gradlew test${NC}"
    fi
}

# Function to get security metrics
get_security_metrics() {
    local report="build/reports/dependency-check-report.html"
    
    if [ -f "$report" ]; then
        local critical=$(grep -o "Critical</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local high=$(grep -o "High</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local medium=$(grep -o "Medium</td>" "$report" 2>/dev/null | wc -l || echo "0")
        local low=$(grep -o "Low</td>" "$report" 2>/dev/null | wc -l || echo "0")
        
        echo -n "  🔴 Critical: "
        if [ "$critical" -gt 0 ]; then
            echo -e "${RED}${critical}${NC}"
        else
            echo -e "${GREEN}0${NC}"
        fi
        
        echo -n "  🟠 High:     "
        if [ "$high" -gt 0 ]; then
            echo -e "${YELLOW}${high}${NC}"
        else
            echo -e "${GREEN}0${NC}"
        fi
        
        echo -e "  🟡 Medium:   ${medium}"
        echo -e "  🟢 Low:      ${low}"
        
        if [ "$critical" -gt 0 ]; then
            echo -e "  ${RED}⚠️  Critical vulnerabilities require immediate attention!${NC}"
        fi
    else
        echo -e "  ${YELLOW}No security scan available${NC}"
        echo -e "  ${CYAN}Run: ./gradlew dependencyCheckAnalyze${NC}"
    fi
}

# Function to get mutation testing metrics
get_mutation_metrics() {
    local xml_file=$(find build/reports/pitest -name "mutations.xml" 2>/dev/null | head -1)
    
    if [ -f "$xml_file" ]; then
        local mutation_score=$(grep -o 'mutationScore="[^"]*"' "$xml_file" 2>/dev/null | cut -d'"' -f2 | head -1 || echo "0")
        local total_mutations=$(grep -o '<mutation ' "$xml_file" 2>/dev/null | wc -l || echo "0")
        local killed=$(grep -o 'status="KILLED"' "$xml_file" 2>/dev/null | wc -l || echo "0")
        local survived=$(grep -o 'status="SURVIVED"' "$xml_file" 2>/dev/null | wc -l || echo "0")
        
        echo -e "  🎯 Mutation Score:     $(format_percentage "$mutation_score" 80 60) (Target: 80%)"
        echo -e "  🔢 Total Mutations:    ${total_mutations}"
        echo -e "  ☠️  Killed:            ${GREEN}${killed}${NC}"
        if [ "$survived" -gt 0 ]; then
            echo -e "  🧟 Survived:          ${RED}${survived}${NC}"
        else
            echo -e "  🧟 Survived:          ${GREEN}0${NC}"
        fi
    else
        echo -e "  ${YELLOW}No mutation test results${NC}"
        echo -e "  ${CYAN}Run: ./gradlew pitest${NC}"
    fi
}

# Function to get performance metrics
get_performance_metrics() {
    local jmeter_results="build/reports/jmeter/results.csv"
    local gatling_stats=$(find build/reports/gatling -name "stats.json" 2>/dev/null | head -1)
    
    if [ -f "$jmeter_results" ]; then
        local avg_response=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}' "$jmeter_results")
        local max_response=$(awk -F',' 'NR>1 {if($2>max) max=$2} END {printf "%.0f", max}' "$jmeter_results")
        local error_rate=$(awk -F',' 'NR>1 {if($4!="true") errors++; total++} END {if(total>0) printf "%.1f", errors*100/total; else print "0"}' "$jmeter_results")
        
        echo -e "  📊 Avg Response:       $(format_percentage "$avg_response" 200 500)ms"
        echo -e "  📈 Max Response:       ${max_response}ms"
        echo -e "  ❌ Error Rate:        $(format_percentage "$error_rate" 1 5)"
    elif [ -f "$gatling_stats" ]; then
        local avg_response=$(grep -o '"mean":[0-9]*' "$gatling_stats" 2>/dev/null | head -1 | cut -d':' -f2 || echo "0")
        echo -e "  📊 Avg Response:       ${avg_response}ms"
    else
        echo -e "  ${YELLOW}No performance test results${NC}"
        echo -e "  ${CYAN}Run: ./gradlew jmRun or ./gradlew gatlingRun${NC}"
    fi
}

# Function to display test trend
display_trend() {
    local history_file="$REPORT_DIR/coverage-history.txt"
    
    if [ -f "$history_file" ]; then
        echo -e "\n${BOLD}${CYAN}📈 Coverage Trend (Last 5 runs)${NC}"
        tail -5 "$history_file" | while read -r line; do
            echo "  $line"
        done
    fi
}

# Function to save current metrics
save_metrics() {
    local history_file="$REPORT_DIR/coverage-history.txt"
    local csv_file="build/reports/jacoco/test/jacocoTestReport.csv"
    
    if [ -f "$csv_file" ]; then
        local coverage=$(tail -n +2 "$csv_file" 2>/dev/null | awk -F',' '{missed+=$4; covered+=$5} END {if(missed+covered>0) printf "%.1f", covered/(missed+covered)*100; else print "0"}')
        echo "$(date +"$TIMESTAMP_FORMAT") - Coverage: ${coverage}%" >> "$history_file"
    fi
}

# Main monitoring loop
monitor_loop() {
    while true; do
        clear
        
        # Header
        print_separator "═"
        echo -e "${BLUE}${BOLD}$(print_centered "🎯 ODDIYA TEST MONITORING DASHBOARD 🎯")${NC}"
        echo -e "${CYAN}$(print_centered "$(date +"$TIMESTAMP_FORMAT")")${NC}"
        print_separator "═"
        
        # Coverage Section
        echo -e "\n${BOLD}${GREEN}📊 COVERAGE METRICS${NC}"
        print_separator "─"
        get_coverage_metrics
        
        # Test Execution Section
        echo -e "\n${BOLD}${MAGENTA}🧪 TEST EXECUTION${NC}"
        print_separator "─"
        get_test_metrics
        
        # Security Section
        echo -e "\n${BOLD}${RED}🔒 SECURITY SCAN${NC}"
        print_separator "─"
        get_security_metrics
        
        # Mutation Testing Section
        echo -e "\n${BOLD}${YELLOW}🧬 MUTATION TESTING${NC}"
        print_separator "─"
        get_mutation_metrics
        
        # Performance Section
        echo -e "\n${BOLD}${CYAN}⚡ PERFORMANCE TESTS${NC}"
        print_separator "─"
        get_performance_metrics
        
        # Trend Display
        display_trend
        
        # Save current metrics
        save_metrics
        
        # Footer
        print_separator "═"
        echo -e "${BLUE}${BOLD}Quick Commands:${NC}"
        echo -e "  ${CYAN}[T]${NC} Run Tests  ${CYAN}[C]${NC} Coverage  ${CYAN}[S]${NC} Security  ${CYAN}[M]${NC} Mutation  ${CYAN}[P]${NC} Performance  ${CYAN}[Q]${NC} Quit"
        echo -e "${YELLOW}Refreshing in ${MONITORING_INTERVAL} seconds...${NC} (Press Ctrl+C to exit)"
        print_separator "═"
        
        # Handle user input with timeout
        if read -t $MONITORING_INTERVAL -n 1 key; then
            case $key in
                t|T)
                    echo -e "\n${GREEN}Running tests...${NC}"
                    ./gradlew test
                    ;;
                c|C)
                    echo -e "\n${GREEN}Generating coverage report...${NC}"
                    ./gradlew jacocoTestReport
                    ;;
                s|S)
                    echo -e "\n${GREEN}Running security scan...${NC}"
                    ./gradlew dependencyCheckAnalyze
                    ;;
                m|M)
                    echo -e "\n${GREEN}Running mutation tests...${NC}"
                    ./gradlew pitest
                    ;;
                p|P)
                    echo -e "\n${GREEN}Running performance tests...${NC}"
                    ./gradlew jmRun
                    ;;
                q|Q)
                    echo -e "\n${GREEN}Exiting monitoring...${NC}"
                    exit 0
                    ;;
            esac
        fi
    done
}

# Trap Ctrl+C to exit gracefully
trap 'echo -e "\n${GREEN}Monitoring stopped.${NC}"; exit 0' INT

# Start monitoring
echo -e "${GREEN}Starting continuous test monitoring...${NC}"
echo -e "${YELLOW}Refresh interval: ${MONITORING_INTERVAL} seconds${NC}"
sleep 2

monitor_loop