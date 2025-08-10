-- Oddiya Travel Planning Service Database Schema
-- Aurora PostgreSQL with Strategic Normalization and Denormalization
-- Version: 1.0
-- Date: 2025-01-05
--
-- =====================================================
-- 관계 유형 표기법 (Relationship Type Notation)
-- =====================================================
-- [IDENTIFYING]: 식별 관계 - 부모 테이블의 PK가 자식 테이블의 PK의 일부가 됨
-- [NON-IDENTIFYING]: 비식별 관계 - 부모 테이블의 PK가 자식 테이블의 일반 FK가 됨
-- [WEAK ENTITY]: 약한 엔티티 - 부모 없이는 존재할 수 없는 엔티티
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- For UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- For cryptographic functions (e.g., hashing passwords)
CREATE EXTENSION IF NOT EXISTS "postgis"; -- For geospatial queries and data types

-- Drop existing schema if exists
DROP SCHEMA IF EXISTS oddiya CASCADE;
CREATE SCHEMA oddiya;
SET search_path TO oddiya, public;

-- =====================================================
-- NORMALIZED TABLES (3NF+)
-- =====================================================

-- User Management Domain
DROP TABLE IF EXISTS "user" CASCADE;
CREATE TABLE "user" (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) UNIQUE NOT NULL,
    nickname VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    birth_date DATE,
    oauth_provider VARCHAR(20) NOT NULL CHECK (oauth_provider IN ('apple', 'google')),
    oauth_id VARCHAR(255) NOT NULL,
    profile_image_url TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    -- Denormalized fields for performance
    total_plans_created INTEGER DEFAULT 0,
    total_places_visited INTEGER DEFAULT 0,
    total_videos_generated INTEGER DEFAULT 0,
    UNIQUE(oauth_provider, oauth_id)
);

-- User Preferences (Normalized from user table)
-- [IDENTIFYING] [WEAK ENTITY] user_preference는 user의 확장 테이블로 user_id를 PK로 사용
DROP TABLE IF EXISTS user_preference CASCADE;
CREATE TABLE user_preference (
    user_id BIGINT PRIMARY KEY REFERENCES "user"(user_id) ON DELETE CASCADE, -- [IDENTIFYING]
    preferred_language VARCHAR(10) DEFAULT 'ko',
    notification_enabled BOOLEAN DEFAULT true,
    email_notifications BOOLEAN DEFAULT true,
    push_notifications BOOLEAN DEFAULT true,
    privacy_settings JSONB DEFAULT '{"profile_public": true, "plans_public": false}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- User Login History
-- [NON-IDENTIFYING] login_history는 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS user_login_history CASCADE;
CREATE TABLE user_login_history (
    login_history_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE, -- [NON-IDENTIFYING]
    login_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    device_type VARCHAR(50),
    device_id VARCHAR(255)
);

-- Travel Plan Domain
-- [NON-IDENTIFYING] travel_plan은 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS travel_plan CASCADE;
CREATE TABLE travel_plan (
    plan_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100),
    status VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'confirmed', 'in_progress', 'completed', 'cancelled')),
    is_public BOOLEAN DEFAULT false,
    created_by BIGINT NOT NULL REFERENCES "user"(user_id), -- [NON-IDENTIFYING]
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    -- Denormalized fields for performance
    total_days INTEGER GENERATED ALWAYS AS (end_date - start_date + 1) STORED,
    participant_count INTEGER DEFAULT 1,
    place_count INTEGER DEFAULT 0,
    total_estimated_cost DECIMAL(10,2),
    ai_generated BOOLEAN DEFAULT false,
    ai_model_version VARCHAR(50)
);

-- Plan Participants (Many-to-Many)
-- [IDENTIFYING] 연관 테이블로 plan_id와 user_id의 조합이 PK
DROP TABLE IF EXISTS plan_participant CASCADE;
CREATE TABLE plan_participant (
    plan_id BIGINT NOT NULL REFERENCES travel_plan(plan_id) ON DELETE CASCADE, -- [IDENTIFYING]
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE, -- [IDENTIFYING]
    role VARCHAR(20) DEFAULT 'member' CHECK (role IN ('owner', 'editor', 'viewer', 'member')),
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    invited_by BIGINT REFERENCES "user"(user_id), -- [NON-IDENTIFYING]
    PRIMARY KEY (plan_id, user_id) -- 복합 PK
);

