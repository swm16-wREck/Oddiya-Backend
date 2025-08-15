-- Create users table and related tables
-- Migration: V001__create_users_table.sql

-- Create users table
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- User identification
    email VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    password VARCHAR(255),
    nickname VARCHAR(255) NOT NULL,
    
    -- Profile information
    profile_image_url TEXT,
    bio TEXT,
    
    -- OAuth integration
    provider VARCHAR(50) NOT NULL DEFAULT 'email',
    provider_id VARCHAR(255) NOT NULL,
    
    -- User status
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Authentication
    refresh_token TEXT,
    last_login_at TIMESTAMP,
    
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_provider_id UNIQUE (provider, provider_id)
);

-- Create indexes for users table
CREATE INDEX idx_user_email ON users (email);
CREATE INDEX idx_user_provider_id ON users (provider, provider_id);
CREATE INDEX idx_user_is_active ON users (is_active);
CREATE INDEX idx_user_created_at ON users (created_at);

-- Create user preferences table
CREATE TABLE user_preferences (
    user_id VARCHAR(255) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (user_id, preference_key),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create user travel preferences table
CREATE TABLE user_travel_preferences (
    user_id VARCHAR(255) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT,
    PRIMARY KEY (user_id, preference_key),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create user followers table (many-to-many relationship)
CREATE TABLE user_followers (
    user_id VARCHAR(255) NOT NULL,
    follower_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, follower_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_not_self_follow CHECK (user_id != follower_id)
);

-- Create indexes for follower relationships
CREATE INDEX idx_user_followers_user_id ON user_followers (user_id);
CREATE INDEX idx_user_followers_follower_id ON user_followers (follower_id);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to users table
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add comments
COMMENT ON TABLE users IS 'User accounts and profiles';
COMMENT ON COLUMN users.provider IS 'OAuth provider (google, apple, email)';
COMMENT ON COLUMN users.provider_id IS 'Unique identifier from OAuth provider';
COMMENT ON TABLE user_preferences IS 'General user preferences key-value pairs';
COMMENT ON TABLE user_travel_preferences IS 'Travel-specific user preferences';
COMMENT ON TABLE user_followers IS 'User follower/following relationships';