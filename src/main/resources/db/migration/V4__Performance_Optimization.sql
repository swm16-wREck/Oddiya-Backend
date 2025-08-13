-- =====================================================
-- Oddiya Travel Planning Service - Performance Optimization
-- Version: 1.0  
-- Date: 2025-08-12
-- Migration: V4__Performance_Optimization.sql
-- =====================================================

-- =====================================================
-- ADDITIONAL PERFORMANCE INDEXES
-- =====================================================

-- Composite indexes for common query patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_location_category_rating 
    ON places USING GIST(location) 
    INCLUDE (category, rating, popularity_score, review_count)
    WHERE is_deleted = false AND is_verified = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_travel_plans_user_status_public 
    ON travel_plans(user_id, status, is_public) 
    WHERE is_deleted = false;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_itinerary_plan_day_sequence 
    ON itinerary_items(travel_plan_id, day_number, sequence) 
    INCLUDE (place_id, start_time, end_time)
    WHERE is_deleted = false;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_place_public_rating 
    ON reviews(place_id, is_public, rating DESC) 
    WHERE is_deleted = false;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_coordinates_btree
    ON places(latitude, longitude)
    WHERE is_deleted = false AND is_verified = true;

-- Partial indexes for commonly filtered data
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_high_rating 
    ON places(rating DESC, review_count DESC) 
    WHERE rating >= 4.0 AND is_deleted = false AND is_verified = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_travel_plans_recent_public 
    ON travel_plans(created_at DESC) 
    WHERE is_public = true AND is_deleted = false AND status != 'DRAFT';

-- =====================================================
-- DATABASE STATISTICS AND ANALYSIS
-- =====================================================