-- Category Management (Normalized)
-- 독립 엔티티 (자기 참조 포함)
DROP TABLE IF EXISTS category CASCADE;
CREATE TABLE category (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(50) UNIQUE NOT NULL,
    category_slug VARCHAR(50) UNIQUE NOT NULL,
    parent_category_id INTEGER REFERENCES category(category_id), -- [NON-IDENTIFYING] 자기 참조
    icon_url TEXT,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true
);

-- Insert default categories
INSERT INTO category (category_name, category_slug) VALUES
('Restaurant', 'restaurant'),
('Cafe', 'cafe'),
('Tourist Attraction', 'tourist-attraction'),
('Hotel', 'hotel'),
('Shopping', 'shopping'),
('Transportation', 'transportation'),
('Entertainment', 'entertainment'),
('Nature', 'nature');

-- Content/Place Domain
-- [NON-IDENTIFYING] place는 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS place CASCADE;
CREATE TABLE place (
    place_id BIGSERIAL PRIMARY KEY,
    google_place_id VARCHAR(255) UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id INTEGER NOT NULL REFERENCES category(category_id), -- [NON-IDENTIFYING]
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    location GEOGRAPHY(POINT, 4326) NOT NULL, -- PostGIS type for geospatial queries
    phone_number VARCHAR(50),
    website_url TEXT,
    google_rating DECIMAL(2, 1) CHECK (google_rating >= 0 AND google_rating <= 5),
    google_rating_count INTEGER,
    price_level SMALLINT CHECK (price_level >= 0 AND price_level <= 4),
    is_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- Denormalized fields for performance
    popularity_score INTEGER DEFAULT 0,
    total_visits INTEGER DEFAULT 0,
    average_visit_duration INTERVAL
);

-- Place Details (Normalized attributes)
-- [IDENTIFYING] [WEAK ENTITY] place_detail은 place의 확장 테이블로 place_id를 PK로 사용
DROP TABLE IF EXISTS place_detail CASCADE;
CREATE TABLE place_detail (
    place_id BIGINT PRIMARY KEY REFERENCES place(place_id) ON DELETE CASCADE, -- [IDENTIFYING]
    opening_hours JSONB,
    amenities JSONB,
    features TEXT[],
    accessibility_features TEXT[],
    payment_methods TEXT[],
    cuisine_types TEXT[],
    reservation_required BOOLEAN,
    kid_friendly BOOLEAN,
    pet_friendly BOOLEAN,
    parking_available BOOLEAN,
    wifi_available BOOLEAN,
    outdoor_seating BOOLEAN
);

-- Place Photos (Normalized)
-- [NON-IDENTIFYING] place_photo는 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS place_photo CASCADE;
CREATE TABLE place_photo (
    photo_id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL REFERENCES place(place_id) ON DELETE CASCADE, -- [NON-IDENTIFYING]
    photo_url TEXT NOT NULL,
    photo_reference VARCHAR(255),
    thumbnail_url TEXT,
    width INTEGER,
    height INTEGER,
    attribution TEXT,
    uploaded_by BIGINT REFERENCES "user"(user_id),
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Plan Details (Places in a plan)
-- [NON-IDENTIFYING] plan_place는 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS plan_place CASCADE;
CREATE TABLE plan_place (
    plan_place_id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES travel_plan(plan_id) ON DELETE CASCADE, -- [NON-IDENTIFYING]
    place_id BIGINT NOT NULL REFERENCES place(place_id), -- [NON-IDENTIFYING]
    visit_date DATE NOT NULL,
    visit_order SMALLINT NOT NULL,
    start_time TIME,
    end_time TIME,
    estimated_duration INTERVAL,
    actual_duration INTERVAL,
    notes TEXT,
    transportation_mode VARCHAR(50),
    transportation_duration INTERVAL,
    transportation_distance DECIMAL(10,2), -- in kilometers
    estimated_cost DECIMAL(10,2),
    actual_cost DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'planned' CHECK (status IN ('planned', 'visited', 'skipped')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plan_id, visit_date, visit_order)
);

-- User votes/ratings for places in plans
-- [IDENTIFYING] 연관 테이블로 plan_place_id와 user_id의 조합이 PK
DROP TABLE IF EXISTS plan_place_vote CASCADE;
CREATE TABLE plan_place_vote (
    plan_place_id BIGINT NOT NULL REFERENCES plan_place(plan_place_id) ON DELETE CASCADE, -- [IDENTIFYING]
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE, -- [IDENTIFYING]
    vote_type VARCHAR(20) NOT NULL CHECK (vote_type IN ('like', 'dislike', 'neutral')),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_place_id, user_id) -- 복합 PK
);

