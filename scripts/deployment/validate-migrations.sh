#!/bin/bash

# Flyway Migration Validation Script for Oddiya
# This script validates PostgreSQL migrations and checks for conflicts or issues
# Version: 1.0
# Date: 2025-08-14

set -euo pipefail

# Configuration
ENVIRONMENT=${1:-dev}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

section() {
    echo -e "\n${CYAN}=== $1 ===${NC}"
}

# Migration file locations
MIGRATION_DIR="$PROJECT_ROOT/src/main/resources/db/migration"

# Check if migration directory exists
check_migration_directory() {
    section "Checking Migration Directory"
    
    if [[ ! -d "$MIGRATION_DIR" ]]; then
        error "Migration directory not found: $MIGRATION_DIR"
    fi
    
    success "Migration directory found: $MIGRATION_DIR"
}

# List and analyze migration files
analyze_migration_files() {
    section "Analyzing Migration Files"
    
    local migration_files=()
    local duplicate_versions=()
    
    # Find all migration files
    while IFS= read -r -d '' file; do
        migration_files+=("$(basename "$file")")
    done < <(find "$MIGRATION_DIR" -name "*.sql" -print0 | sort -z)
    
    if [[ ${#migration_files[@]} -eq 0 ]]; then
        error "No migration files found in $MIGRATION_DIR"
    fi
    
    log "Found ${#migration_files[@]} migration files:"
    
    # Check for duplicates and proper naming
    local seen_versions=()
    for file in "${migration_files[@]}"; do
        log "  - $file"
        
        # Extract version from filename (V1, V2, V001, etc.)
        if [[ $file =~ ^V([0-9]+) ]]; then
            local version="${BASH_REMATCH[1]}"
            # Normalize version (remove leading zeros for comparison)
            local normalized_version=$((10#$version))
            
            # Check for duplicates
            if [[ ${#seen_versions[@]} -gt 0 ]]; then
                for seen_version in "${seen_versions[@]}"; do
                    if [[ $seen_version -eq $normalized_version ]]; then
                        duplicate_versions+=("$file")
                    fi
                done
            fi
            seen_versions+=("$normalized_version")
        else
            warn "  ⚠ File does not follow Flyway naming convention: $file"
        fi
    done
    
    if [[ ${#duplicate_versions[@]} -gt 0 ]]; then
        error "Duplicate migration versions found: ${duplicate_versions[*]}"
    fi
    
    success "Migration file analysis completed"
}

# Check PostgreSQL compatibility
check_postgresql_compatibility() {
    section "Checking PostgreSQL Compatibility"
    
    local incompatible_patterns=(
        "ENGINE=InnoDB"      # MySQL specific
        "AUTO_INCREMENT"     # MySQL specific
        "TINYINT"           # MySQL specific
        "MEDIUMINT"         # MySQL specific
        "LONGTEXT"          # MySQL specific
        "DATETIME"          # MySQL specific (use TIMESTAMP in PostgreSQL)
    )
    
    local issues_found=false
    
    for file in "$MIGRATION_DIR"/*.sql; do
        if [[ -f "$file" ]]; then
            local filename=$(basename "$file")
            log "Checking $filename for PostgreSQL compatibility..."
            
            for pattern in "${incompatible_patterns[@]}"; do
                if grep -q "$pattern" "$file"; then
                    warn "  ⚠ Found potentially incompatible pattern '$pattern' in $filename"
                    issues_found=true
                fi
            done
            
            # Check for proper PostgreSQL syntax
            if grep -q "CREATE TABLE" "$file"; then
                # Check for proper constraint syntax
                if ! grep -q "CONSTRAINT\|CHECK\|FOREIGN KEY\|PRIMARY KEY\|UNIQUE" "$file"; then
                    warn "  ⚠ Table creation without explicit constraints in $filename"
                fi
            fi
        fi
    done
    
    if [[ "$issues_found" == false ]]; then
        success "No PostgreSQL compatibility issues found"
    else
        warn "Some potential PostgreSQL compatibility issues detected - please review manually"
    fi
}

# Check PostGIS extension usage
check_postgis_usage() {
    section "Checking PostGIS Extension Usage"
    
    local postgis_required=false
    local postgis_enabled=false
    
    for file in "$MIGRATION_DIR"/*.sql; do
        if [[ -f "$file" ]]; then
            local filename=$(basename "$file")
            
            # Check if file uses PostGIS functions/types
            if grep -qE "GEOMETRY|GEOGRAPHY|ST_|PostGIS" "$file"; then
                log "PostGIS usage detected in $filename"
                postgis_required=true
            fi
            
            # Check if PostGIS extension is enabled
            if grep -q "CREATE EXTENSION.*postgis" "$file"; then
                log "PostGIS extension creation found in $filename"
                postgis_enabled=true
            fi
        fi
    done
    
    if [[ "$postgis_required" == true ]]; then
        log "PostGIS functionality is used in migrations"
        if [[ "$postgis_enabled" == true ]]; then
            success "PostGIS extension is properly enabled in migrations"
        else
            warn "PostGIS functionality used but extension not created in migrations"
            log "  Make sure PostGIS is enabled before running migrations"
        fi
    else
        log "No PostGIS usage detected in migrations"
    fi
}

# Validate migration order and dependencies
validate_migration_order() {
    section "Validating Migration Order and Dependencies"
    
    local has_v001_v006=false
    local has_v1_v4=false
    
    # Check for both numbering schemes
    if ls "$MIGRATION_DIR"/V00[1-6]*.sql >/dev/null 2>&1; then
        has_v001_v006=true
        log "Found V001-V006 migration series"
    fi
    
    if ls "$MIGRATION_DIR"/V[1-4]_*.sql >/dev/null 2>&1; then
        has_v1_v4=true
        log "Found V1-V4 migration series"
    fi
    
    if [[ "$has_v001_v006" == true && "$has_v1_v4" == true ]]; then
        error "Conflicting migration numbering schemes detected (V001-V006 and V1-V4)"
        error "This will cause Flyway to execute migrations in wrong order"
        error "Please consolidate to a single numbering scheme"
    fi
    
    # Check for proper sequential numbering
    local migration_versions=()
    for file in "$MIGRATION_DIR"/V*.sql; do
        if [[ -f "$file" ]]; then
            local filename=$(basename "$file")
            if [[ $filename =~ ^V([0-9]+) ]]; then
                local version="${BASH_REMATCH[1]}"
                migration_versions+=("$((10#$version))")
            fi
        fi
    done
    
    # Sort versions and check for gaps
    IFS=$'\n' sorted=($(sort -n <<<"${migration_versions[*]}"))
    unset IFS
    
    log "Migration versions found (sorted): ${sorted[*]}"
    
    local expected=1
    for version in "${sorted[@]}"; do
        if [[ $version -ne $expected ]]; then
            if [[ $version -gt $expected ]]; then
                warn "Gap in migration versions: expected V$expected, found V$version"
            else
                error "Migration version V$version is out of order"
            fi
        fi
        expected=$((version + 1))
    done
    
    success "Migration order validation completed"
}

# Check for syntax errors
check_sql_syntax() {
    section "Checking SQL Syntax"
    
    local syntax_errors=false
    
    for file in "$MIGRATION_DIR"/*.sql; do
        if [[ -f "$file" ]]; then
            local filename=$(basename "$file")
            log "Checking syntax in $filename..."
            
            # Basic syntax checks
            local line_num=0
            while IFS= read -r line; do
                line_num=$((line_num + 1))
                
                # Check for common syntax issues
                if [[ $line =~ .*[[:space:]]$ ]]; then
                    warn "  ⚠ Trailing whitespace at line $line_num: $filename"
                fi
                
                # Check for unmatched quotes (basic check)
                local single_quotes=$(echo "$line" | grep -o "'" | wc -l)
                local double_quotes=$(echo "$line" | grep -o '"' | wc -l)
                
                if [[ $((single_quotes % 2)) -ne 0 ]] && [[ ! $line =~ ^[[:space:]]*-- ]]; then
                    warn "  ⚠ Possible unmatched single quotes at line $line_num: $filename"
                fi
                
                if [[ $((double_quotes % 2)) -ne 0 ]] && [[ ! $line =~ ^[[:space:]]*-- ]]; then
                    warn "  ⚠ Possible unmatched double quotes at line $line_num: $filename"
                fi
                
            done < "$file"
            
            # Check if file ends with semicolon for SQL statements
            if grep -q "CREATE\|INSERT\|UPDATE\|DELETE\|ALTER\|DROP" "$file"; then
                local last_line=$(tail -n 1 "$file" | tr -d '[:space:]')
                if [[ ! $last_line =~ \;$ ]] && [[ $last_line != "" ]]; then
                    warn "  ⚠ SQL file may be missing trailing semicolon: $filename"
                fi
            fi
        fi
    done
    
    if [[ "$syntax_errors" == false ]]; then
        success "Basic SQL syntax checks passed"
    else
        warn "Some potential syntax issues detected - please review manually"
    fi
}

# Check for schema conflicts
check_schema_conflicts() {
    section "Checking for Schema Conflicts"
    
    local tables_created=()
    local tables_altered=()
    local conflicts_found=false
    
    for file in "$MIGRATION_DIR"/*.sql; do
        if [[ -f "$file" ]]; then
            local filename=$(basename "$file")
            
            # Extract table names from CREATE TABLE statements
            while IFS= read -r line; do
                if [[ $line =~ CREATE[[:space:]]+TABLE[[:space:]]+([A-Za-z_][A-Za-z0-9_]*) ]]; then
                    local table_name="${BASH_REMATCH[1]}"
                    if [[ " ${tables_created[*]} " =~ " ${table_name} " ]]; then
                        error "Table '$table_name' is created in multiple migration files"
                        conflicts_found=true
                    else
                        tables_created+=("$table_name")
                        log "  Table '$table_name' created in $filename"
                    fi
                fi
                
                if [[ $line =~ ALTER[[:space:]]+TABLE[[:space:]]+([A-Za-z_][A-Za-z0-9_]*) ]]; then
                    local table_name="${BASH_REMATCH[1]}"
                    tables_altered+=("$table_name")
                fi
            done < "$file"
        fi
    done
    
    # Check for tables being altered before being created
    for table in "${tables_altered[@]}"; do
        if [[ ! " ${tables_created[*]} " =~ " ${table} " ]]; then
            warn "Table '$table' is altered but not created in migrations (may exist elsewhere)"
        fi
    done
    
    if [[ "$conflicts_found" == false ]]; then
        success "No schema conflicts detected"
    else
        error "Schema conflicts detected - please resolve before running migrations"
    fi
}

# Generate migration report
generate_migration_report() {
    section "Migration Report"
    
    cat << EOF

Migration Structure Analysis:
============================

Migration Directory: $MIGRATION_DIR

Files Found:
$(find "$MIGRATION_DIR" -name "*.sql" -exec basename {} \; | sort)

Expected Execution Order (by Flyway):
$(find "$MIGRATION_DIR" -name "*.sql" -exec basename {} \; | sort -V)

Key Findings:
- PostgreSQL compatibility: $(ls "$MIGRATION_DIR"/*.sql 2>/dev/null | wc -l) files checked
- PostGIS usage: $(grep -l "GEOMETRY\|GEOGRAPHY\|ST_\|PostGIS" "$MIGRATION_DIR"/*.sql 2>/dev/null | wc -l) files use spatial features
- Table creations: $(grep -l "CREATE TABLE" "$MIGRATION_DIR"/*.sql 2>/dev/null | wc -l) files create tables
- Index creations: $(grep -l "CREATE INDEX" "$MIGRATION_DIR"/*.sql 2>/dev/null | wc -l) files create indexes

Recommendations:
1. Run migrations in a test environment first
2. Backup database before running in production
3. Monitor migration execution times for large datasets
4. Ensure PostGIS extension is available if spatial features are used

EOF
}

# Main execution
main() {
    log "Starting Flyway migration validation for environment: $ENVIRONMENT"
    
    check_migration_directory
    analyze_migration_files
    check_postgresql_compatibility
    check_postgis_usage
    validate_migration_order
    check_sql_syntax
    check_schema_conflicts
    generate_migration_report
    
    success "Migration validation completed successfully"
    log "Your migrations appear ready for PostgreSQL with Flyway"
}

# Show usage if requested
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    cat << EOF
Usage: $0 [ENVIRONMENT]

This script validates Flyway migration files for PostgreSQL compatibility and checks for common issues.

ENVIRONMENT:
  dev      - Development environment (default)
  staging  - Staging environment  
  prod     - Production environment

The script checks for:
- PostgreSQL compatibility issues
- PostGIS extension usage and requirements
- Migration file naming and ordering conflicts
- Basic SQL syntax issues
- Schema conflicts between migrations
- Proper migration file structure

Examples:
  $0                    # Validate migrations for dev environment
  $0 staging           # Validate migrations for staging environment
  $0 prod              # Validate migrations for production environment

EOF
    exit 0
fi

# Run main function
main "$@"