-- =====================================================
-- Oddiya Travel Planning Service - Seed Data
-- Version: 1.0
-- Date: 2025-08-12
-- Migration: V3__Seed_Data.sql
-- =====================================================

-- =====================================================
-- TEST USERS (for development and testing)
-- =====================================================

-- Admin user
INSERT INTO users (id, email, nickname, provider, provider_id, is_active, is_verified) 
VALUES (
    'admin-user-id-001',
    'admin@oddiya.com',
    'admin',
    'google',
    'google-admin-001',
    true,
    true
) ON CONFLICT (email) DO NOTHING;

-- Test users for development
INSERT INTO users (id, email, nickname, provider, provider_id, bio, is_active) VALUES
('test-user-001', 'alice@example.com', 'alice_traveler', 'google', 'google-alice-001', 'Love exploring Seoul''s hidden gems! 🏙️', true),
('test-user-002', 'bob@example.com', 'bob_explorer', 'google', 'google-bob-002', 'Foodie and culture enthusiast', true),
('test-user-003', 'charlie@example.com', 'charlie_wanderer', 'apple', 'apple-charlie-003', 'Adventure seeker from Busan', true),
('test-user-004', 'diana@example.com', 'diana_seoul', 'google', 'google-diana-004', 'Seoul local sharing favorite spots', true)
ON CONFLICT (email) DO NOTHING;

-- User preferences
INSERT INTO user_preferences (user_id, preference_key, preference_value) VALUES
('test-user-001', 'language', 'en'),
('test-user-001', 'currency', 'KRW'),
('test-user-001', 'notifications', 'true'),
('test-user-002', 'language', 'ko'),
('test-user-002', 'currency', 'KRW'),
('test-user-003', 'language', 'en'),
('test-user-003', 'currency', 'USD'),
('test-user-004', 'language', 'ko'),
('test-user-004', 'currency', 'KRW')
ON CONFLICT (user_id, preference_key) DO NOTHING;

-- Travel preferences
INSERT INTO user_travel_preferences (user_id, preference_key, preference_value) VALUES
('test-user-001', 'budget_range', 'mid-range'),
('test-user-001', 'preferred_categories', 'restaurant,cafe,tourist-attraction'),
('test-user-001', 'transportation', 'public'),
('test-user-002', 'budget_range', 'budget'),
('test-user-002', 'preferred_categories', 'restaurant,shopping'),
('test-user-002', 'transportation', 'walking'),
('test-user-003', 'budget_range', 'luxury'),
('test-user-003', 'preferred_categories', 'tourist-attraction,entertainment'),
('test-user-003', 'transportation', 'taxi'),
('test-user-004', 'budget_range', 'mid-range'),
('test-user-004', 'preferred_categories', 'cafe,restaurant,nature'),
('test-user-004', 'transportation', 'mixed')
ON CONFLICT (user_id, preference_key) DO NOTHING;

-- =====================================================
-- SAMPLE PLACES (Seoul landmarks and popular spots)
-- =====================================================

-- Major Seoul landmarks
INSERT INTO places (id, name, category, description, address, latitude, longitude, naver_place_id, rating, review_count, is_verified) VALUES
-- Tourist Attractions
('place-gyeongbok-001', 'Gyeongbokgung Palace', 'tourist-attraction', 'The main royal palace of the Joseon dynasty', '161 Sajik-ro, Jongno-gu, Seoul', 37.579617, 126.977041, 'naver-gyeongbok-001', 4.5, 15420, true),
('place-namsan-002', 'N Seoul Tower', 'tourist-attraction', 'Iconic communication and observation tower', '105 Namsangongwon-gil, Yongsan-gu, Seoul', 37.551169, 126.988227, 'naver-namsan-002', 4.3, 12890, true),
('place-bukchon-003', 'Bukchon Hanok Village', 'tourist-attraction', 'Traditional Korean village with historical architecture', '37 Gyedong-gil, Jongno-gu, Seoul', 37.582418, 126.983157, 'naver-bukchon-003', 4.2, 8765, true),
('place-dongdaemun-004', 'Dongdaemun Design Plaza', 'tourist-attraction', 'Futuristic architecture and design hub', '281 Eulji-ro, Jung-gu, Seoul', 37.566735, 126.009964, 'naver-dongdaemun-004', 4.1, 6543, true),
('place-myeongdong-005', 'Myeongdong Shopping Street', 'shopping', 'Famous shopping and street food district', 'Myeongdong 2-ga, Jung-gu, Seoul', 37.563692, 126.983024, 'naver-myeongdong-005', 4.0, 9876, true),

-- Restaurants
('place-gwangjang-006', 'Gwangjang Market', 'restaurant', 'Traditional Korean market famous for street food', '88 Changgyeonggung-ro, Jongno-gu, Seoul', 37.570221, 127.010103, 'naver-gwangjang-006', 4.4, 5432, true),
('place-jungsik-007', 'Jungsik Seoul', 'restaurant', 'Michelin-starred modern Korean fine dining', '11 Seolleung-ro 158-gil, Gangnam-gu, Seoul', 37.526577, 127.030954, 'naver-jungsik-007', 4.8, 1234, true),
('place-tosokchon-008', 'Tosokchon Samgyetang', 'restaurant', 'Famous ginseng chicken soup restaurant', '5 Jahamun-ro 5-gil, Jongno-gu, Seoul', 37.586379, 126.973721, 'naver-tosokchon-008', 4.3, 3456, true),

