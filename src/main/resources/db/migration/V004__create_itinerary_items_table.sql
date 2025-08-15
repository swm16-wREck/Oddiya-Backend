-- Create itinerary items table
-- Migration: V004__create_itinerary_items_table.sql

-- Create itinerary items table
CREATE TABLE itinerary_items (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Relationships
    travel_plan_id VARCHAR(255) NOT NULL,
    place_id VARCHAR(255),
    
    -- Sequence and timing
    day_number INTEGER NOT NULL,
    sequence INTEGER NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_minutes INTEGER,
    
    -- Item details
    title VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Location (for custom locations not in places table)
    place_name VARCHAR(255),
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    
    -- Cost tracking
    estimated_cost DECIMAL(10,2),
    actual_cost DECIMAL(10,2),
    
    -- Transportation
    transport_mode VARCHAR(50),
    transport_duration_minutes INTEGER,
    
    -- Additional info
    notes TEXT,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_itinerary_day_number CHECK (day_number > 0),
    CONSTRAINT chk_itinerary_sequence CHECK (sequence >= 0),
    CONSTRAINT chk_itinerary_costs CHECK (estimated_cost >= 0 AND actual_cost >= 0),
    CONSTRAINT chk_itinerary_duration CHECK (duration_minutes >= 0),
    CONSTRAINT chk_itinerary_transport_duration CHECK (transport_duration_minutes >= 0),
    CONSTRAINT chk_itinerary_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL) OR 
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT chk_itinerary_times CHECK (
        (start_time IS NULL AND end_time IS NULL) OR 
        (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time >= start_time)
    )
);

-- Create indexes for itinerary items
CREATE INDEX idx_itinerary_plan ON itinerary_items (travel_plan_id);
CREATE INDEX idx_itinerary_place ON itinerary_items (place_id);
CREATE INDEX idx_itinerary_day_sequence ON itinerary_items (travel_plan_id, day_number, sequence);
CREATE INDEX idx_itinerary_times ON itinerary_items (start_time, end_time);
CREATE INDEX idx_itinerary_completed ON itinerary_items (is_completed);
CREATE INDEX idx_itinerary_created_at ON itinerary_items (created_at);

-- Apply updated_at trigger to itinerary items table
CREATE TRIGGER trigger_itinerary_items_updated_at
    BEFORE UPDATE ON itinerary_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to auto-calculate duration from start/end times
CREATE OR REPLACE FUNCTION calculate_itinerary_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.start_time IS NOT NULL AND NEW.end_time IS NOT NULL THEN
        NEW.duration_minutes := EXTRACT(EPOCH FROM (NEW.end_time - NEW.start_time)) / 60;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply duration calculation trigger
CREATE TRIGGER trigger_calculate_itinerary_duration
    BEFORE INSERT OR UPDATE OF start_time, end_time ON itinerary_items
    FOR EACH ROW
    EXECUTE FUNCTION calculate_itinerary_duration();

-- Function to maintain sequence order within day
CREATE OR REPLACE FUNCTION maintain_itinerary_sequence()
RETURNS TRIGGER AS $$
BEGIN
    -- If inserting/updating sequence, ensure no gaps and no duplicates
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND (OLD.day_number != NEW.day_number OR OLD.sequence != NEW.sequence)) THEN
        -- Shift existing items if needed
        UPDATE itinerary_items 
        SET sequence = sequence + 1 
        WHERE travel_plan_id = NEW.travel_plan_id 
          AND day_number = NEW.day_number 
          AND sequence >= NEW.sequence 
          AND id != NEW.id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply sequence maintenance trigger
CREATE TRIGGER trigger_maintain_itinerary_sequence
    BEFORE INSERT OR UPDATE OF day_number, sequence ON itinerary_items
    FOR EACH ROW
    EXECUTE FUNCTION maintain_itinerary_sequence();

-- Create reviews table (linked to places and optionally travel plans)
CREATE TABLE reviews (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Relationships
    user_id VARCHAR(255) NOT NULL,
    place_id VARCHAR(255) NOT NULL,
    travel_plan_id VARCHAR(255), -- Optional: review from specific trip
    
    -- Review content
    rating INTEGER NOT NULL,
    title VARCHAR(255),
    content TEXT,
    
    -- Media
    image_urls TEXT[],
    
    -- Metrics
    helpful_count INTEGER NOT NULL DEFAULT 0,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE,
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_review_rating CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT chk_review_helpful_count CHECK (helpful_count >= 0),
    CONSTRAINT uk_review_user_place UNIQUE (user_id, place_id, travel_plan_id)
);

-- Create indexes for reviews
CREATE INDEX idx_reviews_user ON reviews (user_id);
CREATE INDEX idx_reviews_place ON reviews (place_id);
CREATE INDEX idx_reviews_travel_plan ON reviews (travel_plan_id);
CREATE INDEX idx_reviews_rating ON reviews (rating DESC);
CREATE INDEX idx_reviews_helpful ON reviews (helpful_count DESC);
CREATE INDEX idx_reviews_created_at ON reviews (created_at DESC);

-- Apply updated_at trigger to reviews table
CREATE TRIGGER trigger_reviews_updated_at
    BEFORE UPDATE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to update place rating when reviews change
CREATE OR REPLACE FUNCTION update_place_rating()
RETURNS TRIGGER AS $$
DECLARE
    place_id_to_update VARCHAR(255);
    avg_rating DOUBLE PRECISION;
    review_count INTEGER;
BEGIN
    -- Determine which place to update
    IF TG_OP = 'DELETE' THEN
        place_id_to_update := OLD.place_id;
    ELSE
        place_id_to_update := NEW.place_id;
    END IF;
    
    -- Calculate new average rating and count
    SELECT 
        COALESCE(AVG(rating), 0),
        COUNT(*)
    INTO avg_rating, review_count
    FROM reviews 
    WHERE place_id = place_id_to_update;
    
    -- Update place
    UPDATE places 
    SET rating = avg_rating,
        review_count = review_count
    WHERE id = place_id_to_update;
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Apply place rating update trigger
CREATE TRIGGER trigger_update_place_rating
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_place_rating();

-- Create view for itinerary with place details
CREATE VIEW itinerary_with_places AS
SELECT 
    ii.*,
    p.name as place_name_full,
    p.category as place_category,
    p.address as place_address_full,
    p.rating as place_rating,
    p.latitude as place_latitude,
    p.longitude as place_longitude
FROM itinerary_items ii
LEFT JOIN places p ON ii.place_id = p.id;

-- Add comments
COMMENT ON TABLE itinerary_items IS 'Individual items in travel plan itineraries';
COMMENT ON COLUMN itinerary_items.day_number IS 'Day number within the travel plan (1-based)';
COMMENT ON COLUMN itinerary_items.sequence IS 'Order within the day (0-based)';
COMMENT ON COLUMN itinerary_items.transport_mode IS 'Mode of transport to this location (walk, car, subway, etc.)';
COMMENT ON COLUMN itinerary_items.place_name IS 'Custom place name when place_id is null';
COMMENT ON TABLE reviews IS 'User reviews for places, optionally linked to travel plans';
COMMENT ON COLUMN reviews.image_urls IS 'Array of image URLs for the review';
COMMENT ON VIEW itinerary_with_places IS 'Itinerary items with joined place information';