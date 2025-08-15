-- Create places table with PostGIS spatial support
-- Migration: V002__create_places_table.sql

-- Enable PostGIS extension (requires superuser privileges)
-- This should be done separately by DBA: CREATE EXTENSION IF NOT EXISTS postgis;

-- Create places table
CREATE TABLE places (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Place identification
    naver_place_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Location information
    address TEXT NOT NULL,
    road_address TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    
    -- Contact information
    phone_number VARCHAR(50),
    website TEXT,
    
    -- Metrics
    rating DOUBLE PRECISION,
    review_count INTEGER NOT NULL DEFAULT 0,
    bookmark_count INTEGER NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    
    -- Status
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Spatial column for PostGIS (when available)
    location GEOMETRY(POINT, 4326),
    
    CONSTRAINT uk_places_naver_place_id UNIQUE (naver_place_id),
    CONSTRAINT chk_places_rating CHECK (rating >= 0 AND rating <= 5),
    CONSTRAINT chk_places_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT chk_places_longitude CHECK (longitude >= -180 AND longitude <= 180)
);

-- Create spatial index on location (PostGIS)
-- This will be created conditionally if PostGIS is available
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        -- Update location column with Point geometry
        UPDATE places SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
        WHERE location IS NULL AND longitude IS NOT NULL AND latitude IS NOT NULL;
        
        -- Create spatial index
        CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);
    END IF;
END $$;

-- Create standard indexes
CREATE INDEX idx_place_category ON places (category);
CREATE INDEX idx_place_location ON places (latitude, longitude);
CREATE INDEX idx_place_naver ON places (naver_place_id);
CREATE INDEX idx_place_rating ON places (rating DESC);
CREATE INDEX idx_place_popularity ON places (popularity_score DESC);
CREATE INDEX idx_place_verified ON places (is_verified);
CREATE INDEX idx_place_created_at ON places (created_at);

-- Create place opening hours table
CREATE TABLE place_opening_hours (
    place_id VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    hours TEXT,
    PRIMARY KEY (place_id, day_of_week),
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE,
    CONSTRAINT chk_day_of_week CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

-- Create place images table
CREATE TABLE place_images (
    place_id VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (place_id, image_url),
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
);

CREATE INDEX idx_place_images_sort ON place_images (place_id, sort_order);

-- Create place tags table
CREATE TABLE place_tags (
    place_id VARCHAR(255) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (place_id, tag),
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
);

CREATE INDEX idx_place_tags_tag ON place_tags (tag);

-- Apply updated_at trigger to places table
CREATE TRIGGER trigger_places_updated_at
    BEFORE UPDATE ON places
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to update PostGIS location when lat/lng changes
CREATE OR REPLACE FUNCTION update_place_location()
RETURNS TRIGGER AS $$
BEGIN
    -- Only update if PostGIS is available
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
            NEW.location = ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply location update trigger
CREATE TRIGGER trigger_update_place_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON places
    FOR EACH ROW
    EXECUTE FUNCTION update_place_location();

-- Function for spatial queries (fallback when PostGIS not available)
CREATE OR REPLACE FUNCTION calculate_distance_km(
    lat1 DOUBLE PRECISION,
    lon1 DOUBLE PRECISION,
    lat2 DOUBLE PRECISION,
    lon2 DOUBLE PRECISION
) RETURNS DOUBLE PRECISION AS $$
BEGIN
    -- Haversine formula for distance calculation
    RETURN 6371 * acos(
        cos(radians(lat1)) * 
        cos(radians(lat2)) * 
        cos(radians(lon2) - radians(lon1)) + 
        sin(radians(lat1)) * 
        sin(radians(lat2))
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Add comments
COMMENT ON TABLE places IS 'Places of interest with spatial data';
COMMENT ON COLUMN places.naver_place_id IS 'Unique identifier from Naver Places API';
COMMENT ON COLUMN places.location IS 'PostGIS point geometry (SRID 4326)';
COMMENT ON COLUMN places.popularity_score IS 'Calculated popularity score based on views, ratings, etc.';
COMMENT ON TABLE place_opening_hours IS 'Opening hours by day of week';
COMMENT ON TABLE place_images IS 'Image URLs for places';
COMMENT ON TABLE place_tags IS 'Tags associated with places';
COMMENT ON FUNCTION calculate_distance_km IS 'Calculate distance between two lat/lng points in kilometers';