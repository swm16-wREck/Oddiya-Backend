#!/bin/bash

# PITest Mutation Testing Report Generator
# Parses PITest XML reports and generates summary information

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Configuration
REPORTS_BASE_DIR="build/reports/pitest"
OUTPUT_DIR="build/reports/pitest/summary"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

print_usage() {
    echo -e "${BLUE}PITest Mutation Testing Report Generator${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --profile PROFILE   Generate report for specific profile (fast, critical, service, controller, repository, all)"
    echo "  --all-profiles      Generate reports for all available profiles"
    echo "  --format FORMAT     Output format: console, html, csv, json (default: console)"
    echo "  --threshold NUM     Highlight results below threshold percentage (default: 80)"
    echo "  --output-dir DIR    Output directory for generated reports (default: $OUTPUT_DIR)"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                            # Generate console report for all found results"
    echo "  $0 --profile critical         # Generate report for critical profile only"
    echo "  $0 --format html --all-profiles  # Generate HTML reports for all profiles"
}

log() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ERROR: $1${NC}"
}

success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] SUCCESS: $1${NC}"
}

parse_xml_report() {
    local xml_file=$1
    local profile=$2
    
    if [ ! -f "$xml_file" ]; then
        warn "XML report not found: $xml_file"
        return 1
    fi
    
    # Parse XML using grep and sed (more portable than xmllint)
    local mutation_score=$(grep -o 'mutationScore="[^"]*"' "$xml_file" 2>/dev/null | cut -d'"' -f2 | head -1 || echo "0")
    local test_strength=$(grep -o 'testStrength="[^"]*"' "$xml_file" 2>/dev/null | cut -d'"' -f2 | head -1 || echo "0")
    local coverage=$(grep -o 'coverage="[^"]*"' "$xml_file" 2>/dev/null | cut -d'"' -f2 | head -1 || echo "0")
    local total_mutations=$(grep -o '<mutation ' "$xml_file" | wc -l || echo "0")
    local killed_mutations=$(grep -o 'status="KILLED"' "$xml_file" | wc -l || echo "0")
    local survived_mutations=$(grep -o 'status="SURVIVED"' "$xml_file" | wc -l || echo "0")
    local no_coverage_mutations=$(grep -o 'status="NO_COVERAGE"' "$xml_file" | wc -l || echo "0")
    local timeout_mutations=$(grep -o 'status="TIMED_OUT"' "$xml_file" | wc -l || echo "0")
    local memory_error_mutations=$(grep -o 'status="MEMORY_ERROR"' "$xml_file" | wc -l || echo "0")
    
    # Clean up values (remove decimals for whole numbers, handle percentages)
    mutation_score=$(echo "$mutation_score" | sed 's/\.00$//' | sed 's/\.0$//')
    test_strength=$(echo "$test_strength" | sed 's/\.00$//' | sed 's/\.0$//')
    coverage=$(echo "$coverage" | sed 's/\.00$//' | sed 's/\.0$//')
    
    # Store results in global variables for use by formatters
    PROFILE="$profile"
    MUTATION_SCORE="$mutation_score"
    TEST_STRENGTH="$test_strength"
    COVERAGE="$coverage"
    TOTAL_MUTATIONS="$total_mutations"
    KILLED_MUTATIONS="$killed_mutations"
    SURVIVED_MUTATIONS="$survived_mutations"
    NO_COVERAGE_MUTATIONS="$no_coverage_mutations"
    TIMEOUT_MUTATIONS="$timeout_mutations"
    MEMORY_ERROR_MUTATIONS="$memory_error_mutations"
}

