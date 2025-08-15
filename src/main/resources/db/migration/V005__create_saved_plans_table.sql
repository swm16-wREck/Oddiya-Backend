-- Create saved plans and additional tables
-- Migration: V005__create_saved_plans_table.sql

-- Create saved plans table (user bookmarks of travel plans)
CREATE TABLE saved_plans (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Relationships
    user_id VARCHAR(255) NOT NULL,
    travel_plan_id VARCHAR(255) NOT NULL,
    
    -- Metadata
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT, -- User's personal notes about why they saved this plan
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    
    CONSTRAINT uk_saved_plans_user_plan UNIQUE (user_id, travel_plan_id)
);

-- Create indexes for saved plans
CREATE INDEX idx_saved_plans_user ON saved_plans (user_id);
CREATE INDEX idx_saved_plans_travel_plan ON saved_plans (travel_plan_id);
CREATE INDEX idx_saved_plans_saved_at ON saved_plans (saved_at DESC);

-- Apply updated_at trigger to saved plans table
CREATE TRIGGER trigger_saved_plans_updated_at
    BEFORE UPDATE ON saved_plans
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create video status enum type
CREATE TYPE video_status AS ENUM ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'ARCHIVED');

-- Create videos table
CREATE TABLE videos (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Relationships
    user_id VARCHAR(255) NOT NULL,
    travel_plan_id VARCHAR(255),
    place_id VARCHAR(255),
    
    -- Video details
    title VARCHAR(255) NOT NULL,
    description TEXT,
    filename VARCHAR(255),
    s3_key VARCHAR(500),
    thumbnail_url TEXT,
    
    -- Video metadata
    duration_seconds INTEGER,
    file_size_bytes BIGINT,
    format VARCHAR(50),
    resolution VARCHAR(20),
    
    -- Status and processing
    status video_status NOT NULL DEFAULT 'UPLOADING',
    processing_progress INTEGER DEFAULT 0,
    error_message TEXT,
    
    -- Metrics
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    
    -- Visibility
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_video_duration CHECK (duration_seconds >= 0),
    CONSTRAINT chk_video_file_size CHECK (file_size_bytes >= 0),
    CONSTRAINT chk_video_progress CHECK (processing_progress >= 0 AND processing_progress <= 100)
);

-- Create indexes for videos
CREATE INDEX idx_videos_user ON videos (user_id);
CREATE INDEX idx_videos_travel_plan ON videos (travel_plan_id);
CREATE INDEX idx_videos_place ON videos (place_id);
CREATE INDEX idx_videos_status ON videos (status);
CREATE INDEX idx_videos_public ON videos (is_public);
CREATE INDEX idx_videos_created_at ON videos (created_at DESC);
CREATE INDEX idx_videos_view_count ON videos (view_count DESC);

-- Apply updated_at trigger to videos table
CREATE TRIGGER trigger_videos_updated_at
    BEFORE UPDATE ON videos
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create user interactions table (likes, views, etc.)
CREATE TABLE user_interactions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- User who performed the action
    user_id VARCHAR(255) NOT NULL,
    
    -- Target of the interaction (polymorphic)
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    
    -- Type of interaction
    interaction_type VARCHAR(50) NOT NULL,
    
    -- Additional data (JSON)
    metadata JSONB,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    CONSTRAINT chk_target_type CHECK (target_type IN ('travel_plan', 'place', 'video', 'review')),
    CONSTRAINT chk_interaction_type CHECK (interaction_type IN ('like', 'view', 'share', 'bookmark', 'comment')),
    CONSTRAINT uk_user_interaction UNIQUE (user_id, target_type, target_id, interaction_type)
);

-- Create indexes for user interactions
CREATE INDEX idx_user_interactions_user ON user_interactions (user_id);
CREATE INDEX idx_user_interactions_target ON user_interactions (target_type, target_id);
CREATE INDEX idx_user_interactions_type ON user_interactions (interaction_type);
CREATE INDEX idx_user_interactions_created_at ON user_interactions (created_at DESC);

-- Create notifications table
CREATE TABLE notifications (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Target user
    user_id VARCHAR(255) NOT NULL,
    
    -- Notification content
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    
    -- Related entities
    related_entity_type VARCHAR(50),
    related_entity_id VARCHAR(255),
    
    -- Status
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    
    -- Delivery
    is_sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    CONSTRAINT chk_notification_type CHECK (type IN ('follow', 'like', 'comment', 'share', 'plan_update', 'system')),
    CONSTRAINT chk_related_entity_type CHECK (related_entity_type IN ('user', 'travel_plan', 'place', 'video', 'review'))
);

