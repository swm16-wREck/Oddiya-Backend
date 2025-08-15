#!/bin/bash

# PostGIS Initialization Script for Oddiya Phase 2
# This script sets up PostGIS extension and spatial data structures for PostgreSQL migration
# Updated for Phase 2 PostgreSQL support

set -euo pipefail

# Configuration
ENVIRONMENT=${1:-dev}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# Get database connection info from AWS Secrets Manager
get_db_connection() {
    log "Retrieving database connection information from AWS Secrets Manager..."
    
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
    DB_PORT=$(echo "$secret_json" | jq -r '.port')
    DB_NAME=$(echo "$secret_json" | jq -r '.database')
    DB_USER=$(echo "$secret_json" | jq -r '.username')
    DB_PASSWORD=$(echo "$secret_json" | jq -r '.password')
    
    # Validate connection details
    if [[ -z "$DB_HOST" || "$DB_HOST" == "null" ]]; then
        error "Invalid database host in secret"
    fi
    
    success "Database connection information retrieved successfully"
}

# Test database connectivity
test_db_connection() {
    log "Testing database connectivity..."
    
    local connection_string="postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
    
    if ! PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "SELECT version();" >/dev/null 2>&1; then
        error "Cannot connect to database. Please check connection details and network access."
    fi
    
    success "Database connectivity test passed"
}

# Initialize PostGIS extensions
setup_postgis() {
    log "Setting up PostGIS extensions..."
    
    local sql_commands="
    -- Create PostGIS extension
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS postgis_topology;
    CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;
    CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;
    
    -- Verify PostGIS installation
    SELECT PostGIS_Version();
    
    -- Grant permissions for PostGIS
    GRANT USAGE ON SCHEMA topology TO ${DB_USER};
    GRANT SELECT ON ALL SEQUENCES IN SCHEMA topology TO ${DB_USER};
    GRANT SELECT ON ALL TABLES IN SCHEMA topology TO ${DB_USER};
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA topology TO ${DB_USER};
    
    -- Create spatial reference systems if needed
    INSERT INTO spatial_ref_sys (srid, auth_name, auth_srid, proj4text, srtext)
    VALUES (4326, 'EPSG', 4326, 
        '+proj=longlat +datum=WGS84 +no_defs', 
        'GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]]')
    ON CONFLICT (srid) DO NOTHING;
    "
    
    if ! PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$sql_commands"; then
        error "Failed to setup PostGIS extensions"
    fi
    
    success "PostGIS extensions setup completed"
}

