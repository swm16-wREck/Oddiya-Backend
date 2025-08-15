#!/bin/bash

# Database Migration Script for Oddiya Phase 2 PostgreSQL
# This script handles Flyway-based database migrations for PostgreSQL deployment

set -euo pipefail

# Configuration
ENVIRONMENT=${1:-dev}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIGRATION_TIMEOUT=300
DRY_RUN=${DRY_RUN:-false}

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
    exit 1
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# Get database connection info from AWS Secrets Manager
get_database_config() {
    log "Retrieving database configuration for environment: $ENVIRONMENT"
    
    local secret_name="oddiya-${ENVIRONMENT}-db-connection"
    local secret_json
    
    if ! secret_json=$(aws secretsmanager get-secret-value \
        --secret-id "$secret_name" \
        --region ap-northeast-2 \
        --query SecretString \
        --output text 2>/dev/null); then
        error "Failed to retrieve database connection secret: $secret_name"
    fi
    
    # Parse connection details
    DB_HOST=$(echo "$secret_json" | jq -r '.host')
    DB_PORT=$(echo "$secret_json" | jq -r '.port // "5432"')
    DB_NAME=$(echo "$secret_json" | jq -r '.database // "oddiya"')
    DB_USER=$(echo "$secret_json" | jq -r '.username')
    DB_PASSWORD=$(echo "$secret_json" | jq -r '.password')
    
    # Validate connection details
    if [[ -z "$DB_HOST" || "$DB_HOST" == "null" ]]; then
        error "Invalid database host in secret"
    fi
    
    if [[ -z "$DB_USER" || "$DB_USER" == "null" ]]; then
        error "Invalid database username in secret"
    fi
    
    success "Database configuration retrieved successfully"
}

# Test database connectivity
test_database_connection() {
    log "Testing database connectivity..."
    
    if ! PGPASSWORD="$DB_PASSWORD" pg_isready \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t 30 >/dev/null 2>&1; then
        error "Cannot connect to database. Please check connection details and network access."
    fi
    
    success "Database connectivity test passed"
}

# Backup database before migration (production only)
backup_database() {
    if [[ "$ENVIRONMENT" != "prod" ]]; then
        log "Skipping backup for non-production environment"
        return 0
    fi
    
    log "Creating database backup before migration..."
    
    local backup_dir="/tmp/db-backup-$(date +%Y%m%d-%H%M%S)"
    local backup_file="${backup_dir}/oddiya-pre-migration.sql"
    
    mkdir -p "$backup_dir"
    
    if ! PGPASSWORD="$DB_PASSWORD" pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --clean \
        --if-exists \
        --verbose \
        -f "$backup_file" 2>/dev/null; then
        warn "Database backup failed, but continuing with migration"
        return 0
    fi
    
    # Compress backup
    if command -v gzip >/dev/null 2>&1; then
        gzip "$backup_file"
        backup_file="${backup_file}.gz"
    fi
    
    # Upload backup to S3 if AWS CLI is available
    local s3_bucket="oddiya-${ENVIRONMENT}-backups"
    if aws s3 ls "s3://${s3_bucket}" >/dev/null 2>&1; then
        if aws s3 cp "$backup_file" "s3://${s3_bucket}/db-migrations/"; then
            success "Database backup uploaded to S3: s3://${s3_bucket}/db-migrations/"
        else
            warn "Failed to upload backup to S3, but backup exists locally: $backup_file"
        fi
    fi
    
    success "Database backup completed: $backup_file"
}

# Check current migration status
check_migration_status() {
    log "Checking current migration status..."
    
    # Create schema_version table if it doesn't exist (Flyway standard)
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "CREATE TABLE IF NOT EXISTS flyway_schema_history (
            installed_rank INTEGER NOT NULL,
            version VARCHAR(50),
            description VARCHAR(200) NOT NULL,
            type VARCHAR(20) NOT NULL,
            script VARCHAR(1000) NOT NULL,
            checksum INTEGER,
            installed_by VARCHAR(100) NOT NULL,
            installed_on TIMESTAMP NOT NULL DEFAULT now(),
            execution_time INTEGER NOT NULL,
            success BOOLEAN NOT NULL,
            PRIMARY KEY (installed_rank)
        );" >/dev/null 2>&1
    
    # Get current migration status
    local current_version
    current_version=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT COALESCE(MAX(version), '0') FROM flyway_schema_history WHERE success = true;" 2>/dev/null | xargs)
    
    log "Current database version: $current_version"
    
    # Get pending migrations
    local migration_count=0
    if [[ -d "$PROJECT_ROOT/src/main/resources/db/migration" ]]; then
        migration_count=$(find "$PROJECT_ROOT/src/main/resources/db/migration" -name "V*.sql" | wc -l)
    fi
    
    log "Available migration scripts: $migration_count"
    
    if [[ $migration_count -eq 0 ]]; then
        warn "No migration scripts found in src/main/resources/db/migration"
        return 1
    fi
    
    success "Migration status check completed"
}