-- Function to analyze and optimize database performance
CREATE OR REPLACE FUNCTION analyze_database_performance()
RETURNS TABLE (
    table_name TEXT,
    row_count BIGINT,
    table_size TEXT,
    index_size TEXT,
    recommendations TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        schemaname||'.'||tablename as table_name,
        n_tup_ins as row_count,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as table_size,
        pg_size_pretty(pg_indexes_size(schemaname||'.'||tablename)) as index_size,
        CASE 
            WHEN seq_scan > idx_scan AND n_tup_ins > 1000 THEN 'Consider adding indexes for frequent queries'
            WHEN pg_total_relation_size(schemaname||'.'||tablename) > 100000000 THEN 'Large table - monitor growth'
            ELSE 'Performance looks good'
        END as recommendations
    FROM pg_stat_user_tables 
    WHERE schemaname = 'public'
    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- MAINTENANCE PROCEDURES
-- =====================================================

-- Procedure to update table statistics
CREATE OR REPLACE PROCEDURE update_table_statistics()
LANGUAGE plpgsql
AS $$
BEGIN
    -- Update statistics for query planner optimization
    ANALYZE users;
    ANALYZE places;
    ANALYZE travel_plans;
    ANALYZE itinerary_items;
    ANALYZE reviews;
    ANALYZE videos;
    
    RAISE NOTICE 'Updated table statistics for query optimization';
END;
$$;

-- Procedure to clean up soft-deleted records (older than 30 days)
CREATE OR REPLACE PROCEDURE cleanup_soft_deleted_records()
LANGUAGE plpgsql
AS $$
DECLARE
    cleanup_date TIMESTAMP := CURRENT_TIMESTAMP - INTERVAL '30 days';
    deleted_count INTEGER;
BEGIN
    -- Clean up old soft-deleted users
    DELETE FROM users WHERE is_deleted = true AND deleted_at < cleanup_date;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Cleaned up % soft-deleted users', deleted_count;
    
    -- Clean up old soft-deleted places
    DELETE FROM places WHERE is_deleted = true AND deleted_at < cleanup_date;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Cleaned up % soft-deleted places', deleted_count;
    
    -- Clean up old soft-deleted travel plans
    DELETE FROM travel_plans WHERE is_deleted = true AND deleted_at < cleanup_date;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Cleaned up % soft-deleted travel plans', deleted_count;
END;
$$;

-- =====================================================
-- CONSTRAINTS AND DATA VALIDATION
-- =====================================================

-- Add constraint to ensure geography points are properly set
ALTER TABLE places ADD CONSTRAINT check_location_matches_coordinates 
    CHECK (
        location IS NULL OR 
        (ABS(ST_Y(location::geometry) - latitude) < 0.000001 AND 
         ABS(ST_X(location::geometry) - longitude) < 0.000001)
    );

-- Add constraint for reasonable travel plan duration
ALTER TABLE travel_plans ADD CONSTRAINT check_reasonable_duration 
    CHECK ((end_date - start_date) <= 365); -- Max 1 year duration

-- Add constraint for reasonable itinerary sequence
ALTER TABLE itinerary_items ADD CONSTRAINT check_reasonable_sequence 
    CHECK (sequence <= 50); -- Max 50 items per day

-- =====================================================
-- PERFORMANCE MONITORING VIEWS
-- =====================================================

-- View for monitoring spatial query performance
CREATE OR REPLACE VIEW spatial_query_performance AS
SELECT 
    schemaname,
    tablename,
    attname,
    n_distinct,
    correlation,
    most_common_vals
FROM pg_stats 
WHERE schemaname = 'public' 
  AND (attname LIKE '%location%' OR attname LIKE '%latitude%' OR attname LIKE '%longitude%')
ORDER BY tablename, attname;

-- View for monitoring popular places performance
CREATE OR REPLACE VIEW popular_places_monitoring AS
SELECT 
    category,
    COUNT(*) as place_count,
    AVG(rating) as avg_rating,
    AVG(popularity_score) as avg_popularity,
    MAX(view_count) as max_views
FROM places 
WHERE is_deleted = false AND is_verified = true
GROUP BY category
ORDER BY avg_popularity DESC;

-- =====================================================
-- SCHEDULED MAINTENANCE FUNCTIONS
-- =====================================================

-- Function to be called daily for maintenance
CREATE OR REPLACE FUNCTION daily_maintenance()
RETURNS TEXT AS $$
DECLARE
    result TEXT := '';
    updated_count INTEGER;
BEGIN
    -- Update popularity scores
    SELECT update_place_popularity_scores() INTO updated_count;
    result := result || 'Updated popularity scores for ' || updated_count || ' places. ';
    
    -- Update table statistics
    CALL update_table_statistics();
    result := result || 'Updated table statistics. ';
    
    -- Refresh any materialized views if needed
    -- (None currently defined, but placeholder for future views)
    
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Function to be called weekly for deeper maintenance
CREATE OR REPLACE FUNCTION weekly_maintenance()
RETURNS TEXT AS $$
DECLARE
    result TEXT := '';
BEGIN
    -- Run daily maintenance first
    result := daily_maintenance();
    
    -- Clean up soft-deleted records
    CALL cleanup_soft_deleted_records();
    result := result || 'Cleaned up old soft-deleted records. ';
    
    -- Reindex spatial indexes for optimal performance
    REINDEX INDEX CONCURRENTLY idx_places_location_gist;
    result := result || 'Reindexed spatial indexes. ';
    
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- SAMPLE PERFORMANCE TEST QUERIES
-- =====================================================

-- Create function to test spatial query performance
CREATE OR REPLACE FUNCTION test_spatial_performance()
RETURNS TABLE (
    query_description TEXT,
    execution_time_ms FLOAT,
    rows_returned BIGINT
) AS $$
DECLARE
    start_time TIMESTAMP;
    end_time TIMESTAMP;
    row_count BIGINT;
BEGIN
    -- Test 1: Basic radius search
    start_time := clock_timestamp();
    SELECT COUNT(*) INTO row_count FROM find_places_within_radius(37.5665, 126.9780, 5000);
    end_time := clock_timestamp();
    
    RETURN QUERY SELECT 
        'Basic radius search (Seoul City Hall, 5km)'::TEXT,
        EXTRACT(milliseconds FROM (end_time - start_time))::FLOAT,
        row_count;
    
    -- Test 2: Category filtered search
    start_time := clock_timestamp();
    SELECT COUNT(*) INTO row_count FROM find_places_within_radius(37.5665, 126.9780, 3000, 'restaurant');
    end_time := clock_timestamp();
    
    RETURN QUERY SELECT 
        'Category filtered search (restaurants, 3km)'::TEXT,
        EXTRACT(milliseconds FROM (end_time - start_time))::FLOAT,
        row_count;
    
    -- Test 3: High-rating places
    start_time := clock_timestamp();
    SELECT COUNT(*) INTO row_count FROM find_places_within_radius(37.5665, 126.9780, 5000, NULL, 4.5);
    end_time := clock_timestamp();
    
    RETURN QUERY SELECT 
        'High-rating search (4.5+, 5km)'::TEXT,
        EXTRACT(milliseconds FROM (end_time - start_time))::FLOAT,
        row_count;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- COMMENTS FOR MAINTENANCE
-- =====================================================

COMMENT ON FUNCTION daily_maintenance() IS 'Run daily to update popularity scores and table statistics';
COMMENT ON FUNCTION weekly_maintenance() IS 'Run weekly for comprehensive maintenance including cleanup and reindexing';
COMMENT ON FUNCTION test_spatial_performance() IS 'Test spatial query performance - run periodically to monitor performance';
COMMENT ON PROCEDURE cleanup_soft_deleted_records() IS 'Clean up soft-deleted records older than 30 days to maintain performance';

-- =====================================================
-- INITIAL MAINTENANCE RUN
-- =====================================================

-- Run initial maintenance to optimize the database
SELECT daily_maintenance();

-- Analyze tables for optimal query planning
CALL update_table_statistics();