-- Phase 2 PostgreSQL TestContainers initialization script
-- Updated for PostgreSQL migration with PostGIS extension and spatial testing

-- Enable PostGIS extension for spatial queries
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create test schema
CREATE SCHEMA IF NOT EXISTS oddiya_test;

-- Set search path for spatial queries
ALTER DATABASE oddiya_test SET search_path TO public, postgis, topology;

-- Verify PostGIS installation
SELECT PostGIS_Version();

-- Create basic test data types and functions
CREATE OR REPLACE FUNCTION test_spatial_setup() RETURNS TEXT AS $$
BEGIN
    -- Verify spatial reference system
    IF EXISTS (SELECT * FROM spatial_ref_sys WHERE srid = 4326) THEN
        RETURN 'PostGIS spatial setup successful - WGS84 (SRID: 4326) available';
    ELSE
        RAISE EXCEPTION 'PostGIS spatial setup failed - WGS84 not available';
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Execute spatial setup verification
SELECT test_spatial_setup();

-- Performance optimization for tests
SET shared_preload_libraries = '';
SET effective_cache_size = '128MB';
SET maintenance_work_mem = '64MB';
SET checkpoint_completion_target = 0.9;
SET wal_buffers = '16MB';
SET default_statistics_target = 100;

-- Create test-specific indexes for spatial queries
-- These will be used by the actual migrations but good to have in test setup
COMMENT ON SCHEMA public IS 'Oddiya test database initialized with PostGIS extensions';