get_status_color() {
    local score=$1
    local threshold=$2
    
    if (( $(echo "$score >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo "$GREEN"
    elif (( $(echo "$score >= $(($threshold - 10))" | bc -l 2>/dev/null || echo "0") )); then
        echo "$YELLOW"
    else
        echo "$RED"
    fi
}

get_status_emoji() {
    local score=$1
    local threshold=$2
    
    if (( $(echo "$score >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo "✅"
    elif (( $(echo "$score >= $(($threshold - 10))" | bc -l 2>/dev/null || echo "0") )); then
        echo "⚠️"
    else
        echo "❌"
    fi
}

format_console_report() {
    local threshold=$1
    
    echo -e "\n${BOLD}${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║                🧬 MUTATION TESTING REPORT                    ║${NC}"
    echo -e "${BOLD}${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
    
    echo -e "\n${CYAN}📊 Profile: ${BOLD}$PROFILE${NC}"
    echo -e "${CYAN}🕒 Generated: ${BOLD}$(date)${NC}"
    echo -e "${CYAN}🎯 Threshold: ${BOLD}${threshold}%${NC}"
    
    echo -e "\n${BOLD}${BLUE}═══ KEY METRICS ═══${NC}"
    
    local mutation_color=$(get_status_color "$MUTATION_SCORE" "$threshold")
    local mutation_emoji=$(get_status_emoji "$MUTATION_SCORE" "$threshold")
    echo -e "${mutation_color}${mutation_emoji} Mutation Score:  ${BOLD}${MUTATION_SCORE}%${NC}"
    
    local strength_color=$(get_status_color "$TEST_STRENGTH" "$threshold")
    local strength_emoji=$(get_status_emoji "$TEST_STRENGTH" "$threshold")
    echo -e "${strength_color}${strength_emoji} Test Strength:   ${BOLD}${TEST_STRENGTH}%${NC}"
    
    local coverage_color=$(get_status_color "$COVERAGE" "70")
    local coverage_emoji=$(get_status_emoji "$COVERAGE" "70")
    echo -e "${coverage_color}${coverage_emoji} Line Coverage:   ${BOLD}${COVERAGE}%${NC}"
    
    echo -e "\n${BOLD}${BLUE}═══ MUTATION BREAKDOWN ═══${NC}"
    echo -e "🔢 Total Mutations:      ${BOLD}$TOTAL_MUTATIONS${NC}"
    echo -e "☠️  Killed Mutations:     ${BOLD}$KILLED_MUTATIONS${NC} ${GREEN}(Good)${NC}"
    echo -e "🧟 Survived Mutations:   ${BOLD}$SURVIVED_MUTATIONS${NC} ${RED}(Needs attention)${NC}"
    
    if [ "$NO_COVERAGE_MUTATIONS" -gt 0 ]; then
        echo -e "🚫 No Coverage:          ${BOLD}$NO_COVERAGE_MUTATIONS${NC} ${YELLOW}(Add tests)${NC}"
    fi
    
    if [ "$TIMEOUT_MUTATIONS" -gt 0 ]; then
        echo -e "⏰ Timeouts:             ${BOLD}$TIMEOUT_MUTATIONS${NC} ${YELLOW}(Performance issue?)${NC}"
    fi
    
    if [ "$MEMORY_ERROR_MUTATIONS" -gt 0 ]; then
        echo -e "💾 Memory Errors:        ${BOLD}$MEMORY_ERROR_MUTATIONS${NC} ${RED}(Configuration issue)${NC}"
    fi
    
    # Overall assessment
    echo -e "\n${BOLD}${BLUE}═══ ASSESSMENT ═══${NC}"
    
    if (( $(echo "$MUTATION_SCORE >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${GREEN}🎉 EXCELLENT! Your tests are doing a great job detecting mutations.${NC}"
    elif (( $(echo "$MUTATION_SCORE >= $(($threshold - 10))" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "${YELLOW}👍 GOOD! Your tests are solid, but there's room for improvement.${NC}"
    else
        echo -e "${RED}⚠️  NEEDS IMPROVEMENT! Consider adding more comprehensive tests.${NC}"
    fi
    
    # Recommendations
    echo -e "\n${BOLD}${BLUE}═══ RECOMMENDATIONS ═══${NC}"
    
    if [ "$SURVIVED_MUTATIONS" -gt 0 ]; then
        echo -e "📋 Focus on the $SURVIVED_MUTATIONS survived mutations in the detailed report"
    fi
    
    if [ "$NO_COVERAGE_MUTATIONS" -gt 0 ]; then
        echo -e "📋 Add tests for $NO_COVERAGE_MUTATIONS uncovered mutations"
    fi
    
    if (( $(echo "$TEST_STRENGTH < $MUTATION_SCORE" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "📋 Improve assertion quality - tests are finding code but not verifying behavior"
    fi
    
    echo -e "📋 Review boundary conditions and edge cases"
    echo -e "📋 Add negative test scenarios"
    echo -e "📋 Verify exception handling paths"
    
    echo -e "\n${BOLD}${BLUE}═══════════════════════════════════════════════════════════════${NC}"
}

format_html_report() {
    local output_file=$1
    local threshold=$2
    
    mkdir -p "$(dirname "$output_file")"
    
    cat > "$output_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>PITest Mutation Testing Report - $PROFILE</title>
    <meta charset="UTF-8">
    <style>
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            margin: 0; padding: 40px; background: #f8f9fa; line-height: 1.6;
        }
        .container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 12px; 
                    box-shadow: 0 4px 6px rgba(0,0,0,0.1); overflow: hidden; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; 
                 padding: 30px; text-align: center; }
        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }
        .header p { margin: 10px 0 0 0; opacity: 0.9; }
        .content { padding: 40px; }
        .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); 
                  gap: 20px; margin: 30px 0; }
        .metric { background: #f8f9fa; padding: 25px; border-radius: 8px; text-align: center; 
                 border: 2px solid #e9ecef; }
        .metric.good { border-color: #28a745; background: #f8fff9; }
        .metric.warning { border-color: #ffc107; background: #fffdf0; }
        .metric.critical { border-color: #dc3545; background: #fff5f5; }
        .metric-value { font-size: 3em; font-weight: bold; margin: 10px 0; }
        .metric-label { color: #6c757d; font-size: 0.9em; text-transform: uppercase; 
                       letter-spacing: 1px; }
        .section { margin: 40px 0; }
        .section h2 { color: #495057; border-bottom: 3px solid #007bff; 
                     padding-bottom: 10px; margin-bottom: 20px; }
        .breakdown { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); 
                    gap: 15px; }
        .breakdown-item { padding: 15px; background: #f8f9fa; border-radius: 6px; text-align: center; }
        .recommendations { background: #e7f3ff; padding: 25px; border-radius: 8px; 
                          border-left: 4px solid #007bff; }
        .recommendations ul { margin: 0; padding-left: 20px; }
        .recommendations li { margin: 8px 0; }
        .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #6c757d; 
                 border-top: 1px solid #e9ecef; }
        @media (max-width: 768px) {
            body { padding: 20px; }
            .content { padding: 20px; }
            .metrics { grid-template-columns: 1fr; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧬 Mutation Testing Report</h1>
            <p>Profile: <strong>$PROFILE</strong> | Generated: $(date)</p>
        </div>
        
        <div class="content">
            <div class="metrics">
                <div class="metric $([ $(echo "$MUTATION_SCORE >= $threshold" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "good" || echo "critical")">
                    <div class="metric-label">Mutation Score</div>
                    <div class="metric-value" style="color: $([ $(echo "$MUTATION_SCORE >= $threshold" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "#28a745" || echo "#dc3545")">${MUTATION_SCORE}%</div>
                </div>
                <div class="metric $([ $(echo "$TEST_STRENGTH >= $threshold" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "good" || echo "warning")">
                    <div class="metric-label">Test Strength</div>
                    <div class="metric-value" style="color: $([ $(echo "$TEST_STRENGTH >= $threshold" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "#28a745" || echo "#ffc107")">${TEST_STRENGTH}%</div>
                </div>
                <div class="metric $([ $(echo "$COVERAGE >= 70" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "good" || echo "warning")">
                    <div class="metric-label">Line Coverage</div>
                    <div class="metric-value" style="color: $([ $(echo "$COVERAGE >= 70" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "#28a745" || echo "#ffc107")">${COVERAGE}%</div>
                </div>
            </div>
            
            <div class="section">
                <h2>📊 Mutation Breakdown</h2>
                <div class="breakdown">
                    <div class="breakdown-item">
                        <strong>$TOTAL_MUTATIONS</strong><br>
                        <small>Total Mutations</small>
                    </div>
                    <div class="breakdown-item" style="background: #f8fff9; border: 1px solid #28a745;">
                        <strong>$KILLED_MUTATIONS</strong><br>
                        <small>Killed (Good)</small>
                    </div>
                    <div class="breakdown-item" style="background: #fff5f5; border: 1px solid #dc3545;">
                        <strong>$SURVIVED_MUTATIONS</strong><br>
                        <small>Survived (Review)</small>
                    </div>
EOF

    if [ "$NO_COVERAGE_MUTATIONS" -gt 0 ]; then
        cat >> "$output_file" << EOF
                    <div class="breakdown-item" style="background: #fffdf0; border: 1px solid #ffc107;">
                        <strong>$NO_COVERAGE_MUTATIONS</strong><br>
                        <small>No Coverage</small>
                    </div>
EOF
    fi

    cat >> "$output_file" << EOF
                </div>
            </div>
            
            <div class="section">
                <h2>🎯 Assessment</h2>
                <div style="padding: 20px; background: $([ $(echo "$MUTATION_SCORE >= $threshold" | bc -l 2>/dev/null || echo "0") -eq 1 ] && echo "#f8fff9; border: 1px solid #28a745" || echo "#fff5f5; border: 1px solid #dc3545"); border-radius: 6px;">
EOF

    if (( $(echo "$MUTATION_SCORE >= $threshold" | bc -l 2>/dev/null || echo "0") )); then
        echo "                    <strong>🎉 EXCELLENT!</strong> Your tests are doing a great job detecting mutations." >> "$output_file"
    elif (( $(echo "$MUTATION_SCORE >= $(($threshold - 10))" | bc -l 2>/dev/null || echo "0") )); then
        echo "                    <strong>👍 GOOD!</strong> Your tests are solid, but there's room for improvement." >> "$output_file"
    else
        echo "                    <strong>⚠️ NEEDS IMPROVEMENT!</strong> Consider adding more comprehensive tests." >> "$output_file"
    fi

    cat >> "$output_file" << EOF
                </div>
            </div>
            
            <div class="section">
                <div class="recommendations">
                    <h2 style="margin-top: 0; border: none; color: #0056b3;">💡 Recommendations</h2>
                    <ul>
EOF

    if [ "$SURVIVED_MUTATIONS" -gt 0 ]; then
        echo "                        <li>Focus on the $SURVIVED_MUTATIONS survived mutations in the detailed PITest report</li>" >> "$output_file"
    fi
    
    if [ "$NO_COVERAGE_MUTATIONS" -gt 0 ]; then
        echo "                        <li>Add tests for $NO_COVERAGE_MUTATIONS uncovered mutations</li>" >> "$output_file"
    fi

    cat >> "$output_file" << EOF
                        <li>Review boundary conditions and edge cases</li>
                        <li>Add negative test scenarios for error handling</li>
                        <li>Verify exception handling paths are tested</li>
                        <li>Consider parametrized tests for comprehensive coverage</li>
                    </ul>
                </div>
            </div>
        </div>
        
        <div class="footer">
            <p>Generated by PITest Mutation Testing Report Generator</p>
            <p>For detailed mutation information, review the main PITest HTML reports</p>
        </div>
    </div>
</body>
</html>
EOF

    success "HTML report generated: $output_file"
}

format_csv_report() {
    local output_file=$1
    
    mkdir -p "$(dirname "$output_file")"
    
    echo "Profile,MutationScore,TestStrength,Coverage,TotalMutations,KilledMutations,SurvivedMutations,NoCoverageMutations,TimeoutMutations,MemoryErrorMutations,Timestamp" > "$output_file"
    echo "$PROFILE,$MUTATION_SCORE,$TEST_STRENGTH,$COVERAGE,$TOTAL_MUTATIONS,$KILLED_MUTATIONS,$SURVIVED_MUTATIONS,$NO_COVERAGE_MUTATIONS,$TIMEOUT_MUTATIONS,$MEMORY_ERROR_MUTATIONS,$(date -Iseconds)" >> "$output_file"
    
    success "CSV report generated: $output_file"
}

format_json_report() {
    local output_file=$1
    local threshold=$2
    
    mkdir -p "$(dirname "$output_file")"
    
    local status="PASS"
    if (( $(echo "$MUTATION_SCORE < $threshold" | bc -l 2>/dev/null || echo "1") )); then
        status="FAIL"
    fi
    
    cat > "$output_file" << EOF
{
  "profile": "$PROFILE",
  "timestamp": "$(date -Iseconds)",
  "threshold": $threshold,
  "status": "$status",
  "metrics": {
    "mutationScore": $MUTATION_SCORE,
    "testStrength": $TEST_STRENGTH,
    "coverage": $COVERAGE
  },
  "mutations": {
    "total": $TOTAL_MUTATIONS,
    "killed": $KILLED_MUTATIONS,
    "survived": $SURVIVED_MUTATIONS,
    "noCoverage": $NO_COVERAGE_MUTATIONS,
    "timeout": $TIMEOUT_MUTATIONS,
    "memoryError": $MEMORY_ERROR_MUTATIONS
  },
  "recommendations": [
EOF

    local first=true
    if [ "$SURVIVED_MUTATIONS" -gt 0 ]; then
        echo "    \"Focus on the $SURVIVED_MUTATIONS survived mutations\"" >> "$output_file"
        first=false
    fi
    
    if [ "$NO_COVERAGE_MUTATIONS" -gt 0 ]; then
        [ "$first" = false ] && echo "," >> "$output_file"
        echo "    \"Add tests for $NO_COVERAGE_MUTATIONS uncovered mutations\"" >> "$output_file"
        first=false
    fi
    
    [ "$first" = false ] && echo "," >> "$output_file"
    echo "    \"Review boundary conditions and edge cases\"" >> "$output_file"

    cat >> "$output_file" << EOF
  ]
}
EOF

    success "JSON report generated: $output_file"
}

find_reports() {
    local profile=$1
    
    if [ -n "$profile" ]; then
        # Look for specific profile
        find "$REPORTS_BASE_DIR" -name "mutations.xml" -path "*/$profile/*" 2>/dev/null | head -1
    else
        # Find all available reports
        find "$REPORTS_BASE_DIR" -name "mutations.xml" 2>/dev/null
    fi
}

generate_report() {
    local profile=$1
    local format=$2
    local threshold=$3
    local output_dir=$4
    
    local xml_file
    if [ -n "$profile" ]; then
        xml_file=$(find_reports "$profile")
        if [ -z "$xml_file" ]; then
            error "No mutation test results found for profile: $profile"
            echo "Available profiles:"
            find "$REPORTS_BASE_DIR" -name "mutations.xml" 2>/dev/null | sed 's|.*/pitest/||' | sed 's|/mutations.xml||' | sort -u | sed 's/^/  - /'
            return 1
        fi
    else
        xml_file=$(find_reports | head -1)
        if [ -z "$xml_file" ]; then
            error "No mutation test results found in $REPORTS_BASE_DIR"
            echo "Run mutation tests first: ./gradlew pitest"
            return 1
        fi
        profile=$(echo "$xml_file" | sed 's|.*/pitest/||' | sed 's|/mutations.xml||')
        [ -z "$profile" ] && profile="default"
    fi
    
    log "Processing mutation test results for profile: $profile"
    
    if ! parse_xml_report "$xml_file" "$profile"; then
        return 1
    fi
    
    case "$format" in
        "console")
            format_console_report "$threshold"
            ;;
        "html")
            local output_file="$output_dir/mutation-report-$profile-$TIMESTAMP.html"
            format_html_report "$output_file" "$threshold"
            ;;
        "csv")
            local output_file="$output_dir/mutation-report-$profile-$TIMESTAMP.csv"
            format_csv_report "$output_file"
            ;;
        "json")
            local output_file="$output_dir/mutation-report-$profile-$TIMESTAMP.json"
            format_json_report "$output_file" "$threshold"
            ;;
        *)
            error "Unknown format: $format"
            return 1
            ;;
    esac
}

# Main execution
main() {
    local profile=""
    local format="console"
    local threshold=80
    local output_dir="$OUTPUT_DIR"
    local all_profiles=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --profile)
                profile="$2"
                shift 2
                ;;
            --all-profiles)
                all_profiles=true
                shift
                ;;
            --format)
                format="$2"
                shift 2
                ;;
            --threshold)
                threshold="$2"
                shift 2
                ;;
            --output-dir)
                output_dir="$2"
                shift 2
                ;;
            --help|-h)
                print_usage
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done
    
    # Ensure output directory exists
    mkdir -p "$output_dir"
    
    if [ "$all_profiles" = true ]; then
        log "Generating reports for all available profiles..."
        local profiles
        profiles=$(find "$REPORTS_BASE_DIR" -name "mutations.xml" 2>/dev/null | sed 's|.*/pitest/||' | sed 's|/mutations.xml||' | sort -u)
        
        if [ -z "$profiles" ]; then
            error "No mutation test results found"
            exit 1
        fi
        
        for prof in $profiles; do
            echo -e "\n${CYAN}Processing profile: $prof${NC}"
            generate_report "$prof" "$format" "$threshold" "$output_dir"
        done
    else
        generate_report "$profile" "$format" "$threshold" "$output_dir"
    fi
}

# Check for required tools
if ! command -v bc >/dev/null 2>&1; then
    warn "bc command not found - some calculations may not work properly"
fi

# Execute main function
main "$@"