-- =====================================================
-- Oddiya Travel Planning Service - PostGIS Spatial Functions
-- Version: 1.0
-- Date: 2025-08-12
-- Migration: V2__PostGIS_Spatial_Functions.sql
-- =====================================================

-- =====================================================
-- SPATIAL HELPER FUNCTIONS
-- =====================================================

-- Function to convert latitude/longitude to PostGIS geography point
CREATE OR REPLACE FUNCTION create_geography_point(lat DOUBLE PRECISION, lng DOUBLE PRECISION)
RETURNS GEOGRAPHY(POINT, 4326) AS $$
BEGIN
    IF lat IS NULL OR lng IS NULL THEN
        RETURN NULL;
    END IF;
    
    -- Validate coordinate ranges
    IF lat < -90 OR lat > 90 OR lng < -180 OR lng > 180 THEN
        RAISE EXCEPTION 'Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180';
    END IF;
    
    RETURN ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to search places within radius (optimized for Korean coordinates)
CREATE OR REPLACE FUNCTION find_places_within_radius(
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 5000,
    place_category VARCHAR DEFAULT NULL,
    limit_results INTEGER DEFAULT 50,
    min_rating DOUBLE PRECISION DEFAULT NULL
)
RETURNS TABLE (
    place_id VARCHAR,
    name VARCHAR,
    category VARCHAR,
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters INTEGER,
    rating DOUBLE PRECISION,
    review_count INTEGER,
    popularity_score DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.id,
        p.name,
        p.category,
        p.address,
        p.latitude,
        p.longitude,
        ROUND(ST_Distance(p.location, create_geography_point(center_lat, center_lng))::numeric)::INTEGER as distance_meters,
        p.rating,
        p.review_count,
        p.popularity_score
    FROM places p
    WHERE 
        p.is_deleted = false
        AND p.is_verified = true
        AND ST_DWithin(
            p.location, 
            create_geography_point(center_lat, center_lng), 
            radius_meters
        )
        AND (place_category IS NULL OR p.category = place_category)
        AND (min_rating IS NULL OR p.rating >= min_rating)
    ORDER BY 
        -- Prioritize by distance first, then by popularity score
        ST_Distance(p.location, create_geography_point(center_lat, center_lng)),
        p.popularity_score DESC,
        p.rating DESC
    LIMIT limit_results;
END;
$$ LANGUAGE plpgsql STABLE;

-- Function to find nearby places for recommendations
CREATE OR REPLACE FUNCTION find_nearby_recommendations(
    place_ids VARCHAR[],
    radius_meters INTEGER DEFAULT 2000,
    exclude_categories VARCHAR[] DEFAULT NULL,
    limit_results INTEGER DEFAULT 20
)
RETURNS TABLE (
    place_id VARCHAR,
    name VARCHAR,
    category VARCHAR,
    distance_from_nearest INTEGER,
    avg_distance_to_group INTEGER,
    popularity_score DOUBLE PRECISION,
    rating DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.id,
        p.name,
        p.category,
        ROUND(min_distance.min_dist)::INTEGER as distance_from_nearest,
        ROUND(avg_distance.avg_dist)::INTEGER as avg_distance_to_group,
        p.popularity_score,
        p.rating
    FROM places p
    CROSS JOIN LATERAL (
        SELECT MIN(ST_Distance(p.location, ref_p.location)) as min_dist
        FROM places ref_p
        WHERE ref_p.id = ANY(place_ids)
    ) min_distance
    CROSS JOIN LATERAL (
        SELECT AVG(ST_Distance(p.location, ref_p.location)) as avg_dist
        FROM places ref_p
        WHERE ref_p.id = ANY(place_ids)
    ) avg_distance
    WHERE 
        p.is_deleted = false
        AND p.is_verified = true
        AND p.id != ALL(place_ids) -- Exclude the input places
        AND min_distance.min_dist <= radius_meters
        AND (exclude_categories IS NULL OR p.category != ALL(exclude_categories))
    ORDER BY 
        min_distance.min_dist,
        p.popularity_score DESC,
        p.rating DESC
    LIMIT limit_results;
END;
$$ LANGUAGE plpgsql STABLE;

-- Function to calculate optimal route distance between places
CREATE OR REPLACE FUNCTION calculate_places_route_distance(place_ids VARCHAR[])
RETURNS TABLE (
    total_distance_meters INTEGER,
    max_segment_distance_meters INTEGER,
    place_coordinates JSONB
) AS $$
DECLARE
    total_dist DOUBLE PRECISION := 0;
    max_segment DOUBLE PRECISION := 0;
    current_dist DOUBLE PRECISION;
    coords JSONB := '[]'::jsonb;
    i INTEGER;
BEGIN
    -- Build coordinates array and calculate distances
    FOR i IN 1..array_length(place_ids, 1) LOOP
        -- Add coordinates to result
        SELECT coords || jsonb_build_object(
            'place_id', p.id,
            'name', p.name,
            'latitude', p.latitude,
            'longitude', p.longitude,
            'sequence', i
        ) INTO coords
        FROM places p
        WHERE p.id = place_ids[i];
        
        -- Calculate distance to next place
        IF i < array_length(place_ids, 1) THEN
            SELECT ST_Distance(p1.location, p2.location) INTO current_dist
            FROM places p1, places p2
            WHERE p1.id = place_ids[i] AND p2.id = place_ids[i+1];
            
            total_dist := total_dist + current_dist;
            max_segment := GREATEST(max_segment, current_dist);
        END IF;
    END LOOP;
    
    RETURN QUERY SELECT 
        ROUND(total_dist)::INTEGER,
        ROUND(max_segment)::INTEGER,
        coords;
END;
$$ LANGUAGE plpgsql STABLE;

-- =====================================================
-- PERFORMANCE OPTIMIZATION FUNCTIONS
-- =====================================================

-- Function to update place popularity scores
CREATE OR REPLACE FUNCTION update_place_popularity_scores()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER := 0;
BEGIN
    UPDATE places
    SET popularity_score = (
        -- Base score from rating (0-50 points)
        COALESCE(rating * 10, 0) +
        -- Visit count score (0-30 points, capped)
        LEAST(view_count / 10, 30) +
        -- Review engagement score (0-20 points)
        LEAST(review_count * 2, 20) +
        -- Bookmark score (0-10 points)
        LEAST(bookmark_count, 10)
    )
    WHERE is_deleted = false;
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- Function to refresh location data from coordinates
CREATE OR REPLACE FUNCTION refresh_place_locations()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER := 0;
BEGIN
    UPDATE places
    SET location = create_geography_point(latitude, longitude)
    WHERE location IS NULL OR latitude != ST_Y(location::geometry) OR longitude != ST_X(location::geometry);
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- DATA VALIDATION FUNCTIONS
-- =====================================================

-- Function to validate Korean coordinate ranges (approximate bounds for South Korea)
CREATE OR REPLACE FUNCTION is_valid_korean_coordinates(lat DOUBLE PRECISION, lng DOUBLE PRECISION)
RETURNS BOOLEAN AS $$
BEGIN
    -- South Korea approximate bounds:
    -- Latitude: 33.0° N to 38.6° N
    -- Longitude: 125.0° E to 131.9° E
    RETURN lat >= 33.0 AND lat <= 38.6 AND lng >= 125.0 AND lng <= 131.9;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Trigger function to automatically set geography point on place insert/update
CREATE OR REPLACE FUNCTION auto_update_place_location()
RETURNS TRIGGER AS $$
BEGIN
    -- Update location field from latitude/longitude
    NEW.location := create_geography_point(NEW.latitude, NEW.longitude);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply the trigger to places table
CREATE TRIGGER places_auto_location_trigger
    BEFORE INSERT OR UPDATE ON places
    FOR EACH ROW
    WHEN (NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL)
    EXECUTE FUNCTION auto_update_place_location();

-- =====================================================
-- ANALYTICS AND REPORTING FUNCTIONS
-- =====================================================

-- Function to get place statistics within an area
CREATE OR REPLACE FUNCTION get_area_place_statistics(
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 10000
)
RETURNS TABLE (
    total_places INTEGER,
    verified_places INTEGER,
    avg_rating DOUBLE PRECISION,
    category_breakdown JSONB,
    top_rated_places JSONB
) AS $$
DECLARE
    stats_result RECORD;
BEGIN
    -- Get basic statistics
    SELECT 
        COUNT(*)::INTEGER as total,
        COUNT(*) FILTER (WHERE is_verified = true)::INTEGER as verified,
        ROUND(AVG(rating), 2) as avg_rating
    INTO stats_result
    FROM places p
    WHERE 
        p.is_deleted = false
        AND ST_DWithin(p.location, create_geography_point(center_lat, center_lng), radius_meters);
    
    RETURN QUERY
    SELECT 
        stats_result.total,
        stats_result.verified,
        stats_result.avg_rating,
        -- Category breakdown
        (SELECT jsonb_object_agg(category, cnt)
         FROM (
            SELECT category, COUNT(*)::INTEGER as cnt
            FROM places p
            WHERE p.is_deleted = false
              AND ST_DWithin(p.location, create_geography_point(center_lat, center_lng), radius_meters)
            GROUP BY category
            ORDER BY cnt DESC
         ) cat_stats),
        -- Top 5 rated places
        (SELECT jsonb_agg(jsonb_build_object(
            'id', id,
            'name', name,
            'category', category,
            'rating', rating,
            'review_count', review_count
        ))
         FROM (
            SELECT id, name, category, rating, review_count
            FROM places p
            WHERE p.is_deleted = false
              AND p.rating IS NOT NULL
              AND ST_DWithin(p.location, create_geography_point(center_lat, center_lng), radius_meters)
            ORDER BY rating DESC, review_count DESC
            LIMIT 5
         ) top_places);
END;
$$ LANGUAGE plpgsql STABLE;

-- =====================================================
-- INDEXES FOR SPATIAL FUNCTIONS
-- =====================================================

-- Additional indexes for function performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_category_rating 
    ON places(category, rating DESC) 
    WHERE is_deleted = false AND is_verified = true AND rating IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_verified_popularity 
    ON places(is_verified, popularity_score DESC) 
    WHERE is_deleted = false;

-- Composite index for common spatial queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_spatial_composite 
    ON places USING GIST(location) 
    INCLUDE (category, rating, popularity_score, is_verified)
    WHERE is_deleted = false;

-- =====================================================
-- COMMENTS FOR SPATIAL FUNCTIONS
-- =====================================================

COMMENT ON FUNCTION find_places_within_radius IS 'Optimized spatial search for places within specified radius, with optional filtering by category and rating';
COMMENT ON FUNCTION find_nearby_recommendations IS 'Find recommended places near a group of places, useful for travel itinerary suggestions';
COMMENT ON FUNCTION calculate_places_route_distance IS 'Calculate total route distance and return coordinates for a sequence of places';
COMMENT ON FUNCTION update_place_popularity_scores IS 'Recalculate popularity scores for all places based on ratings, views, reviews, and bookmarks';
COMMENT ON FUNCTION is_valid_korean_coordinates IS 'Validate if coordinates fall within South Korean boundaries';