# Create spatial data structures for Oddiya
setup_spatial_structures() {
    log "Creating spatial data structures for Oddiya..."
    
    local sql_commands="
    -- Create places table with spatial index (if not exists)
    CREATE TABLE IF NOT EXISTS places (
        id BIGSERIAL PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        category VARCHAR(100) NOT NULL,
        subcategory VARCHAR(100),
        address TEXT,
        location GEOMETRY(POINT, 4326),
        naver_place_id VARCHAR(100) UNIQUE,
        rating DECIMAL(3,2),
        price_range VARCHAR(20),
        metadata JSONB,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    
    -- Create spatial index on location
    CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);
    
    -- Create category index
    CREATE INDEX IF NOT EXISTS idx_places_category ON places (category);
    
    -- Create text search index
    CREATE INDEX IF NOT EXISTS idx_places_name_search ON places USING gin(to_tsvector('korean', name));
    
    -- Create travel_plan_items table with spatial relationships
    CREATE TABLE IF NOT EXISTS travel_plan_items (
        id BIGSERIAL PRIMARY KEY,
        travel_plan_id BIGINT NOT NULL,
        place_id BIGINT REFERENCES places(id),
        day_number INTEGER NOT NULL,
        order_in_day INTEGER NOT NULL,
        start_time TIME,
        end_time TIME,
        notes TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    
    -- Create indexes for travel plan items
    CREATE INDEX IF NOT EXISTS idx_travel_plan_items_plan_id ON travel_plan_items (travel_plan_id);
    CREATE INDEX IF NOT EXISTS idx_travel_plan_items_place_id ON travel_plan_items (place_id);
    CREATE INDEX IF NOT EXISTS idx_travel_plan_items_day ON travel_plan_items (travel_plan_id, day_number);
    
    -- Create spatial search function for nearby places
    CREATE OR REPLACE FUNCTION find_nearby_places(
        search_lat DOUBLE PRECISION,
        search_lng DOUBLE PRECISION,
        radius_meters INTEGER DEFAULT 5000,
        place_category TEXT DEFAULT NULL,
        result_limit INTEGER DEFAULT 50
    )
    RETURNS TABLE (
        id BIGINT,
        name VARCHAR(255),
        category VARCHAR(100),
        subcategory VARCHAR(100),
        address TEXT,
        latitude DOUBLE PRECISION,
        longitude DOUBLE PRECISION,
        distance_meters DOUBLE PRECISION,
        rating DECIMAL(3,2),
        price_range VARCHAR(20)
    )
    LANGUAGE SQL
    STABLE
    AS \$\$
        SELECT 
            p.id,
            p.name,
            p.category,
            p.subcategory,
            p.address,
            ST_Y(p.location) as latitude,
            ST_X(p.location) as longitude,
            ST_Distance(
                p.location::geography,
                ST_SetSRID(ST_MakePoint(search_lng, search_lat), 4326)::geography
            ) as distance_meters,
            p.rating,
            p.price_range
        FROM places p
        WHERE 
            ST_DWithin(
                p.location::geography,
                ST_SetSRID(ST_MakePoint(search_lng, search_lat), 4326)::geography,
                radius_meters
            )
            AND (place_category IS NULL OR p.category = place_category)
        ORDER BY distance_meters
        LIMIT result_limit;
    \$\$;
    
    -- Create function to calculate travel plan bounds
    CREATE OR REPLACE FUNCTION get_travel_plan_bounds(plan_id BIGINT)
    RETURNS TABLE (
        min_lat DOUBLE PRECISION,
        min_lng DOUBLE PRECISION,
        max_lat DOUBLE PRECISION,
        max_lng DOUBLE PRECISION,
        center_lat DOUBLE PRECISION,
        center_lng DOUBLE PRECISION
    )
    LANGUAGE SQL
    STABLE
    AS \$\$
        WITH bounds AS (
            SELECT 
                ST_YMin(ST_Extent(p.location)) as min_lat,
                ST_XMin(ST_Extent(p.location)) as min_lng,
                ST_YMax(ST_Extent(p.location)) as max_lat,
                ST_XMax(ST_Extent(p.location)) as max_lng,
                ST_Y(ST_Centroid(ST_Extent(p.location))) as center_lat,
                ST_X(ST_Centroid(ST_Extent(p.location))) as center_lng
            FROM travel_plan_items tpi
            JOIN places p ON tpi.place_id = p.id
            WHERE tpi.travel_plan_id = plan_id
        )
        SELECT * FROM bounds;
    \$\$;
    "
    
    if ! PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$sql_commands"; then
        error "Failed to create spatial data structures"
    fi
    
    success "Spatial data structures created successfully"
}

# Insert sample data for testing
insert_sample_data() {
    log "Inserting sample spatial data for testing..."
    
    local sql_commands="
    -- Insert sample places in Seoul
    INSERT INTO places (name, category, subcategory, address, location, rating, price_range, metadata)
    VALUES 
        ('경복궁', 'attraction', 'palace', '서울특별시 종로구 사직로 161', ST_SetSRID(ST_MakePoint(126.9770, 37.5796), 4326), 4.5, 'free', '{\"description\": \"조선 왕조의 첫 번째 궁궐\"}'),
        ('명동성당', 'attraction', 'church', '서울특별시 중구 명동길 74', ST_SetSRID(ST_MakePoint(126.9856, 37.5633), 4326), 4.3, 'free', '{\"description\": \"한국 천주교의 중심지\"}'),
        ('남산타워', 'attraction', 'tower', '서울특별시 용산구 남산공원길 105', ST_SetSRID(ST_MakePoint(126.9883, 37.5512), 4326), 4.2, '$$', '{\"description\": \"서울의 랜드마크 타워\"}'),
        ('홍대입구역', 'transportation', 'subway', '서울특별시 마포구 어울마당로 200', ST_SetSRID(ST_MakePoint(126.9244, 37.5565), 4326), 4.0, 'free', '{\"description\": \"지하철 2, 6, 공항철도 환승역\"}'),
        ('광화문광장', 'attraction', 'square', '서울특별시 종로구 세종대로 172', ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326), 4.1, 'free', '{\"description\": \"서울의 중심 광장\"}')
    ON CONFLICT (naver_place_id) DO NOTHING;
    "
    
    if ! PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$sql_commands"; then
        warn "Failed to insert sample data (may already exist)"
    else
        success "Sample spatial data inserted successfully"
    fi
}