-- Hashtag Domain
DROP TABLE IF EXISTS hashtag CASCADE;
CREATE TABLE hashtag (
    hashtag_id BIGSERIAL PRIMARY KEY,
    tag_name VARCHAR(100) UNIQUE NOT NULL,
    tag_slug VARCHAR(100) UNIQUE NOT NULL,
    usage_count INTEGER DEFAULT 0,
    trending_score INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_trending_at TIMESTAMP WITH TIME ZONE
);

-- Place Hashtags (Many-to-Many)
-- [IDENTIFYING] 연관 테이블로 place_id와 hashtag_id의 조합이 PK
DROP TABLE IF EXISTS place_hashtag CASCADE;
CREATE TABLE place_hashtag (
    place_id BIGINT NOT NULL REFERENCES place(place_id) ON DELETE CASCADE, -- [IDENTIFYING]
    hashtag_id BIGINT NOT NULL REFERENCES hashtag(hashtag_id) ON DELETE CASCADE, -- [IDENTIFYING]
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "user"(user_id), -- [NON-IDENTIFYING]
    PRIMARY KEY (place_id, hashtag_id) -- 복합 PK
);

-- Video/Shorts Domain
-- [NON-IDENTIFYING] video_generation_job는 독립적인 엔티티로 UUID를 PK로 사용
DROP TABLE IF EXISTS video_generation_job CASCADE;
CREATE TABLE video_generation_job (
    job_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE, -- [NON-IDENTIFYING]
    plan_id BIGINT REFERENCES travel_plan(plan_id) ON DELETE SET NULL, -- [NON-IDENTIFYING] 선택적
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    job_type VARCHAR(20) DEFAULT 'shorts' CHECK (job_type IN ('shorts', 'slideshow', 'compilation')),
    input_images JSONB NOT NULL,
    music_url TEXT,
    ai_recommendations JSONB,
    output_video_url TEXT,
    output_thumbnail_url TEXT,
    video_duration INTEGER, -- in seconds
    video_size_bytes BIGINT,
    processing_time_ms INTEGER,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    ttl TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP + INTERVAL '30 days'
);

