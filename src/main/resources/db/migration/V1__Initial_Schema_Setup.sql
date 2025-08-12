-- =====================================================
-- Oddiya Travel Planning Service - Initial Database Schema
-- Version: 1.0
-- Date: 2025-08-12
-- Database: Aurora PostgreSQL 15 with PostGIS
-- Migration: V1__Initial_Schema_Setup.sql
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";       -- For UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";        -- For cryptographic functions
CREATE EXTENSION IF NOT EXISTS "postgis";         -- For geospatial queries and data types

-- =====================================================
-- CORE TABLES (Aligned with JPA Entities)
-- =====================================================

-- Users table (primary entity for authentication and user management)
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email VARCHAR(320) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE,
    password VARCHAR(255),
    nickname VARCHAR(50) NOT NULL,
    profile_image_url TEXT,
    bio VARCHAR(500),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('google', 'apple')),
    provider_id VARCHAR(255) NOT NULL,
    is_email_verified BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    refresh_token TEXT,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(provider, provider_id)
);

-- User preferences (normalized from users table)
CREATE TABLE user_preferences (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (user_id, preference_key)
);

-- User travel preferences (specific to travel planning)
CREATE TABLE user_travel_preferences (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (user_id, preference_key)
);

-- User followers (many-to-many relationship)
CREATE TABLE user_followers (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    follower_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, follower_id),
    CHECK (user_id != follower_id)
);

-- Places table with PostGIS spatial support
CREATE TABLE places (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    naver_place_id VARCHAR(255) UNIQUE,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    address TEXT NOT NULL,
    road_address TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    phone_number VARCHAR(50),
    website TEXT,
    rating DOUBLE PRECISION,
    review_count INTEGER DEFAULT 0,
    bookmark_count INTEGER DEFAULT 0,
    view_count BIGINT DEFAULT 0,
    is_verified BOOLEAN DEFAULT FALSE,
    popularity_score DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CHECK (latitude >= -90 AND latitude <= 90),
    CHECK (longitude >= -180 AND longitude <= 180),
    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);

-- Place opening hours
CREATE TABLE place_opening_hours (
    place_id VARCHAR(36) NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    hours VARCHAR(255),
    PRIMARY KEY (place_id, day_of_week)
);

-- Place images
CREATE TABLE place_images (
    id BIGSERIAL PRIMARY KEY,
    place_id VARCHAR(36) NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Place tags
CREATE TABLE place_tags (
    id BIGSERIAL PRIMARY KEY,
    place_id VARCHAR(36) NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(place_id, tag)
);

-- Travel plans table
CREATE TABLE travel_plans (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    destination VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    number_of_people INTEGER,
    budget DECIMAL(12,2),
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    is_public BOOLEAN DEFAULT FALSE,
    is_ai_generated BOOLEAN DEFAULT FALSE,
    view_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    share_count BIGINT DEFAULT 0,
    save_count BIGINT DEFAULT 0,
    cover_image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CHECK (end_date >= start_date),
    CHECK (number_of_people > 0)
);

-- Travel plan preferences
CREATE TABLE travel_plan_preferences (
    travel_plan_id VARCHAR(36) NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (travel_plan_id, preference_key)
);

-- Travel plan tags
CREATE TABLE travel_plan_tags (
    id BIGSERIAL PRIMARY KEY,
    travel_plan_id VARCHAR(36) NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    UNIQUE(travel_plan_id, tag)
);

-- Travel plan collaborators (many-to-many)
CREATE TABLE travel_plan_collaborators (
    travel_plan_id VARCHAR(36) NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER', 'MEMBER')),
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (travel_plan_id, user_id)
);

-- Itinerary items (places within travel plans)
CREATE TABLE itinerary_items (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    travel_plan_id VARCHAR(36) NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
    place_id VARCHAR(36) NOT NULL REFERENCES places(id),
    day_number INTEGER NOT NULL,
    sequence INTEGER NOT NULL,
    start_time TIME,
    end_time TIME,
    notes TEXT,
    estimated_cost DECIMAL(10,2),
    actual_cost DECIMAL(10,2),
    transportation_mode VARCHAR(50),
    transportation_duration INTERVAL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CHECK (day_number > 0),
    CHECK (sequence > 0),
    CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time),
    UNIQUE(travel_plan_id, day_number, sequence)
);

-- Reviews table
CREATE TABLE reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    place_id VARCHAR(36) NOT NULL REFERENCES places(id),
    travel_plan_id VARCHAR(36) REFERENCES travel_plans(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    content TEXT,
    is_public BOOLEAN DEFAULT TRUE,
    helpful_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, place_id, travel_plan_id)
);

