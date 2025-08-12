#!/bin/bash

# Profile-based Bean Switching Demo Script
# This script demonstrates how to run the application with different profiles

set -e

echo "======================================"
echo "Profile-based Bean Switching Demo"
echo "======================================"

function run_with_profile() {
    local profile=$1
    local description=$2
    
    echo ""
    echo "--------------------------------------"
    echo "Running with profile: $profile"
    echo "Description: $description"
    echo "--------------------------------------"
    
    # Set the profile environment variable
    export SPRING_PROFILES_ACTIVE=$profile
    
    # Show which profile is active
    echo "Active profile: $SPRING_PROFILES_ACTIVE"
    
    # Run the application for a few seconds to show initialization
    echo "Starting application..."
    timeout 10s ./gradlew bootRun --quiet || true
    
    echo "Profile $profile demo completed."
}

function show_profile_info() {
    echo ""
    echo "Available Profiles:"
    echo "==================="
    echo ""
    echo "local     - Local development with PostgreSQL + Local storage + Local messaging"
    echo "test      - Testing with H2 in-memory + Local storage + Local messaging"
    echo "dynamodb  - AWS DynamoDB + S3 storage + SQS messaging"
    echo "aws       - AWS profile (can be combined with others)"
    echo "docker    - Docker environment with PostgreSQL"
    echo ""
    echo "Profile Combinations:"
    echo "===================="
    echo ""
    echo "JPA Profiles:     local, test, docker"
    echo "NoSQL Profiles:   dynamodb"
    echo "AWS Profiles:     aws, dynamodb"
    echo "Local Profiles:   local, test"
    echo ""
}

function show_configuration_matrix() {
    echo ""
    echo "Configuration Matrix:"
    echo "===================="
    echo ""
    echo "Profile    | Storage | DataSource     | Storage Service | Messaging Service"
    echo "-----------|---------|----------------|-----------------|------------------"
    echo "local      | JPA     | PostgreSQL     | Local           | Local"
    echo "test       | JPA     | H2 Memory      | Local           | Local"
    echo "docker     | JPA     | PostgreSQL     | Local           | Local"
    echo "dynamodb   | DynamoDB| H2 Dummy       | S3              | SQS"
    echo "aws        | JPA     | PostgreSQL     | S3              | SQS"
    echo ""
}

function run_migration_demo() {
    echo ""
    echo "--------------------------------------"
    echo "Data Migration Demo"
    echo "--------------------------------------"
    echo ""
    echo "1. Starting with JPA (test profile)..."
    export SPRING_PROFILES_ACTIVE=test
    echo "   - Creates sample data in H2 database"
    echo "   - Data stored in relational tables"
    
    echo ""
    echo "2. Switching to DynamoDB profile..."
    export SPRING_PROFILES_ACTIVE=dynamodb
    echo "   - Uses DataMigrationService to migrate data"
    echo "   - Data transferred to DynamoDB tables"
    echo "   - Application seamlessly switches storage backends"
    
    echo ""
    echo "3. Migration validation..."
    echo "   - Validates data consistency between stores"
    echo "   - Provides migration report and metrics"
    echo "   - Supports rollback if needed"
    
    echo ""
    echo "Migration demo completed (simulated)."
}

function run_feature_demo() {
    echo ""
    echo "--------------------------------------"
    echo "Feature Support Demo"
    echo "--------------------------------------"
    echo ""
    echo "JPA Profile Features:"
    echo "  ✓ Complex Queries (JOINs, subqueries)"
    echo "  ✓ ACID Transactions"
    echo "  ✓ Full-text Search"
    echo "  ✓ SQL Compliance"
    echo ""
    echo "DynamoDB Profile Features:"
    echo "  ✓ High Scalability"
    echo "  ✓ Auto-scaling"
    echo "  ✓ Global Secondary Indexes"
    echo "  ✓ Eventual Consistency"
    echo ""
    echo "Feature detection is automatic based on active profile."
}

# Main menu
if [ "$1" == "--info" ]; then
    show_profile_info
    show_configuration_matrix
    exit 0
elif [ "$1" == "--migration" ]; then
    run_migration_demo
    exit 0
elif [ "$1" == "--features" ]; then
    run_feature_demo
    exit 0
elif [ "$1" == "--all" ]; then
    echo "Running complete demo..."
    show_profile_info
    show_configuration_matrix
    
    # Demo each profile (quick startup only)
    run_with_profile "test" "Testing profile with H2 and local services"
    run_with_profile "local" "Local development profile with PostgreSQL"
    
    run_migration_demo
    run_feature_demo
    
    echo ""
    echo "======================================"
    echo "Demo completed!"
    echo "======================================"
    exit 0
elif [ "$1" != "" ]; then
    # Run with specific profile
    run_with_profile "$1" "User-specified profile"
    exit 0
else
    echo ""
    echo "Usage: $0 [profile|option]"
    echo ""
    echo "Profiles:"
    echo "  local      Run with local profile"
    echo "  test       Run with test profile"
    echo "  dynamodb   Run with DynamoDB profile"
    echo "  aws        Run with AWS profile"
    echo "  docker     Run with Docker profile"
    echo ""
    echo "Options:"
    echo "  --info       Show profile information and configuration matrix"
    echo "  --migration  Demonstrate data migration between profiles"
    echo "  --features   Show feature support by profile"
    echo "  --all        Run complete demo with all profiles and features"
    echo ""
    echo "Examples:"
    echo "  $0 test                 # Run with test profile"
    echo "  $0 dynamodb            # Run with DynamoDB profile"
    echo "  $0 --info              # Show configuration information"
    echo "  $0 --all               # Run complete demo"
    echo ""
fi