-- Cafes
('place-bluebottle-009', 'Blue Bottle Coffee Samcheong', 'cafe', 'Specialty coffee with traditional Korean architecture', '35 Samcheong-ro, Jongno-gu, Seoul', 37.586514, 126.985656, 'naver-bluebottle-009', 4.2, 2345, true),
('place-anthracite-010', 'Anthracite Coffee Roasters', 'cafe', 'Industrial-style coffee roastery', '240-1 Itaewon-ro, Yongsan-gu, Seoul', 37.534418, 126.994896, 'naver-anthracite-010', 4.1, 1876, true),

-- Hotels
('place-signiel-011', 'Signiel Seoul', 'hotel', 'Luxury hotel with city views from Lotte World Tower', '300 Olympic-ro, Songpa-gu, Seoul', 37.512985, 127.102786, 'naver-signiel-011', 4.7, 987, true),
('place-fourseasons-012', 'Four Seasons Hotel Seoul', 'hotel', 'Luxury hotel in Gwanghwamun area', '97 Saemunan-ro, Jongno-gu, Seoul', 37.571607, 126.975747, 'naver-fourseasons-012', 4.6, 765, true),

-- Nature spots
('place-hangang-013', 'Hangang Park Banpo', 'nature', 'Riverside park with rainbow fountain', '40 Sinbanpo-ro 11-gil, Seocho-gu, Seoul', 37.507913, 126.996094, 'naver-hangang-013', 4.2, 4321, true),
('place-namsan-014', 'Namsan Park', 'nature', 'Mountain park in the heart of Seoul', '231 Samil-daero, Jung-gu, Seoul', 37.552711, 126.990816, 'naver-namsan-014', 4.3, 3210, true),

-- Entertainment
('place-hongdae-015', 'Hongdae Street', 'entertainment', 'Vibrant nightlife and live music area', 'Wausan-ro, Mapo-gu, Seoul', 37.556785, 126.924229, 'naver-hongdae-015', 4.1, 5678, true)
ON CONFLICT (id) DO NOTHING;

-- Place images
INSERT INTO place_images (place_id, image_url, is_primary) VALUES
('place-gyeongbok-001', 'https://example.com/images/gyeongbokgung-main.jpg', true),
('place-gyeongbok-001', 'https://example.com/images/gyeongbokgung-gate.jpg', false),
('place-namsan-002', 'https://example.com/images/seoul-tower-main.jpg', true),
('place-bukchon-003', 'https://example.com/images/bukchon-hanok.jpg', true),
('place-jungsik-007', 'https://example.com/images/jungsik-interior.jpg', true)
ON CONFLICT DO NOTHING;

-- Place tags
INSERT INTO place_tags (place_id, tag) VALUES
('place-gyeongbok-001', 'historical'),
('place-gyeongbok-001', 'cultural'),
('place-gyeongbok-001', 'palace'),
('place-namsan-002', 'romantic'),
('place-namsan-002', 'viewpoint'),
('place-namsan-002', 'iconic'),
('place-bukchon-003', 'traditional'),
('place-bukchon-003', 'photogenic'),
('place-gwangjang-006', 'street-food'),
('place-gwangjang-006', 'local'),
('place-jungsik-007', 'fine-dining'),
('place-jungsik-007', 'michelin'),
('place-bluebottle-009', 'specialty-coffee'),
('place-hongdae-015', 'nightlife'),
('place-hongdae-015', 'young-crowd')
ON CONFLICT (place_id, tag) DO NOTHING;

-- =====================================================
-- SAMPLE TRAVEL PLANS
-- =====================================================

-- Sample travel plans
INSERT INTO travel_plans (id, user_id, title, description, destination, start_date, end_date, number_of_people, budget, status, is_public, is_ai_generated) VALUES
('plan-seoul-3day-001', 'test-user-001', '3 Days in Seoul - First Timer''s Guide', 'Perfect introduction to Seoul covering major attractions, traditional culture, and modern city life', 'Seoul, South Korea', '2024-09-15', '2024-09-17', 2, 800000.00, 'CONFIRMED', true, true),
('plan-foodie-007', 'test-user-002', 'Seoul Food Adventure', 'A culinary journey through Seoul''s best traditional and modern restaurants', 'Seoul, South Korea', '2024-10-01', '2024-10-03', 4, 600000.00, 'DRAFT', true, false),
('plan-luxury-weekend', 'test-user-003', 'Luxury Seoul Weekend', 'High-end experiences in Seoul including fine dining and premium hotels', 'Seoul, South Korea', '2024-11-10', '2024-11-12', 2, 2000000.00, 'CONFIRMED', false, false)
ON CONFLICT (id) DO NOTHING;

