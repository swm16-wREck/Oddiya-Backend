-- Seed test data for Oddiya application
-- Korean travel data with realistic places, users, and travel plans

-- Clear existing test data
DELETE FROM review_images WHERE review_id IN (SELECT id FROM reviews WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM review_likes WHERE review_id IN (SELECT id FROM reviews WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM reviews WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM place_images WHERE place_id IN (SELECT id FROM places WHERE naver_place_id LIKE 'test_%');
DELETE FROM place_opening_hours WHERE place_id IN (SELECT id FROM places WHERE naver_place_id LIKE 'test_%');
DELETE FROM place_tags WHERE place_id IN (SELECT id FROM places WHERE naver_place_id LIKE 'test_%');
DELETE FROM itinerary_items WHERE travel_plan_id IN (SELECT id FROM travel_plans WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM travel_plan_tags WHERE travel_plan_id IN (SELECT id FROM travel_plans WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM travel_plan_preferences WHERE travel_plan_id IN (SELECT id FROM travel_plans WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM travel_plan_collaborators WHERE travel_plan_id IN (SELECT id FROM travel_plans WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%'));
DELETE FROM travel_plans WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM videos WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM user_followers WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%') OR follower_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM user_travel_preferences WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM user_preferences WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test%');
DELETE FROM places WHERE naver_place_id LIKE 'test_%';
DELETE FROM users WHERE email LIKE '%@test%';

-- Insert test users with Korean names and realistic preferences
INSERT INTO users (id, email, username, password, nickname, profile_image_url, bio, provider, provider_id, is_email_verified, is_premium, is_active, refresh_token, created_at, updated_at) VALUES
(1001, 'minsu.adventure@test.com', 'adventure_minsu', '$2a$10$N.zmdr9k7uH/WdKS5SwPQOBhCHJHH5rKMJbWdl4xWbJL1b6t6H6fC', '모험가민수', 'https://example.com/avatars/minsu.jpg', '혼자 떠나는 여행을 좋아하는 모험가입니다. 새로운 경험을 추구합니다.', 'local', 'local_1001', true, false, true, null, '2024-01-15 10:00:00', now()),
(1002, 'family.mom@test.com', 'family_travel', '$2a$10$N.zmdr9k7uH/WdKS5SwPQOBhCHJHH5rKMJbWdl4xWbJL1b6t6H6fC', '가족여행맘', 'https://example.com/avatars/family.jpg', '아이들과 함께하는 안전하고 교육적인 여행을 계획합니다.', 'google', 'google_family', true, false, true, null, '2024-01-20 11:30:00', now()),
(1003, 'luxury.jiyoung@test.com', 'luxury_travel', '$2a$10$N.zmdr9k7uH/WdKS5SwPQOBhCHJHH5rKMJbWdl4xWbJL1b6t6H6fC', '럭셔리지영', 'https://example.com/avatars/jiyoung.jpg', '프리미엄 서비스와 고급 여행을 선호합니다. 품격있는 여행을 추구해요.', 'apple', 'apple_luxury', true, true, true, null, '2024-02-01 14:20:00', now()),
(1004, 'foodie.hunter@test.com', 'food_hunter', '$2a$10$N.zmdr9k7uH/WdKS5SwPQOBhCHJHH5rKMJbWdl4xWbJL1b6t6H6fC', '맛집헌터', 'https://example.com/avatars/foodie.jpg', '전국 맛집을 찾아다니는 미식가입니다. 숨은 맛집 정보를 공유합니다.', 'kakao', 'kakao_foodie', true, false, true, null, '2024-02-10 16:45:00', now()),
(1005, 'kpop.lover@test.com', 'kpop_fan', '$2a$10$N.zmdr9k7uH/WdKS5SwPQOBhCHJHH5rKMJbWdl4xWbJL1b6t6H6fC', '케이팝러버', 'https://example.com/avatars/kpop.jpg', 'K-pop과 한류 문화를 사랑하는 팬입니다. 성지순례 여행을 자주 갑니다.', 'naver', 'naver_kpop', true, false, true, null, '2024-02-15 12:00:00', now());

-- Insert user preferences
INSERT INTO user_preferences (user_id, preference_key, preference_value) VALUES
(1001, 'language', 'ko'),
(1001, 'notifications', 'enabled'),
(1001, 'privacy', 'public'),
(1001, 'theme', 'dark'),
(1002, 'language', 'ko'),
(1002, 'notifications', 'enabled'),
(1002, 'privacy', 'private'),
(1002, 'theme', 'light'),
(1003, 'language', 'en'),
(1003, 'notifications', 'premium'),
(1003, 'privacy', 'public'),
(1003, 'theme', 'light'),
(1004, 'language', 'ko'),
(1004, 'notifications', 'enabled'),
(1004, 'privacy', 'public'),
(1004, 'theme', 'light'),
(1005, 'language', 'ko'),
(1005, 'notifications', 'enabled'),
(1005, 'privacy', 'public'),
(1005, 'theme', 'dark');

-- Insert user travel preferences
INSERT INTO user_travel_preferences (user_id, preference_key, preference_value) VALUES
(1001, '여행스타일', '모험적'),
(1001, '숙박선호', '게스트하우스'),
(1001, '예산수준', '경제적'),
(1001, '동행타입', '솔로'),
(1001, '관심분야', '자연'),
(1002, '여행스타일', '여유로운'),
(1002, '숙박선호', '리조트'),
(1002, '예산수준', '중상급'),
(1002, '동행타입', '가족'),
(1002, '관심분야', '역사문화'),
(1003, '여행스타일', '문화탐방'),
(1003, '숙박선호', '럭셔리호텔'),
(1003, '예산수준', '프리미엄'),
(1003, '동행타입', '커플'),
(1003, '관심분야', '예술'),
(1004, '여행스타일', '맛집투어'),
(1004, '숙박선호', '부티크호텔'),
(1004, '예산수준', '중급'),
(1004, '동행타입', '친구'),
(1004, '관심분야', '음식'),
(1005, '여행스타일', '문화탐방'),
(1005, '숙박선호', '부티크호텔'),
(1005, '예산수준', '중급'),
(1005, '동행타입', '친구'),
(1005, '관심분야', 'K-pop');

-- Insert Seoul landmarks and attractions
INSERT INTO places (id, naver_place_id, name, category, description, address, road_address, latitude, longitude, phone_number, website, rating, review_count, bookmark_count, view_count, is_verified, popularity_score, created_at, updated_at) VALUES
(2001, 'test_gyeongbok', '경복궁', '관광지', 'Korean royal palace from Joseon Dynasty', '서울 종로구 사직로 161', '서울 종로구 사직로 161', 37.5788, 126.9770, '02-3700-3900', 'http://www.royalpalace.go.kr', 4.5, 1250, 340, 15420, true, 95.0, '2023-01-01 09:00:00', now()),
(2002, 'test_namsan', '남산타워', '관광지', 'Iconic Seoul tower with panoramic city views', '서울 용산구 남산공원길 105', '서울 용산구 남산공원길 105', 37.5512, 126.9882, '02-3455-9277', 'http://www.seoultower.co.kr', 4.3, 2100, 567, 28350, true, 92.5, '2023-01-01 09:00:00', now()),
(2003, 'test_myeongdong', '명동', '쇼핑', 'Famous shopping and cultural district', '서울 중구 명동', '서울 중구 명동', 37.5636, 126.9831, null, null, 4.2, 890, 234, 12750, true, 88.0, '2023-01-01 09:00:00', now()),
(2004, 'test_hongdae', '홍대', '유흥', 'University area known for nightlife and indie culture', '서울 마포구 홍익로', '서울 마포구 홍익로', 37.5563, 126.9236, null, null, 4.4, 1560, 445, 18920, true, 90.5, '2023-01-01 09:00:00', now()),
(2005, 'test_bukchon', '북촌한옥마을', '문화지구', 'Traditional Korean village with hanok houses', '서울 종로구 계동길 37', '서울 종로구 계동길 37', 37.5814, 126.9849, null, null, 4.1, 756, 189, 9870, true, 85.0, '2023-01-01 09:00:00', now());

-- Insert Seoul restaurants
INSERT INTO places (id, naver_place_id, name, category, description, address, road_address, latitude, longitude, phone_number, website, rating, review_count, bookmark_count, view_count, is_verified, popularity_score, created_at, updated_at) VALUES
(2006, 'test_korean_rest', '미슐랭 한식당', '한식', 'Premium Korean fine dining experience', '서울 강남구 테헤란로', '서울 강남구 테헤란로', 37.5123, 127.0591, '02-1234-5678', null, 4.7, 234, 89, 3450, true, 78.5, '2023-01-01 09:00:00', now()),
(2007, 'test_myeongdong_kyoja', '명동교자', '면요리', 'Famous kalguksu and mandu restaurant', '서울 중구 명동10길 29', '서울 중구 명동10길 29', 37.5652, 126.9810, '02-776-5348', null, 4.4, 567, 145, 6780, true, 72.0, '2023-01-01 09:00:00', now()),
(2008, 'test_gangnam_bbq', '강남 BBQ', '고기구이', 'Premium Korean BBQ with wagyu beef', '서울 강남구 강남대로', '서울 강남구 강남대로', 37.5014, 127.0269, '02-9876-5432', null, 4.6, 345, 112, 4890, true, 80.0, '2023-01-01 09:00:00', now());

-- Insert Seoul hotels
INSERT INTO places (id, naver_place_id, name, category, description, address, road_address, latitude, longitude, phone_number, website, rating, review_count, bookmark_count, view_count, is_verified, popularity_score, created_at, updated_at) VALUES
(2009, 'test_shilla', '신라호텔', '럭셔리호텔', 'Luxury hotel with traditional Korean hospitality', '서울 중구 동호로 249', '서울 중구 동호로 249', 37.5558, 127.0054, '02-2233-3131', 'http://www.shilla.net', 4.8, 456, 234, 8970, true, 95.5, '2023-01-01 09:00:00', now()),
(2010, 'test_lotte_hotel', '롯데호텔 명동', '호텔', 'Premium hotel in the heart of Seoul', '서울 중구 을지로 30', '서울 중구 을지로 30', 37.5651, 126.9810, '02-771-1000', 'http://www.lotte.co.kr', 4.5, 789, 298, 12340, true, 87.5, '2023-01-01 09:00:00', now());

-- Insert place images
INSERT INTO place_images (place_id, image_url) VALUES
(2001, 'https://example.com/places/gyeongbok1.jpg'),
(2001, 'https://example.com/places/gyeongbok2.jpg'),
(2001, 'https://example.com/places/gyeongbok3.jpg'),
(2002, 'https://example.com/places/namsan1.jpg'),
(2002, 'https://example.com/places/namsan2.jpg'),
(2003, 'https://example.com/places/myeongdong1.jpg'),
(2004, 'https://example.com/places/hongdae1.jpg'),
(2004, 'https://example.com/places/hongdae2.jpg'),
(2005, 'https://example.com/places/bukchon1.jpg'),
(2006, 'https://example.com/places/korean_rest1.jpg'),
(2007, 'https://example.com/places/kyoja1.jpg'),
(2008, 'https://example.com/places/bbq1.jpg'),
(2009, 'https://example.com/places/shilla1.jpg'),
(2010, 'https://example.com/places/lotte1.jpg');

-- Insert place tags
INSERT INTO place_tags (place_id, tag) VALUES
(2001, '역사'),
(2001, '궁궐'),
(2001, '한국문화'),
(2002, '전망'),
(2002, '랜드마크'),
(2002, '로맨틱'),
(2003, '쇼핑'),
(2003, '음식'),
(2003, '관광'),
(2004, '클럽'),
(2004, '술집'),
(2004, '음악'),
(2005, '전통'),
(2005, '한옥'),
(2005, '사진촬영'),
(2006, '미슐랭'),
(2006, '한식'),
(2006, '고급'),
(2007, '칼국수'),
(2007, '만두'),
(2007, '전통'),
(2008, '한우'),
(2008, '바베큐'),
(2008, '소고기'),
(2009, '럭셔리'),
(2009, '전통'),
(2009, '서비스'),
(2010, '명동'),
(2010, '쇼핑'),
(2010, '중심가');

-- Insert place opening hours
INSERT INTO place_opening_hours (place_id, day_of_week, hours) VALUES
(2001, '월요일', '09:00-18:00'),
(2001, '화요일', '09:00-18:00'),
(2001, '수요일', '09:00-18:00'),
(2001, '목요일', '09:00-18:00'),
(2001, '금요일', '09:00-18:00'),
(2001, '토요일', '09:00-18:00'),
(2001, '일요일', '09:00-18:00'),
(2002, '월요일', '10:00-23:00'),
(2002, '화요일', '10:00-23:00'),
(2002, '수요일', '10:00-23:00'),
(2002, '목요일', '10:00-23:00'),
(2002, '금요일', '10:00-23:00'),
(2002, '토요일', '10:00-23:00'),
(2002, '일요일', '10:00-23:00'),
(2006, '월요일', '11:30-22:00'),
(2006, '화요일', '11:30-22:00'),
(2006, '수요일', '11:30-22:00'),
(2006, '목요일', '11:30-22:00'),
(2006, '금요일', '11:30-22:00'),
(2006, '토요일', '11:30-22:00'),
(2006, '일요일', '휴무'),
(2007, '월요일', '10:30-21:30'),
(2007, '화요일', '10:30-21:30'),
(2007, '수요일', '10:30-21:30'),
(2007, '목요일', '10:30-21:30'),
(2007, '금요일', '10:30-21:30'),
(2007, '토요일', '10:30-21:30'),
(2007, '일요일', '10:30-21:30');

-- Insert travel plans
INSERT INTO travel_plans (id, user_id, title, description, destination, start_date, end_date, number_of_people, budget, status, is_public, is_ai_generated, view_count, like_count, share_count, save_count, cover_image_url, created_at, updated_at) VALUES
(3001, 1001, '서울 주말 힐링 여행', '혼자만의 시간을 즐기는 서울 시내 힐링 여행 코스입니다.', '서울', '2024-09-14', '2024-09-16', 1, 300000, 'PUBLISHED', true, false, 245, 18, 5, 42, 'https://example.com/covers/seoul_healing.jpg', '2024-08-25 14:30:00', now()),
(3002, 1002, '서울 가족여행 3박 4일', '아이들과 함께하는 서울의 주요 관광지 탐방과 체험 활동', '서울', '2024-09-30', '2024-10-03', 4, 800000, 'DRAFT', false, false, 12, 3, 1, 8, 'https://example.com/covers/family_seoul.jpg', '2024-08-29 10:15:00', now()),
(3003, 1003, '서울 출장 + 개인여행', '출장 일정을 마친 후 서울의 문화와 맛집을 탐방하는 여행', '서울', '2024-09-21', '2024-09-25', 1, 500000, 'PUBLISHED', true, false, 156, 12, 8, 29, 'https://example.com/covers/business_seoul.jpg', '2024-08-22 16:45:00', now()),
(3004, 1005, '서울 K-pop 성지순례', 'K-pop 팬들을 위한 서울의 K-pop 관련 명소와 카페 투어', '서울', '2024-10-15', '2024-10-17', 3, 450000, 'PLANNING', true, false, 89, 7, 3, 15, 'https://example.com/covers/kpop_seoul.jpg', '2024-08-27 20:00:00', now());

-- Insert travel plan preferences
INSERT INTO travel_plan_preferences (travel_plan_id, preference_key, preference_value) VALUES
(3001, '여행스타일', '여유로운'),
(3001, '교통수단', '대중교통'),
(3001, '관심분야', '문화'),
(3002, '여행스타일', '가족친화'),
(3002, '교통수단', '택시'),
(3002, '관심분야', '체험'),
(3003, '여행스타일', '비즈니스'),
(3003, '교통수단', '대중교통'),
(3003, '관심분야', '음식'),
(3004, '여행스타일', '문화탐방'),
(3004, '교통수단', '대중교통'),
(3004, '관심분야', 'K-pop');

-- Insert travel plan tags
INSERT INTO travel_plan_tags (travel_plan_id, tag) VALUES
(3001, '솔로여행'),
(3001, '힐링'),
(3001, '서울'),
(3001, '주말'),
(3002, '가족여행'),
(3002, '아이들'),
(3002, '체험'),
(3002, '관광'),
(3003, '출장'),
(3003, '비즈니스'),
(3003, '맛집'),
(3003, '문화'),
(3004, 'K-pop'),
(3004, '한류'),
(3004, '성지순례'),
(3004, '친구');

-- Insert itinerary items
INSERT INTO itinerary_items (id, travel_plan_id, place_id, day_number, sequence, start_time, end_time, notes, created_at, updated_at) VALUES
(4001, 3001, 2001, 1, 1, '09:00:00', '11:30:00', '조선왕조의 대표 궁궐. 수문장 교대식 관람 (10:00, 11:00)', now(), now()),
(4002, 3001, 2007, 1, 2, '12:00:00', '13:30:00', '전통 한식으로 점심. 칼국수와 만두 추천', now(), now()),
(4003, 3001, 2005, 1, 3, '14:00:00', '16:00:00', '전통 한옥마을 투어. 사진 촬영 명소', now(), now()),
(4004, 3001, 2002, 1, 4, '17:00:00', '19:00:00', '서울 전망 감상. 석양이 아름다운 시간', now(), now()),
(4005, 3002, 2001, 1, 1, '10:00:00', '12:00:00', '아이들과 함께하는 궁궐 투어', now(), now()),
(4006, 3002, 2006, 1, 2, '12:30:00', '14:00:00', '가족 친화적인 한식당에서 점심', now(), now()),
(4007, 3002, 2003, 1, 3, '15:00:00', '17:00:00', '명동에서 쇼핑과 간식', now(), now());

-- Insert reviews with Korean content
INSERT INTO reviews (id, place_id, user_id, rating, content, visit_date, likes_count, is_verified_purchase, created_at, updated_at) VALUES
(5001, 2001, 1001, 5, '정말 멋진 곳이에요! 사진 찍기도 좋고 분위기가 최고예요. 한국의 전통 문화를 체험할 수 있어서 의미있는 시간이었습니다.', '2024-08-15 14:30:00', 12, true, '2024-08-16 10:00:00', now()),
(5002, 2002, 1002, 4, '가족과 함께 즐거운 시간을 보냈습니다. 강추합니다! 아이들이 특히 좋아했어요.', '2024-08-20 18:45:00', 8, true, '2024-08-21 09:15:00', now()),
(5003, 2007, 1004, 5, '음식이 너무 맛있고 서비스도 친절해서 또 오고 싶어요. 칼국수가 정말 일품입니다!', '2024-08-22 13:00:00', 15, true, '2024-08-22 20:30:00', now()),
(5004, 2006, 1003, 4, '가격 대비 품질이 좋아요. 미슐랭 맛집 답게 맛과 서비스 모두 훌륭했습니다.', '2024-08-25 19:30:00', 6, true, '2024-08-26 11:00:00', now()),
(5005, 2004, 1005, 5, 'K-pop 문화를 느낄 수 있는 최고의 장소! 젊은 분위기가 매력적이에요.', '2024-08-28 22:00:00', 20, true, '2024-08-29 12:30:00', now()),
(5006, 2001, 1002, 3, '그냥 보통이었어요. 특별히 나쁘지도 좋지도 않았습니다. 사람이 너무 많았어요.', '2024-08-18 11:00:00', 2, false, '2024-08-19 14:00:00', now()),
(5007, 2009, 1003, 5, '럭셔리 호텔 답게 서비스가 완벽했어요! 시설도 최고급이고 직원분들이 너무 친절합니다.', '2024-08-10 16:00:00', 18, true, '2024-08-12 10:45:00', now());

-- Insert review images
INSERT INTO review_images (review_id, image_url) VALUES
(5001, 'https://example.com/reviews/gyeongbok_review1.jpg'),
(5001, 'https://example.com/reviews/gyeongbok_review2.jpg'),
(5002, 'https://example.com/reviews/namsan_family.jpg'),
(5003, 'https://example.com/reviews/kyoja_food.jpg'),
(5005, 'https://example.com/reviews/hongdae_night.jpg'),
(5007, 'https://example.com/reviews/shilla_room.jpg'),
(5007, 'https://example.com/reviews/shilla_service.jpg');

-- Insert videos
INSERT INTO videos (id, user_id, travel_plan_id, title, description, video_url, thumbnail_url, duration, status, is_public, view_count, like_count, share_count, created_at, updated_at) VALUES
(6001, 1001, 3001, '서울 주말 힐링 여행 - 여행 브이로그 🇰🇷', '혼자 떠난 서울 힐링 여행기를 브이로그로 담아봤어요. 궁궐과 한옥마을의 아름다운 모습을 만끽한 여행이었습니다!', 'https://www.youtube.com/watch?v=test123', 'https://example.com/thumbnails/seoul_healing.jpg', 1248, 'PUBLISHED', true, 1250, 89, 12, '2024-08-17 20:00:00', now()),
(6002, 1004, null, '서울 길거리 음식 도전기', '서울 명동과 홍대에서 만난 다양한 길거리 음식들! 한국 음식의 진짜 맛을 찾아 떠나는 미식 여행입니다.', 'https://www.youtube.com/watch?v=test456', 'https://example.com/thumbnails/street_food.jpg', 892, 'PUBLISHED', true, 2340, 156, 28, '2024-08-20 15:30:00', now()),
(6003, 1005, 3004, '서울 K-pop 성지순례 투어', 'K-pop 팬들을 위한 서울의 K-pop 관련 명소들을 소개합니다! 함께 성지순례 떠나요!', 'https://www.youtube.com/watch?v=test789', 'https://example.com/thumbnails/kpop_tour.jpg', 1456, 'PUBLISHED', true, 3450, 234, 45, '2024-08-25 18:45:00', now());

-- Insert video tags  
INSERT INTO video_tags (video_id, tag) VALUES
(6001, '브이로그'),
(6001, '솔로여행'),
(6001, '힐링'),
(6001, '서울'),
(6002, '먹방'),
(6002, '길거리음식'),
(6002, '맛집'),
(6002, '서울'),
(6003, 'K-pop'),
(6003, '성지순례'),
(6003, '한류'),
(6003, '문화');

-- Insert user followers (social connections)
INSERT INTO user_followers (user_id, follower_id) VALUES
(1001, 1002),
(1001, 1004),
(1002, 1003),
(1003, 1001),
(1003, 1005),
(1004, 1001),
(1004, 1005),
(1005, 1002),
(1005, 1004);

-- Insert review likes
INSERT INTO review_likes (review_id, user_id) VALUES
(5001, 1002),
(5001, 1003),
(5001, 1004),
(5002, 1001),
(5002, 1003),
(5003, 1001),
(5003, 1002),
(5003, 1005),
(5005, 1001),
(5005, 1002),
(5007, 1001),
(5007, 1004);

-- Update place statistics based on reviews
UPDATE places SET 
    rating = (SELECT AVG(rating) FROM reviews WHERE place_id = places.id),
    review_count = (SELECT COUNT(*) FROM reviews WHERE place_id = places.id)
WHERE id IN (2001, 2002, 2004, 2006, 2007, 2009);

COMMIT;