# Run database migrations using embedded Gradle task
run_migrations() {
    log "Running database migrations..."
    
    cd "$PROJECT_ROOT"
    
    # Set environment variables for Gradle
    export SPRING_PROFILES_ACTIVE="postgresql,$ENVIRONMENT"
    export DATABASE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
    export DATABASE_USERNAME="$DB_USER"
    export DATABASE_PASSWORD="$DB_PASSWORD"
    
    # Create Flyway configuration
    local flyway_conf="/tmp/flyway-${ENVIRONMENT}.conf"
    cat > "$flyway_conf" << EOF
flyway.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
flyway.user=${DB_USER}
flyway.password=${DB_PASSWORD}
flyway.locations=filesystem:src/main/resources/db/migration
flyway.baselineOnMigrate=true
flyway.validateOnMigrate=true
flyway.outOfOrder=false
flyway.table=flyway_schema_history
flyway.sqlMigrationPrefix=V
flyway.sqlMigrationSeparator=__
flyway.sqlMigrationSuffixes=.sql
EOF

    if [[ "$DRY_RUN" == "true" ]]; then
        log "DRY RUN: Would run migrations with Spring Boot"
        log "Configuration: $flyway_conf"
        cat "$flyway_conf"
        return 0
    fi
    
    # Run migrations using Spring Boot
    local migration_start_time=$(date +%s)
    
    if timeout $MIGRATION_TIMEOUT ./gradlew flywayMigrate -PflywayConfigFile="$flyway_conf" --no-daemon; then
        local migration_end_time=$(date +%s)
        local migration_duration=$((migration_end_time - migration_start_time))
        success "Database migrations completed successfully in ${migration_duration} seconds"
    else
        local exit_code=$?
        error "Database migrations failed with exit code: $exit_code"
    fi
    
    # Clean up
    rm -f "$flyway_conf"
}

# Validate migration results
validate_migrations() {
    log "Validating migration results..."
    
    # Check for failed migrations
    local failed_migrations
    failed_migrations=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null | xargs)
    
    if [[ $failed_migrations -gt 0 ]]; then
        error "Found $failed_migrations failed migrations. Please review and fix before proceeding."
    fi
    
    # Check essential tables exist
    local essential_tables=("users" "places" "travel_plans" "itinerary_items")
    for table in "${essential_tables[@]}"; do
        local table_exists
        table_exists=$(PGPASSWORD="$DB_PASSWORD" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$DB_NAME" \
            -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '$table';" 2>/dev/null | xargs)
        
        if [[ $table_exists -eq 0 ]]; then
            warn "Essential table '$table' not found"
        else
            log "✓ Table '$table' exists"
        fi
    done
    
    # Check PostGIS extension if spatial functionality is expected
    local postgis_version
    postgis_version=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -c "SELECT PostGIS_Version();" 2>/dev/null || echo "Not installed")
    
    if [[ "$postgis_version" != "Not installed" ]]; then
        success "✓ PostGIS extension available: $postgis_version"
    else
        warn "PostGIS extension not found (spatial features may not work)"
    fi
    
    success "Migration validation completed"
}

# Send migration notification
send_notification() {
    local status="$1"
    local message="$2"
    
    local sns_topic_arn="${SNS_TOPIC_ARN:-}"
    if [[ -z "$sns_topic_arn" ]]; then
        log "No SNS topic configured, skipping notification"
        return 0
    fi
    
    local subject="[Oddiya] Database Migration - $ENVIRONMENT - $status"
    local full_message="Database migration completed for environment: $ENVIRONMENT

Status: $status
Host: $DB_HOST
Database: $DB_NAME
Time: $(date)

$message

Migration Details:
- Environment: $ENVIRONMENT
- Database Host: $DB_HOST:$DB_PORT
- Database Name: $DB_NAME
- Migration User: $DB_USER
"
    
    if aws sns publish \
        --topic-arn "$sns_topic_arn" \
        --subject "$subject" \
        --message "$full_message" >/dev/null 2>&1; then
        success "Migration notification sent"
    else
        warn "Failed to send migration notification"
    fi
}

# Main execution
main() {
    log "Starting database migration for environment: $ENVIRONMENT"
    
    # Check prerequisites
    local missing_tools=()
    command -v aws >/dev/null 2>&1 || missing_tools+=("aws")
    command -v psql >/dev/null 2>&1 || missing_tools+=("psql")
    command -v pg_isready >/dev/null 2>&1 || missing_tools+=("pg_isready")
    command -v jq >/dev/null 2>&1 || missing_tools+=("jq")
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        error "Missing required tools: ${missing_tools[*]}"
    fi
    
    # Validate project structure
    if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
        error "Gradle wrapper not found. Please run this script from the project root."
    fi
    
    # Execute migration steps
    get_database_config
    test_database_connection
    backup_database
    check_migration_status
    run_migrations
    validate_migrations
    
    success "Database migration completed successfully for $ENVIRONMENT environment"
    send_notification "SUCCESS" "All migration steps completed successfully"
}

# Show usage if requested
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    cat << EOF
Usage: $0 [ENVIRONMENT] [OPTIONS]

Database migration script for Oddiya Phase 2 PostgreSQL.

ENVIRONMENT:
  dev      - Development environment (default)
  staging  - Staging environment  
  prod     - Production environment

Environment Variables:
  DRY_RUN           - Set to 'true' to simulate migration without executing (default: false)
  SNS_TOPIC_ARN    - SNS topic for migration notifications (optional)

Examples:
  $0                           # Migrate dev environment
  $0 prod                      # Migrate production environment
  DRY_RUN=true $0 prod        # Dry run for production environment

Prerequisites:
  - AWS CLI configured with appropriate credentials
  - PostgreSQL client tools (psql, pg_isready) installed
  - jq command-line JSON processor installed
  - Network access to target database
  - Valid AWS Secrets Manager configuration for database credentials

Migration Process:
  1. Retrieve database credentials from AWS Secrets Manager
  2. Test database connectivity
  3. Create database backup (production only)
  4. Check current migration status
  5. Run Flyway migrations using Gradle
  6. Validate migration results
  7. Send notification (if SNS topic configured)

EOF
    exit 0
fi

# Run main function
main "$@"