-- User Generated Content
-- [NON-IDENTIFYING] user_content는 독립적인 엔티티로 자체 PK를 가짐
DROP TABLE IF EXISTS user_content CASCADE;
CREATE TABLE user_content (
    content_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE, -- [NON-IDENTIFYING]
    plan_id BIGINT REFERENCES travel_plan(plan_id) ON DELETE SET NULL, -- [NON-IDENTIFYING] 선택적
    place_id BIGINT REFERENCES place(place_id) ON DELETE SET NULL, -- [NON-IDENTIFYING] 선택적
    content_type VARCHAR(20) NOT NULL CHECK (content_type IN ('photo', 'video', 'review')),
    content_url TEXT,
    thumbnail_url TEXT,
    content_text TEXT,
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    is_public BOOLEAN DEFAULT true,
    view_count INTEGER DEFAULT 0,
    like_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- DENORMALIZED TABLES FOR PERFORMANCE
-- =====================================================

-- Popular Places View (Materialized View for performance)
-- RDB 캐시 테이블 대신 ElastiCache 사용 권장
-- 필요시 아래 Materialized View 활용 가능
DROP MATERIALIZED VIEW IF EXISTS popular_places_view CASCADE;
CREATE MATERIALIZED VIEW popular_places_view AS
SELECT 
    p.place_id,
    p.name as place_name,
    p.city,
    p.country,
    c.category_name,
    p.google_rating,
    p.google_rating_count,
    p.price_level,
    p.popularity_score,
    p.total_visits,
    pp.photo_url as primary_photo_url,
    pp.thumbnail_url as primary_thumbnail_url,
    COALESCE(avg_cost.average_cost, 0) as average_estimated_cost
FROM place p
JOIN category c ON p.category_id = c.category_id
LEFT JOIN place_photo pp ON p.place_id = pp.place_id AND pp.is_primary = true
LEFT JOIN LATERAL (
    SELECT AVG(estimated_cost) as average_cost
    FROM plan_place
    WHERE place_id = p.place_id
) avg_cost ON true
WHERE p.is_verified = true;

-- 인덱스 생성
CREATE INDEX idx_popular_places_city_score 
ON popular_places_view(city, popularity_score DESC);

CREATE INDEX idx_popular_places_category 
ON popular_places_view(category_name, popularity_score DESC);

-- User Activity Summary (Denormalized for quick access)
-- [IDENTIFYING] [WEAK ENTITY] user_activity_summary는 user의 요약 테이블로 user_id를 PK로 사용
DROP TABLE IF EXISTS user_activity_summary CASCADE;
CREATE TABLE user_activity_summary (
    user_id BIGINT PRIMARY KEY REFERENCES "user"(user_id) ON DELETE CASCADE, -- [IDENTIFYING]
    last_30_days_login_count INTEGER DEFAULT 0,
    last_30_days_plans_created INTEGER DEFAULT 0,
    last_30_days_places_visited INTEGER DEFAULT 0,
    last_30_days_videos_generated INTEGER DEFAULT 0,
    favorite_city VARCHAR(100),
    favorite_category VARCHAR(50),
    total_spending DECIMAL(10,2),
    last_activity_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Plan Summary View (Materialized View for performance)
DROP MATERIALIZED VIEW IF EXISTS plan_summary_view CASCADE;
CREATE MATERIALIZED VIEW plan_summary_view AS
SELECT 
    p.plan_id,
    p.title,
    p.city,
    p.start_date,
    p.end_date,
    p.status,
    p.created_by,
    u.nickname as creator_nickname,
    u.profile_image_url as creator_profile_image,
    p.participant_count,
    p.place_count,
    p.total_estimated_cost,
    COALESCE(ph.primary_photo_url, '') as primary_photo_url,
    COALESCE(array_agg(DISTINCT pt.tag_name) FILTER (WHERE pt.tag_name IS NOT NULL), ARRAY[]::varchar[]) as tags
FROM travel_plan p
JOIN "user" u ON p.created_by = u.user_id
LEFT JOIN LATERAL (
    SELECT pp.photo_url as primary_photo_url
    FROM plan_place ppl
    JOIN place pl ON ppl.place_id = pl.place_id
    JOIN place_photo pp ON pl.place_id = pp.place_id
    WHERE ppl.plan_id = p.plan_id AND pp.is_primary = true
    LIMIT 1
) ph ON true
LEFT JOIN plan_place ppl ON p.plan_id = ppl.plan_id
LEFT JOIN place pl ON ppl.place_id = pl.place_id
LEFT JOIN place_hashtag pht ON pl.place_id = pht.place_id
LEFT JOIN hashtag pt ON pht.hashtag_id = pt.hashtag_id
WHERE p.deleted_at IS NULL
GROUP BY p.plan_id, p.title, p.city, p.start_date, p.end_date, p.status, 
         p.created_by, u.nickname, u.profile_image_url, p.participant_count, 
         p.place_count, p.total_estimated_cost, ph.primary_photo_url;

CREATE INDEX idx_plan_summary_view_dates ON plan_summary_view(start_date, end_date);
CREATE INDEX idx_plan_summary_view_city ON plan_summary_view(city);
CREATE INDEX idx_plan_summary_view_creator ON plan_summary_view(created_by);

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================

-- User indexes
CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_oauth ON "user"(oauth_provider, oauth_id);
CREATE INDEX idx_user_nickname ON "user"(nickname);
CREATE INDEX idx_user_active ON "user"(is_active) WHERE is_active = true;

-- Login history indexes
CREATE INDEX idx_login_history_user_time ON user_login_history(user_id, login_at DESC);
CREATE INDEX idx_login_history_device ON user_login_history(device_id);

-- Travel plan indexes
CREATE INDEX idx_travel_plan_user ON travel_plan(created_by);
CREATE INDEX idx_travel_plan_dates ON travel_plan(start_date, end_date);
CREATE INDEX idx_travel_plan_status ON travel_plan(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_travel_plan_city ON travel_plan(city);
CREATE INDEX idx_travel_plan_public ON travel_plan(is_public) WHERE is_public = true AND deleted_at IS NULL;

-- Place indexes
CREATE INDEX idx_place_location ON place USING GIST(location);
CREATE INDEX idx_place_city ON place(city);
CREATE INDEX idx_place_google_id ON place(google_place_id);
CREATE INDEX idx_place_category ON place(category_id);
CREATE INDEX idx_place_popularity ON place(popularity_score DESC);

-- Plan place indexes
CREATE INDEX idx_plan_place_date ON plan_place(plan_id, visit_date, visit_order);
CREATE INDEX idx_plan_place_status ON plan_place(status);

-- Hashtag indexes
CREATE INDEX idx_hashtag_name ON hashtag(tag_name);
CREATE INDEX idx_hashtag_slug ON hashtag(tag_slug);
CREATE INDEX idx_hashtag_trending ON hashtag(trending_score DESC);

-- Video job indexes
CREATE INDEX idx_video_job_user ON video_generation_job(user_id, created_at DESC);
CREATE INDEX idx_video_job_status ON video_generation_job(status) WHERE status IN ('pending', 'processing');
CREATE INDEX idx_video_job_ttl ON video_generation_job(ttl);

-- Content indexes
CREATE INDEX idx_content_user_type ON user_content(user_id, content_type);
CREATE INDEX idx_content_place ON user_content(place_id);
CREATE INDEX idx_content_public ON user_content(is_public) WHERE is_public = true;

-- =====================================================
-- TRIGGERS AND FUNCTIONS
-- =====================================================

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply update trigger to relevant tables
CREATE TRIGGER update_user_updated_at BEFORE UPDATE ON "user"
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    
CREATE TRIGGER update_user_preference_updated_at BEFORE UPDATE ON user_preference
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    
CREATE TRIGGER update_travel_plan_updated_at BEFORE UPDATE ON travel_plan
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    
CREATE TRIGGER update_place_updated_at BEFORE UPDATE ON place
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    
CREATE TRIGGER update_plan_place_updated_at BEFORE UPDATE ON plan_place
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Update denormalized counts
CREATE OR REPLACE FUNCTION update_user_plan_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE "user" SET total_plans_created = total_plans_created + 1
        WHERE user_id = NEW.created_by;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE "user" SET total_plans_created = total_plans_created - 1
        WHERE user_id = OLD.created_by;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_plan_count_trigger
AFTER INSERT OR DELETE ON travel_plan
FOR EACH ROW EXECUTE FUNCTION update_user_plan_count();

-- Update plan participant count
CREATE OR REPLACE FUNCTION update_plan_participant_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE travel_plan SET participant_count = participant_count + 1
        WHERE plan_id = NEW.plan_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE travel_plan SET participant_count = participant_count - 1
        WHERE plan_id = OLD.plan_id;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_plan_participant_count_trigger
AFTER INSERT OR DELETE ON plan_participant
FOR EACH ROW EXECUTE FUNCTION update_plan_participant_count();

-- Update plan place count
CREATE OR REPLACE FUNCTION update_plan_place_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE travel_plan SET place_count = place_count + 1
        WHERE plan_id = NEW.plan_id;
        UPDATE place SET total_visits = total_visits + 1
        WHERE place_id = NEW.place_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE travel_plan SET place_count = place_count - 1
        WHERE plan_id = OLD.plan_id;
        UPDATE place SET total_visits = total_visits - 1
        WHERE place_id = OLD.place_id;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_plan_place_count_trigger
AFTER INSERT OR DELETE ON plan_place
FOR EACH ROW EXECUTE FUNCTION update_plan_place_count();

-- Update hashtag usage count
CREATE OR REPLACE FUNCTION update_hashtag_usage_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE hashtag SET usage_count = usage_count + 1
        WHERE hashtag_id = NEW.hashtag_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE hashtag SET usage_count = usage_count - 1
        WHERE hashtag_id = OLD.hashtag_id;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_hashtag_usage_count_trigger
AFTER INSERT OR DELETE ON place_hashtag
FOR EACH ROW EXECUTE FUNCTION update_hashtag_usage_count();

-- Update user video count
CREATE OR REPLACE FUNCTION update_user_video_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'completed' THEN
        UPDATE "user" SET total_videos_generated = total_videos_generated + 1
        WHERE user_id = NEW.user_id;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_video_count_trigger
AFTER INSERT OR UPDATE OF status ON video_generation_job
FOR EACH ROW WHEN (NEW.status = 'completed')
EXECUTE FUNCTION update_user_video_count();

-- Function to calculate place popularity score
CREATE OR REPLACE FUNCTION calculate_place_popularity_score(p_place_id BIGINT)
RETURNS INTEGER AS $$
DECLARE
    v_score INTEGER := 0;
    v_rating_score INTEGER;
    v_visit_score INTEGER;
    v_content_score INTEGER;
BEGIN
    -- Rating component (0-50 points)
    SELECT 
        COALESCE(google_rating * 10, 0) INTO v_rating_score
    FROM place 
    WHERE place_id = p_place_id;
    
    -- Visit component (0-30 points)
    SELECT 
        LEAST(total_visits, 30) INTO v_visit_score
    FROM place 
    WHERE place_id = p_place_id;
    
    -- Content component (0-20 points)
    SELECT 
        LEAST(COUNT(*), 20) INTO v_content_score
    FROM user_content 
    WHERE place_id = p_place_id AND is_public = true;
    
    v_score := v_rating_score + v_visit_score + v_content_score;
    
    RETURN v_score;
END;
$$ LANGUAGE plpgsql;

-- Refresh materialized view function
CREATE OR REPLACE FUNCTION refresh_plan_summary_view()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY plan_summary_view;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- SECURITY POLICIES (Row Level Security)
-- =====================================================

-- Enable RLS on sensitive tables
ALTER TABLE "user" ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE travel_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE plan_participant ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_content ENABLE ROW LEVEL SECURITY;

-- User policies
CREATE POLICY user_select_policy ON "user"
    FOR SELECT USING (true);

CREATE POLICY user_update_policy ON "user"
    FOR UPDATE USING (user_id = current_setting('app.current_user_id')::BIGINT);

-- User preference policies
CREATE POLICY user_preference_owner_policy ON user_preference
    FOR ALL USING (user_id = current_setting('app.current_user_id')::BIGINT);

-- Travel plan policies
CREATE POLICY travel_plan_select_policy ON travel_plan
    FOR SELECT USING (
        is_public = true 
        OR created_by = current_setting('app.current_user_id')::BIGINT
        OR EXISTS (
            SELECT 1 FROM plan_participant 
            WHERE plan_id = travel_plan.plan_id 
            AND user_id = current_setting('app.current_user_id')::BIGINT
        )
    );

CREATE POLICY travel_plan_insert_policy ON travel_plan
    FOR INSERT WITH CHECK (created_by = current_setting('app.current_user_id')::BIGINT);

CREATE POLICY travel_plan_update_policy ON travel_plan
    FOR UPDATE USING (
        created_by = current_setting('app.current_user_id')::BIGINT
        OR EXISTS (
            SELECT 1 FROM plan_participant 
            WHERE plan_id = travel_plan.plan_id 
            AND user_id = current_setting('app.current_user_id')::BIGINT
            AND role IN ('owner', 'editor')
        )
    );

-- =====================================================
-- RELATIONSHIP SUMMARY (관계 요약)
-- =====================================================
-- 식별 관계 (IDENTIFYING RELATIONSHIPS):
-- 1. user → user_preference (1:1, user_id가 PK)
-- 2. user → user_activity_summary (1:1, user_id가 PK)
-- 3. place → place_detail (1:1, place_id가 PK)
-- 4. (plan, user) → plan_participant (M:N, 복합 PK)
-- 5. (place, hashtag) → place_hashtag (M:N, 복합 PK)
-- 6. (plan_place, user) → plan_place_vote (M:N, 복합 PK)
--
-- 비식별 관계 (NON-IDENTIFYING RELATIONSHIPS):
-- 1. user → user_login_history (1:N)
-- 2. user → travel_plan (1:N)
-- 3. user → video_generation_job (1:N)
-- 4. user → user_content (1:N)
-- 5. category → place (1:N)
-- 6. place → place_photo (1:N)
-- 7. travel_plan → plan_place (1:N)
-- 8. place → plan_place (1:N)
-- 9. category → category (자기 참조, 1:N)
-- =====================================================

-- =====================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON SCHEMA oddiya IS 'Oddiya travel planning and video generation service database schema';

COMMENT ON TABLE "user" IS 'User account information with OAuth integration and denormalized activity counts';
COMMENT ON TABLE user_preference IS 'User preferences and settings, normalized from user table';
COMMENT ON TABLE travel_plan IS 'Travel plans created by users with denormalized counts for performance';
COMMENT ON TABLE place IS 'Places/locations with PostGIS geography for spatial queries';
COMMENT ON TABLE plan_place IS 'Places included in specific travel plans with visit details';
COMMENT ON TABLE video_generation_job IS 'Async video generation job tracking aligned with serverless architecture';
COMMENT ON MATERIALIZED VIEW popular_places_view IS 'Materialized view for popular places, use ElastiCache for actual caching';
COMMENT ON TABLE user_activity_summary IS 'Denormalized user activity metrics for analytics';

COMMENT ON COLUMN place.location IS 'PostGIS geography point for spatial queries and distance calculations';
COMMENT ON COLUMN place.price_level IS '0=Free, 1=Inexpensive, 2=Moderate, 3=Expensive, 4=Very Expensive';
COMMENT ON COLUMN video_generation_job.ttl IS 'Time to live for automatic cleanup via AWS lifecycle';
COMMENT ON COLUMN travel_plan.total_days IS 'Generated column for quick duration calculations';

-- =====================================================
-- INITIAL DATA SETUP
-- =====================================================

-- Create default admin user (for testing)
INSERT INTO "user" (email, nickname, full_name, oauth_provider, oauth_id)
VALUES ('admin@oddiya.com', 'admin', 'System Admin', 'google', 'system-admin-001')
ON CONFLICT (email) DO NOTHING;

-- Refresh materialized views
SELECT refresh_plan_summary_view();

-- =====================================================
-- MAINTENANCE PROCEDURES
-- =====================================================

-- Procedure to clean up expired video jobs
CREATE OR REPLACE PROCEDURE cleanup_expired_video_jobs()
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM video_generation_job
    WHERE ttl < CURRENT_TIMESTAMP;
    
    RAISE NOTICE 'Cleaned up % expired video jobs', ROW_COUNT;
END;
$$;

-- Procedure to refresh popular places view
CREATE OR REPLACE PROCEDURE refresh_popular_places_view()
LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY popular_places_view;
    RAISE NOTICE 'Refreshed popular places view';
END;
$$;

-- Schedule maintenance jobs (to be run by external scheduler)
-- CALL cleanup_expired_video_jobs();
-- CALL refresh_popular_places_view();
-- SELECT refresh_plan_summary_view();

-- Note: 인기 장소 캐싱은 ElastiCache 사용 권장
-- Redis example:
-- SET popular_places:seoul "{json_data}" EX 3600
-- GET popular_places:seoul