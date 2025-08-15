-- Create travel plans table and related tables
-- Migration: V003__create_travel_plans_table.sql

-- Create travel plan status enum type
CREATE TYPE travel_plan_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'PRIVATE');

-- Create travel plans table
CREATE TABLE travel_plans (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- User relationship
    user_id VARCHAR(255) NOT NULL,
    
    -- Plan details
    title VARCHAR(255) NOT NULL,
    description TEXT,
    destination VARCHAR(255) NOT NULL,
    
    -- Dates and logistics
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    number_of_people INTEGER,
    budget DECIMAL(12,2),
    
    -- Status and visibility
    status travel_plan_status NOT NULL DEFAULT 'DRAFT',
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    is_ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Media
    cover_image_url TEXT,
    
    -- Metrics
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    share_count BIGINT NOT NULL DEFAULT 0,
    save_count BIGINT NOT NULL DEFAULT 0,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_number_of_people CHECK (number_of_people > 0),
    CONSTRAINT chk_budget CHECK (budget >= 0)
);

-- Create indexes for travel plans
CREATE INDEX idx_travel_plan_user ON travel_plans (user_id);
CREATE INDEX idx_travel_plan_status ON travel_plans (status);
CREATE INDEX idx_travel_plan_destination ON travel_plans (destination);
CREATE INDEX idx_travel_plan_dates ON travel_plans (start_date, end_date);
CREATE INDEX idx_travel_plan_public ON travel_plans (is_public);
CREATE INDEX idx_travel_plan_created_at ON travel_plans (created_at);
CREATE INDEX idx_travel_plan_popularity ON travel_plans (like_count DESC, view_count DESC);

-- Create travel plan preferences table
CREATE TABLE travel_plan_preferences (
    travel_plan_id VARCHAR(255) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (travel_plan_id, preference_key),
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE
);

-- Create travel plan tags table
CREATE TABLE travel_plan_tags (
    travel_plan_id VARCHAR(255) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (travel_plan_id, tag),
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE
);

CREATE INDEX idx_travel_plan_tags_tag ON travel_plan_tags (tag);

-- Create travel plan collaborators table (many-to-many)
CREATE TABLE travel_plan_collaborators (
    travel_plan_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'VIEWER',
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (travel_plan_id, user_id),
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_collaborator_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

CREATE INDEX idx_collaborators_user ON travel_plan_collaborators (user_id);
CREATE INDEX idx_collaborators_role ON travel_plan_collaborators (role);

-- Apply updated_at trigger to travel plans table
CREATE TRIGGER trigger_travel_plans_updated_at
    BEFORE UPDATE ON travel_plans
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to calculate trip duration in days
CREATE OR REPLACE FUNCTION calculate_trip_duration(start_date DATE, end_date DATE)
RETURNS INTEGER AS $$
BEGIN
    RETURN CASE 
        WHEN end_date >= start_date THEN (end_date - start_date) + 1
        ELSE 0
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Add calculated column for trip duration (virtual column simulation)
ALTER TABLE travel_plans ADD COLUMN duration_days INTEGER 
GENERATED ALWAYS AS (calculate_trip_duration(start_date, end_date)) STORED;

-- Function to update popularity score based on metrics
CREATE OR REPLACE FUNCTION update_travel_plan_popularity()
RETURNS TRIGGER AS $$
BEGIN
    -- Simple popularity score calculation
    -- Weights: likes (40%), saves (30%), shares (20%), views (10%)
    NEW.popularity_score := (
        (COALESCE(NEW.like_count, 0) * 0.4) + 
        (COALESCE(NEW.save_count, 0) * 0.3) + 
        (COALESCE(NEW.share_count, 0) * 0.2) + 
        (COALESCE(NEW.view_count, 0) * 0.1 / 100.0)
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Add popularity score column
ALTER TABLE travel_plans ADD COLUMN popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0.0;

-- Apply popularity update trigger
CREATE TRIGGER trigger_travel_plan_popularity
    BEFORE UPDATE OF like_count, save_count, share_count, view_count ON travel_plans
    FOR EACH ROW
    EXECUTE FUNCTION update_travel_plan_popularity();

-- Create view for public travel plans with user info
CREATE VIEW public_travel_plans AS
SELECT 
    tp.*,
    u.nickname as author_nickname,
    u.profile_image_url as author_profile_image,
    ARRAY_AGG(DISTINCT tpt.tag) FILTER (WHERE tpt.tag IS NOT NULL) as tags
FROM travel_plans tp
JOIN users u ON tp.user_id = u.id
LEFT JOIN travel_plan_tags tpt ON tp.id = tpt.travel_plan_id
WHERE tp.is_public = true AND tp.status = 'PUBLISHED'
GROUP BY tp.id, u.nickname, u.profile_image_url;

-- Add comments
COMMENT ON TABLE travel_plans IS 'User-created travel plans and itineraries';
COMMENT ON TYPE travel_plan_status IS 'Status of travel plan (DRAFT, PUBLISHED, ARCHIVED, PRIVATE)';
COMMENT ON COLUMN travel_plans.is_ai_generated IS 'Whether this plan was generated by AI';
COMMENT ON COLUMN travel_plans.duration_days IS 'Calculated duration of trip in days';
COMMENT ON COLUMN travel_plans.popularity_score IS 'Calculated popularity score based on engagement metrics';
COMMENT ON TABLE travel_plan_preferences IS 'Travel plan specific preferences and settings';
COMMENT ON TABLE travel_plan_tags IS 'Tags associated with travel plans';
COMMENT ON TABLE travel_plan_collaborators IS 'Users who can collaborate on travel plans';
COMMENT ON VIEW public_travel_plans IS 'Public travel plans with author information and tags';