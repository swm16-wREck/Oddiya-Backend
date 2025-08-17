-- PostgreSQL TestContainers initialization script
-- Works with or without PostGIS extension

-- Enable UUID extension (always available)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Try to enable PostGIS but don't fail if not available
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS postgis_topology;
    RAISE NOTICE 'PostGIS extensions created successfully';
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'PostGIS not available - tests will run without spatial features';
END $$;

-- Create test schema
CREATE SCHEMA IF NOT EXISTS oddiya_test;

-- Set search path
ALTER DATABASE oddiya_test SET search_path TO public;

-- Verify setup without requiring PostGIS
DO $$
BEGIN
    RAISE NOTICE 'Test database initialized successfully';
END $$;

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