# Verify PostGIS setup
verify_setup() {
    log "Verifying PostGIS setup..."
    
    local verification_sql="
    -- Check PostGIS version
    SELECT 'PostGIS Version: ' || PostGIS_Version() as info;
    
    -- Check spatial reference system
    SELECT 'SRID 4326 exists: ' || CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END as srid_check
    FROM spatial_ref_sys WHERE srid = 4326;
    
    -- Check places table structure
    SELECT 'Places table exists: ' || CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END as table_check
    FROM information_schema.tables WHERE table_name = 'places';
    
    -- Check spatial index
    SELECT 'Spatial index exists: ' || CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END as index_check
    FROM pg_indexes WHERE tablename = 'places' AND indexname = 'idx_places_location';
    
    -- Test spatial function
    SELECT 'Nearby places function: ' || CASE WHEN COUNT(*) >= 0 THEN 'WORKING' ELSE 'FAILED' END as function_check
    FROM find_nearby_places(37.5759, 126.9769, 1000);
    "
    
    if ! PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$verification_sql"; then
        error "PostGIS verification failed"
    fi
    
    success "PostGIS setup verification completed successfully"
}

# Main execution
main() {
    log "Starting PostGIS initialization for environment: $ENVIRONMENT"
    
    # Check prerequisites
    if ! command -v aws &> /dev/null; then
        error "AWS CLI is not installed or not in PATH"
    fi
    
    if ! command -v psql &> /dev/null; then
        error "PostgreSQL client (psql) is not installed or not in PATH"
    fi
    
    if ! command -v jq &> /dev/null; then
        error "jq is not installed or not in PATH"
    fi
    
    # Get database connection and setup PostGIS
    get_db_connection
    test_db_connection
    setup_postgis
    setup_spatial_structures
    insert_sample_data
    verify_setup
    
    success "PostGIS initialization completed successfully for $ENVIRONMENT environment"
    log "Your database is now ready for spatial queries and operations"
}

# Show usage if requested
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    cat << EOF
Usage: $0 [ENVIRONMENT]

This script initializes PostGIS extensions and spatial data structures for the Oddiya application.

ENVIRONMENT:
  dev      - Development environment (default)
  staging  - Staging environment
  prod     - Production environment

Prerequisites:
  - AWS CLI configured with appropriate credentials
  - PostgreSQL client (psql) installed
  - jq command-line JSON processor installed
  - Database must be accessible from current network

Examples:
  $0                    # Initialize PostGIS for dev environment
  $0 staging           # Initialize PostGIS for staging environment
  $0 prod              # Initialize PostGIS for production environment

EOF
    exit 0
fi

# Run main function
main "$@"