-- Videos table (for travel content generation)
CREATE TABLE videos (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    travel_plan_id VARCHAR(36) REFERENCES travel_plans(id),
    title VARCHAR(200),
    description TEXT,
    video_url TEXT,
    thumbnail_url TEXT,
    duration INTEGER, -- in seconds
    file_size BIGINT, -- in bytes
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    processing_progress INTEGER DEFAULT 0 CHECK (processing_progress >= 0 AND processing_progress <= 100),
    is_public BOOLEAN DEFAULT TRUE,
    view_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Saved plans (users can save other users' plans)
CREATE TABLE saved_plans (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    travel_plan_id VARCHAR(36) NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, travel_plan_id)
);

-- =====================================================
-- PERFORMANCE INDEXES
-- =====================================================

-- User indexes
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_provider_id ON users(provider, provider_id);
CREATE INDEX idx_user_active ON users(is_active) WHERE is_active = true AND is_deleted = false;
CREATE INDEX idx_user_created_at ON users(created_at);

-- Place indexes (including spatial)
CREATE INDEX idx_place_location_gist ON places USING GIST(location);
CREATE INDEX idx_place_category ON places(category);
CREATE INDEX idx_place_naver ON places(naver_place_id) WHERE naver_place_id IS NOT NULL;
CREATE INDEX idx_place_popularity ON places(popularity_score DESC);
CREATE INDEX idx_place_rating ON places(rating DESC) WHERE rating IS NOT NULL;
CREATE INDEX idx_place_coordinates ON places(latitude, longitude);
CREATE INDEX idx_place_verified ON places(is_verified) WHERE is_verified = true AND is_deleted = false;

-- Travel plan indexes
CREATE INDEX idx_travel_plan_user ON travel_plans(user_id);
CREATE INDEX idx_travel_plan_status ON travel_plans(status) WHERE is_deleted = false;
CREATE INDEX idx_travel_plan_dates ON travel_plans(start_date, end_date);
CREATE INDEX idx_travel_plan_destination ON travel_plans(destination);
CREATE INDEX idx_travel_plan_public ON travel_plans(is_public) WHERE is_public = true AND is_deleted = false;
CREATE INDEX idx_travel_plan_created_at ON travel_plans(created_at);

-- Itinerary items indexes
CREATE INDEX idx_itinerary_plan_day ON itinerary_items(travel_plan_id, day_number, sequence);
CREATE INDEX idx_itinerary_place ON itinerary_items(place_id);

-- Review indexes
CREATE INDEX idx_review_place ON reviews(place_id) WHERE is_deleted = false;
CREATE INDEX idx_review_user ON reviews(user_id) WHERE is_deleted = false;
CREATE INDEX idx_review_rating ON reviews(rating);
CREATE INDEX idx_review_public ON reviews(is_public) WHERE is_public = true AND is_deleted = false;

-- Video indexes
CREATE INDEX idx_video_user ON videos(user_id) WHERE is_deleted = false;
CREATE INDEX idx_video_plan ON videos(travel_plan_id) WHERE travel_plan_id IS NOT NULL AND is_deleted = false;
CREATE INDEX idx_video_status ON videos(status);
CREATE INDEX idx_video_public ON videos(is_public) WHERE is_public = true AND is_deleted = false;

-- Follower indexes
CREATE INDEX idx_follower_user ON user_followers(user_id);
CREATE INDEX idx_follower_follower ON user_followers(follower_id);

-- =====================================================
-- TRIGGERS FOR UPDATED_AT
-- =====================================================

-- Function to update the updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers to relevant tables
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_places_updated_at BEFORE UPDATE ON places
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_travel_plans_updated_at BEFORE UPDATE ON travel_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_itinerary_items_updated_at BEFORE UPDATE ON itinerary_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reviews_updated_at BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_videos_updated_at BEFORE UPDATE ON videos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saved_plans_updated_at BEFORE UPDATE ON saved_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON TABLE users IS 'User accounts with OAuth integration from Google/Apple via Supabase';
COMMENT ON TABLE places IS 'Places/locations with PostGIS geography for spatial queries, integrated with Naver Maps API';
COMMENT ON TABLE travel_plans IS 'AI-generated and user-created travel plans with social features';
COMMENT ON TABLE itinerary_items IS 'Individual items within travel plans, linking places to specific days and times';
COMMENT ON TABLE reviews IS 'User reviews and ratings for places, supporting both standalone and travel-plan-specific reviews';
COMMENT ON TABLE videos IS 'Travel videos generated from photos with AI assistance';

COMMENT ON COLUMN places.location IS 'PostGIS geography point for spatial queries and distance calculations';
COMMENT ON COLUMN places.naver_place_id IS 'Integration key for Naver Maps API place data synchronization';
COMMENT ON COLUMN travel_plans.is_ai_generated IS 'Flag indicating if the travel plan was generated using AWS Bedrock AI';
COMMENT ON COLUMN videos.status IS 'Video processing status for async generation workflow';