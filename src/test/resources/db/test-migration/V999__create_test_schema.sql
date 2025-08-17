-- Test-specific schema creation without PostGIS geometry types
-- This allows tests to run without PostGIS dependency

-- Create places table without geometry column for testing
CREATE TABLE IF NOT EXISTS places (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Place identification
    naver_place_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Location information (without geometry type)
    address TEXT NOT NULL,
    road_address TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    
    -- Contact information
    phone_number VARCHAR(50),
    website TEXT,
    
    -- Metrics
    rating DOUBLE PRECISION,
    review_count INTEGER DEFAULT 0,
    
    -- Business hours
    business_hours TEXT,
    
    -- Arrays stored as JSONB
    image_urls JSONB,
    amenities JSONB,
    keywords JSONB,
    
    -- Metadata
    is_featured BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT uk_places_naver_place_id UNIQUE (naver_place_id),
    CONSTRAINT chk_places_rating CHECK (rating >= 0 AND rating <= 5),
    CONSTRAINT chk_places_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT chk_places_longitude CHECK (longitude >= -180 AND longitude <= 180)
);

-- Create spatial index alternative using B-tree for lat/lng
CREATE INDEX IF NOT EXISTS idx_places_lat_lng ON places (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_places_category ON places (category);
CREATE INDEX IF NOT EXISTS idx_places_rating ON places (rating DESC);

-- Create other tables that don't depend on PostGIS
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS travel_plans (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    status VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS itinerary_items (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    travel_plan_id VARCHAR(255) NOT NULL,
    place_id VARCHAR(255) NOT NULL,
    day_number INTEGER NOT NULL,
    order_index INTEGER NOT NULL,
    notes TEXT,
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id),
    FOREIGN KEY (place_id) REFERENCES places(id)
);

CREATE TABLE IF NOT EXISTS saved_plans (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    user_id VARCHAR(255) NOT NULL,
    travel_plan_id VARCHAR(255) NOT NULL,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id),
    CONSTRAINT uk_user_plan UNIQUE (user_id, travel_plan_id)
);