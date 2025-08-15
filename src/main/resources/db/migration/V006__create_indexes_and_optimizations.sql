-- Performance optimization indexes and constraints
-- Migration: V006__create_indexes_and_optimizations.sql

-- ==========================================
-- Additional Performance Indexes
-- ==========================================

-- Composite indexes for common query patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_travel_plans_user_status_dates 
ON travel_plans (user_id, status, start_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_travel_plans_public_popular 
ON travel_plans (is_public, popularity_score DESC, created_at DESC) 
WHERE is_public = true AND status = 'PUBLISHED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_category_rating 
ON places (category, rating DESC, popularity_score DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_location_verified 
ON places (latitude, longitude, is_verified) 
WHERE is_verified = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_place_rating_date 
ON reviews (place_id, rating DESC, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_itinerary_plan_day_seq 
ON itinerary_items (travel_plan_id, day_number, sequence);

-- Partial indexes for active/published content
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_active 
ON users (created_at DESC) 
WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_videos_public_ready 
ON videos (created_at DESC, view_count DESC) 
WHERE is_public = true AND status = 'READY';

-- ==========================================
-- Full-Text Search Indexes
-- ==========================================

-- Add tsvector columns for full-text search
ALTER TABLE places ADD COLUMN search_vector tsvector;
ALTER TABLE travel_plans ADD COLUMN search_vector tsvector;

-- Create function to update place search vector
CREATE OR REPLACE FUNCTION update_places_search_vector() 
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('english', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.category, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'C') ||
        setweight(to_tsvector('english', COALESCE(NEW.address, '')), 'D');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create function to update travel plans search vector
CREATE OR REPLACE FUNCTION update_travel_plans_search_vector() 
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.destination, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers for search vector updates
CREATE TRIGGER trigger_places_search_vector_update
    BEFORE INSERT OR UPDATE OF name, category, description, address ON places
    FOR EACH ROW
    EXECUTE FUNCTION update_places_search_vector();

CREATE TRIGGER trigger_travel_plans_search_vector_update
    BEFORE INSERT OR UPDATE OF title, destination, description ON travel_plans
    FOR EACH ROW
    EXECUTE FUNCTION update_travel_plans_search_vector();

-- Create GIN indexes for full-text search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_search_vector 
ON places USING gin(search_vector);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_travel_plans_search_vector 
ON travel_plans USING gin(search_vector);

-- Update existing records with search vectors
UPDATE places SET search_vector = 
    setweight(to_tsvector('english', COALESCE(name, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(category, '')), 'B') ||
    setweight(to_tsvector('english', COALESCE(description, '')), 'C') ||
    setweight(to_tsvector('english', COALESCE(address, '')), 'D');

UPDATE travel_plans SET search_vector = 
    setweight(to_tsvector('english', COALESCE(title, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(destination, '')), 'B') ||
    setweight(to_tsvector('english', COALESCE(description, '')), 'C');

-- ==========================================
-- Spatial Queries Enhancement
-- ==========================================

-- Function to find nearby places (enhanced with PostGIS when available)
CREATE OR REPLACE FUNCTION find_nearby_places(
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    radius_km DOUBLE PRECISION DEFAULT 5.0,
    limit_count INTEGER DEFAULT 20
) RETURNS TABLE(
    place_id VARCHAR(255),
    name VARCHAR(255),
    category VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_km DOUBLE PRECISION
) AS $$
BEGIN
    -- Use PostGIS if available, otherwise fall back to Haversine
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        RETURN QUERY
        SELECT 
            p.id,
            p.name,
            p.category,
            p.latitude,
            p.longitude,
            ST_Distance(p.location, ST_SetSRID(ST_MakePoint(center_lng, center_lat), 4326)) / 1000 as distance_km
        FROM places p
        WHERE p.location IS NOT NULL
          AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(center_lng, center_lat), 4326), radius_km * 1000)
          AND p.is_verified = true
        ORDER BY p.location <-> ST_SetSRID(ST_MakePoint(center_lng, center_lat), 4326)
        LIMIT limit_count;
    ELSE
        RETURN QUERY
        SELECT 
            p.id,
            p.name,
            p.category,
            p.latitude,
            p.longitude,
            calculate_distance_km(center_lat, center_lng, p.latitude, p.longitude) as distance_km
        FROM places p
        WHERE p.latitude IS NOT NULL 
          AND p.longitude IS NOT NULL
          AND p.is_verified = true
          AND calculate_distance_km(center_lat, center_lng, p.latitude, p.longitude) <= radius_km
        ORDER BY calculate_distance_km(center_lat, center_lng, p.latitude, p.longitude)
        LIMIT limit_count;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- Partitioning for Large Tables
-- ==========================================

-- Create partitioned table for user interactions (by month)
-- Note: This is for future scalability when data grows large

-- ==========================================
-- Statistics and Analytics Functions
-- ==========================================

-- Function to get popular destinations
CREATE OR REPLACE FUNCTION get_popular_destinations(
    limit_count INTEGER DEFAULT 10,
    days_back INTEGER DEFAULT 30
) RETURNS TABLE(
    destination VARCHAR(255),
    plan_count BIGINT,
    avg_rating DOUBLE PRECISION,
    total_views BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        tp.destination,
        COUNT(*)::BIGINT as plan_count,
        AVG(COALESCE(tp.popularity_score, 0)) as avg_rating,
        SUM(tp.view_count)::BIGINT as total_views
    FROM travel_plans tp
    WHERE tp.is_public = true 
      AND tp.status = 'PUBLISHED'
      AND tp.created_at >= CURRENT_DATE - INTERVAL '%s days' % days_back
    GROUP BY tp.destination
    HAVING COUNT(*) >= 2
    ORDER BY plan_count DESC, total_views DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- Function to get trending places
CREATE OR REPLACE FUNCTION get_trending_places(
    limit_count INTEGER DEFAULT 10,
    days_back INTEGER DEFAULT 7
) RETURNS TABLE(
    place_id VARCHAR(255),
    name VARCHAR(255),
    category VARCHAR(100),
    recent_views BIGINT,
    recent_bookmarks BIGINT,
    total_rating DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.id,
        p.name,
        p.category,
        COALESCE(recent_stats.views, 0)::BIGINT as recent_views,
        COALESCE(recent_stats.bookmarks, 0)::BIGINT as recent_bookmarks,
        p.rating as total_rating
    FROM places p
    LEFT JOIN (
        SELECT 
            ui.target_id,
            COUNT(*) FILTER (WHERE ui.interaction_type = 'view') as views,
            COUNT(*) FILTER (WHERE ui.interaction_type = 'bookmark') as bookmarks
        FROM user_interactions ui
        WHERE ui.target_type = 'place'
          AND ui.created_at >= CURRENT_DATE - INTERVAL '%s days' % days_back
        GROUP BY ui.target_id
    ) recent_stats ON p.id = recent_stats.target_id
    WHERE p.is_verified = true
      AND (recent_stats.views > 0 OR recent_stats.bookmarks > 0)
    ORDER BY 
        recent_stats.views DESC NULLS LAST,
        recent_stats.bookmarks DESC NULLS LAST,
        p.rating DESC NULLS LAST
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- Data Validation and Constraints
-- ==========================================

-- Add additional constraints for data integrity
ALTER TABLE places ADD CONSTRAINT chk_places_popularity_score 
CHECK (popularity_score >= 0);

ALTER TABLE travel_plans ADD CONSTRAINT chk_travel_plans_metrics 
CHECK (view_count >= 0 AND like_count >= 0 AND share_count >= 0 AND save_count >= 0);

ALTER TABLE videos ADD CONSTRAINT chk_videos_metrics 
CHECK (view_count >= 0 AND like_count >= 0);

-- ==========================================
-- Database Maintenance Functions
-- ==========================================

-- Function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_materialized_views() 
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_activity_feed;
END;
$$ LANGUAGE plpgsql;

-- Function to update statistics
CREATE OR REPLACE FUNCTION update_table_statistics() 
RETURNS VOID AS $$
BEGIN
    ANALYZE users;
    ANALYZE places;
    ANALYZE travel_plans;
    ANALYZE itinerary_items;
    ANALYZE reviews;
    ANALYZE user_interactions;
    ANALYZE saved_plans;
    ANALYZE videos;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- Comments and Documentation
-- ==========================================

COMMENT ON FUNCTION find_nearby_places IS 'Find places near a location with distance calculation (PostGIS aware)';
COMMENT ON FUNCTION get_popular_destinations IS 'Get popular travel destinations based on plan count and engagement';
COMMENT ON FUNCTION get_trending_places IS 'Get trending places based on recent user interactions';
COMMENT ON FUNCTION refresh_materialized_views IS 'Refresh all materialized views';
COMMENT ON FUNCTION update_table_statistics IS 'Update table statistics for query optimization';

-- Add column comments for search vectors
COMMENT ON COLUMN places.search_vector IS 'Full-text search vector for places';
COMMENT ON COLUMN travel_plans.search_vector IS 'Full-text search vector for travel plans';