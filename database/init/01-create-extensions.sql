-- Database Initialization Script
-- Creates necessary extensions for Oddiya application

-- Enable PostGIS extension for spatial functionality
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;
CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;

-- Enable additional useful extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Create a dedicated schema for spatial data
CREATE SCHEMA IF NOT EXISTS spatial;
GRANT USAGE ON SCHEMA spatial TO PUBLIC;

-- Create application-specific roles
DO $$
BEGIN
    -- Create read-only role
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oddiya_reader') THEN
        CREATE ROLE oddiya_reader;
        GRANT CONNECT ON DATABASE oddiya TO oddiya_reader;
        GRANT USAGE ON SCHEMA public TO oddiya_reader;
        GRANT USAGE ON SCHEMA spatial TO oddiya_reader;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO oddiya_reader;
        GRANT SELECT ON ALL TABLES IN SCHEMA spatial TO oddiya_reader;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO oddiya_reader;
        ALTER DEFAULT PRIVILEGES IN SCHEMA spatial GRANT SELECT ON TABLES TO oddiya_reader;
    END IF;

    -- Create application role
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oddiya_app') THEN
        CREATE ROLE oddiya_app;
        GRANT CONNECT ON DATABASE oddiya TO oddiya_app;
        GRANT USAGE, CREATE ON SCHEMA public TO oddiya_app;
        GRANT USAGE, CREATE ON SCHEMA spatial TO oddiya_app;
        GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO oddiya_app;
        GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA spatial TO oddiya_app;
        GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO oddiya_app;
        GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA spatial TO oddiya_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO oddiya_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA spatial GRANT ALL ON TABLES TO oddiya_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO oddiya_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA spatial GRANT ALL ON SEQUENCES TO oddiya_app;
    END IF;
END
$$;