-- Travel plan tags
INSERT INTO travel_plan_tags (travel_plan_id, tag) VALUES
('plan-seoul-3day-001', 'first-timer'),
('plan-seoul-3day-001', 'cultural'),
('plan-seoul-3day-001', 'tourist-attractions'),
('plan-foodie-007', 'food'),
('plan-foodie-007', 'local-experience'),
('plan-luxury-weekend', 'luxury'),
('plan-luxury-weekend', 'premium')
ON CONFLICT (travel_plan_id, tag) DO NOTHING;

-- Sample itinerary items
INSERT INTO itinerary_items (id, travel_plan_id, place_id, day_number, sequence, start_time, end_time, notes, estimated_cost) VALUES
-- Day 1 of Seoul 3-day plan
('item-001', 'plan-seoul-3day-001', 'place-gyeongbok-001', 1, 1, '09:00:00', '11:30:00', 'Visit during the changing of the guard ceremony at 10:00 AM', 3000.00),
('item-002', 'plan-seoul-3day-001', 'place-bukchon-003', 1, 2, '12:00:00', '14:00:00', 'Walk through traditional Korean village, great for photos', 0.00),
('item-003', 'plan-seoul-3day-001', 'place-bluebottle-009', 1, 3, '14:30:00', '15:30:00', 'Coffee break in traditional setting', 12000.00),
-- Day 2
('item-004', 'plan-seoul-3day-001', 'place-myeongdong-005', 2, 1, '10:00:00', '13:00:00', 'Shopping and street food exploration', 50000.00),
('item-005', 'plan-seoul-3day-001', 'place-namsan-002', 2, 2, '15:00:00', '18:00:00', 'Visit Seoul Tower for sunset views', 16000.00),
-- Day 3
('item-006', 'plan-seoul-3day-001', 'place-gwangjang-006', 3, 1, '11:00:00', '13:00:00', 'Traditional market food tour', 25000.00),
('item-007', 'plan-seoul-3day-001', 'place-dongdaemun-004', 3, 2, '14:00:00', '16:00:00', 'Modern architecture and design', 0.00)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- SAMPLE REVIEWS
-- =====================================================

INSERT INTO reviews (id, user_id, place_id, travel_plan_id, rating, title, content, is_public) VALUES
('review-001', 'test-user-001', 'place-gyeongbok-001', 'plan-seoul-3day-001', 5, 'Amazing Historical Experience', 'The palace is absolutely stunning and the changing of the guard ceremony is a must-see. Very well preserved and informative.', true),
('review-002', 'test-user-002', 'place-gwangjang-006', NULL, 4, 'Great Street Food Variety', 'So many options and everything was delicious! The bindaetteok was especially good. Can get crowded during peak hours.', true),
('review-003', 'test-user-004', 'place-jungsik-007', NULL, 5, 'Exceptional Fine Dining', 'Every course was a work of art. The modern interpretation of Korean flavors is brilliant. Worth every penny for a special occasion.', true),
('review-004', 'test-user-003', 'place-namsan-002', NULL, 4, 'Beautiful City Views', 'The views are spectacular, especially at sunset. The cable car ride up was fun too. A bit touristy but still worth it.', true)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- FOLLOWER RELATIONSHIPS (for social features)
-- =====================================================

INSERT INTO user_followers (user_id, follower_id) VALUES
('test-user-004', 'test-user-001'), -- Diana followed by Alice
('test-user-004', 'test-user-002'), -- Diana followed by Bob  
('test-user-001', 'test-user-002'), -- Alice followed by Bob
('test-user-003', 'test-user-001')  -- Charlie followed by Alice
ON CONFLICT (user_id, follower_id) DO NOTHING;

-- =====================================================
-- COLLABORATORS (for collaborative travel planning)
-- =====================================================

INSERT INTO travel_plan_collaborators (travel_plan_id, user_id, role, accepted_at) VALUES
('plan-seoul-3day-001', 'test-user-001', 'OWNER', NOW()),
('plan-seoul-3day-001', 'test-user-002', 'EDITOR', NOW()),
('plan-foodie-007', 'test-user-002', 'OWNER', NOW()),
('plan-luxury-weekend', 'test-user-003', 'OWNER', NOW())
ON CONFLICT (travel_plan_id, user_id) DO NOTHING;

-- =====================================================
-- UPDATE CALCULATED FIELDS
-- =====================================================

-- Update place popularity scores based on the sample data
SELECT update_place_popularity_scores();

-- Update location geography points for all places
SELECT refresh_place_locations();

-- =====================================================
-- VERIFICATION QUERIES (commented out, for testing purposes)
-- =====================================================

-- Test spatial queries
-- SELECT * FROM find_places_within_radius(37.5665, 126.9780, 2000); -- Near City Hall
-- SELECT * FROM find_nearby_recommendations(ARRAY['place-gyeongbok-001', 'place-bukchon-003'], 1500);
-- SELECT * FROM get_area_place_statistics(37.5665, 126.9780, 5000);