-- Create indexes for notifications
CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_unread ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_type ON notifications (type);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

-- Apply updated_at trigger to notifications table
CREATE TRIGGER trigger_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to increment interaction counts
CREATE OR REPLACE FUNCTION handle_interaction_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Increment count based on interaction type and target
        CASE NEW.interaction_type
            WHEN 'like' THEN
                CASE NEW.target_type
                    WHEN 'travel_plan' THEN
                        UPDATE travel_plans SET like_count = like_count + 1 WHERE id = NEW.target_id;
                    WHEN 'video' THEN
                        UPDATE videos SET like_count = like_count + 1 WHERE id = NEW.target_id;
                    ELSE
                        -- Handle other target types as needed
                END CASE;
            WHEN 'view' THEN
                CASE NEW.target_type
                    WHEN 'travel_plan' THEN
                        UPDATE travel_plans SET view_count = view_count + 1 WHERE id = NEW.target_id;
                    WHEN 'video' THEN
                        UPDATE videos SET view_count = view_count + 1 WHERE id = NEW.target_id;
                    WHEN 'place' THEN
                        UPDATE places SET view_count = view_count + 1 WHERE id = NEW.target_id;
                    ELSE
                        -- Handle other target types as needed
                END CASE;
            WHEN 'share' THEN
                CASE NEW.target_type
                    WHEN 'travel_plan' THEN
                        UPDATE travel_plans SET share_count = share_count + 1 WHERE id = NEW.target_id;
                    ELSE
                        -- Handle other target types as needed
                END CASE;
            WHEN 'bookmark' THEN
                CASE NEW.target_type
                    WHEN 'place' THEN
                        UPDATE places SET bookmark_count = bookmark_count + 1 WHERE id = NEW.target_id;
                    WHEN 'travel_plan' THEN
                        UPDATE travel_plans SET save_count = save_count + 1 WHERE id = NEW.target_id;
                    ELSE
                        -- Handle other target types as needed
                END CASE;
        END CASE;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        -- Decrement count (similar logic as above but with -1)
        CASE OLD.interaction_type
            WHEN 'like' THEN
                CASE OLD.target_type
                    WHEN 'travel_plan' THEN
                        UPDATE travel_plans SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.target_id;
                    WHEN 'video' THEN
                        UPDATE videos SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.target_id;
                END CASE;
            -- Add other cases as needed
        END CASE;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Apply interaction count trigger
CREATE TRIGGER trigger_handle_interaction_count
    AFTER INSERT OR DELETE ON user_interactions
    FOR EACH ROW
    EXECUTE FUNCTION handle_interaction_count();

-- Create materialized view for user activity feed
CREATE MATERIALIZED VIEW user_activity_feed AS
SELECT 
    ui.user_id,
    ui.created_at,
    ui.interaction_type,
    ui.target_type,
    ui.target_id,
    CASE 
        WHEN ui.target_type = 'travel_plan' THEN tp.title
        WHEN ui.target_type = 'place' THEN p.name
        WHEN ui.target_type = 'video' THEN v.title
    END as target_title,
    u.nickname as user_nickname,
    u.profile_image_url as user_profile_image
FROM user_interactions ui
JOIN users u ON ui.user_id = u.id
LEFT JOIN travel_plans tp ON ui.target_type = 'travel_plan' AND ui.target_id = tp.id
LEFT JOIN places p ON ui.target_type = 'place' AND ui.target_id = p.id
LEFT JOIN videos v ON ui.target_type = 'video' AND ui.target_id = v.id
ORDER BY ui.created_at DESC;

-- Create index on materialized view
CREATE INDEX idx_user_activity_feed_user ON user_activity_feed (user_id, created_at DESC);

-- Add comments
COMMENT ON TABLE saved_plans IS 'User bookmarks/saves of travel plans';
COMMENT ON TABLE videos IS 'Videos uploaded by users, linked to places or travel plans';
COMMENT ON TYPE video_status IS 'Status of video processing (UPLOADING, PROCESSING, READY, FAILED, ARCHIVED)';
COMMENT ON TABLE user_interactions IS 'Generic table for user interactions (likes, views, shares, etc.)';
COMMENT ON TABLE notifications IS 'User notifications for various activities';
COMMENT ON MATERIALIZED VIEW user_activity_feed IS 'Materialized view of user activity for feeds';