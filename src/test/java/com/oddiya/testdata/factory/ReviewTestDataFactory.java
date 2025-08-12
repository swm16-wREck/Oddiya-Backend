package com.oddiya.testdata.factory;

import com.oddiya.entity.Place;
import com.oddiya.entity.Review;
import com.oddiya.entity.User;
import com.oddiya.testdata.data.KoreanLocationData;
import net.datafaker.Faker;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for Review entities using Builder pattern
 * Generates realistic Korean review data with various sentiments and ratings
 */
public class ReviewTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final KoreanLocationData locationData = new KoreanLocationData();
    private final Random random = new Random(12345);
    
    /**
     * Create a single review with default settings
     */
    public Review createReview(User user, Place place) {
        return createReviewBuilder(user, place).build();
    }
    
    /**
     * Create multiple reviews
     */
    public List<Review> createReviews(int count, List<User> users, List<Place> places) {
        List<Review> reviews = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            Place place = places.get(random.nextInt(places.size()));
            reviews.add(createReviewBuilder(user, place).build());
        }
        
        return reviews;
    }
    
    /**
     * Create Korean reviews with authentic sentiments
     */
    public List<Review> createKoreanReviews(List<User> users, List<Place> places, int count) {
        List<Review> reviews = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            Place place = places.get(random.nextInt(places.size()));
            
            // Create review with Korean sentiment
            reviews.add(createKoreanReviewBuilder(user, place).build());
        }
        
        return reviews;
    }
    
    /**
     * Create reviews for a specific place with varied ratings
     */
    public List<Review> createReviewsForPlace(Place place, List<User> users, int count) {
        List<Review> reviews = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            reviews.add(createReviewBuilder(user, place).build());
        }
        
        return reviews;
    }
    
    /**
     * Create positive reviews (4-5 stars)
     */
    public List<Review> createPositiveReviews(List<User> users, List<Place> places, int count) {
        List<Review> reviews = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            Place place = places.get(random.nextInt(places.size()));
            
            reviews.add(createReviewBuilder(user, place)
                .rating(4 + random.nextInt(2)) // 4 or 5 stars
                .content(getPositiveKoreanReview())
                .build());
        }
        
        return reviews;
    }
    
    /**
     * Create negative reviews (1-2 stars)
     */
    public List<Review> createNegativeReviews(List<User> users, List<Place> places, int count) {
        List<Review> reviews = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            Place place = places.get(random.nextInt(places.size()));
            
            reviews.add(createReviewBuilder(user, place)
                .rating(1 + random.nextInt(2)) // 1 or 2 stars
                .content(getNegativeKoreanReview())
                .build());
        }
        
        return reviews;
    }
    
    /**
     * Create review builder with randomized data
     */
    public Review.ReviewBuilder createReviewBuilder(User user, Place place) {
        return Review.builder()
            .place(place)
            .user(user)
            .rating(generateRating())
            .content(generateReviewContent())
            .images(generateReviewImages())
            .visitDate(generateVisitDate())
            .likesCount(random.nextInt(50))
            .isVerifiedPurchase(random.nextBoolean())
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create Korean review builder with authentic content
     */
    public Review.ReviewBuilder createKoreanReviewBuilder(User user, Place place) {
        int rating = generateRating();
        String content = generateKoreanReviewContent(rating, place.getCategory());
        
        return Review.builder()
            .place(place)
            .user(user)
            .rating(rating)
            .content(content)
            .images(generateReviewImages())
            .visitDate(generateVisitDate())
            .likesCount(rating >= 4 ? random.nextInt(30) + 5 : random.nextInt(10))
            .isVerifiedPurchase(random.nextBoolean())
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create detailed review with photos
     */
    public Review createDetailedReview(User user, Place place) {
        return createReviewBuilder(user, place)
            .content(generateDetailedKoreanReview(place.getCategory()))
            .images(generateMultipleReviewImages())
            .isVerifiedPurchase(true)
            .build();
    }
    
    /**
     * Create quick review (short content)
     */
    public Review createQuickReview(User user, Place place) {
        return createReviewBuilder(user, place)
            .content(generateShortKoreanReview())
            .images(new ArrayList<>()) // No images for quick reviews
            .build();
    }
    
    private Integer generateRating() {
        // Weighted distribution: more positive reviews
        double rand = random.nextDouble();
        if (rand < 0.05) return 1;      // 5% - 1 star
        else if (rand < 0.10) return 2; // 5% - 2 stars
        else if (rand < 0.25) return 3; // 15% - 3 stars
        else if (rand < 0.60) return 4; // 35% - 4 stars
        else return 5;                  // 40% - 5 stars
    }
    
    private String generateReviewContent() {
        // Default fallback to Korean content
        return generateKoreanReviewContent(generateRating(), "일반");
    }
    
    private String generateKoreanReviewContent(int rating, String category) {
        if (rating >= 4) {
            return getPositiveKoreanReview() + generateCategorySpecificComment(category, true);
        } else if (rating <= 2) {
            return getNegativeKoreanReview() + generateCategorySpecificComment(category, false);
        } else {
            return generateNeutralKoreanReview() + generateCategorySpecificComment(category, null);
        }
    }
    
    private String getPositiveKoreanReview() {
        List<String> positiveReviews = locationData.getPositiveReviews();
        return positiveReviews.get(random.nextInt(positiveReviews.size()));
    }
    
    private String getNegativeKoreanReview() {
        List<String> negativeReviews = locationData.getNegativeReviews();
        return negativeReviews.get(random.nextInt(negativeReviews.size()));
    }
    
    private String generateNeutralKoreanReview() {
        String[] neutralReviews = {
            "그냥 보통이었어요. 특별히 나쁘지도 좋지도 않았습니다.",
            "기대했던 것만큼은 아니었지만 괜찮았어요.",
            "평범한 곳이에요. 한 번 정도는 가볼만합니다.",
            "나쁘지 않았지만 다시 가고 싶을 정도는 아니네요.",
            "그럭저럭 괜찮은 곳이에요. 기대치를 높이지 말고 가세요.",
            "무난한 선택지인 것 같아요. 큰 기대는 하지 마세요.",
            "가격 대비 적당한 수준이에요. 그냥 그래요.",
            "특별함은 없지만 실망스럽지도 않았어요."
        };
        
        return neutralReviews[random.nextInt(neutralReviews.length)];
    }
    
    private String generateCategorySpecificComment(String category, Boolean isPositive) {
        Map<String, String[]> positiveComments = Map.of(
            "음식점", new String[]{" 음식이 정말 맛있었어요!", " 서비스가 친절하고 음식도 훌륭했습니다.", " 현지인들이 많이 오는 진짜 맛집이네요!"},
            "카페", new String[]{" 커피가 정말 맛있고 분위기도 좋아요.", " 디저트가 예술이에요!", " 인스타그램 찍기 딱 좋은 곳입니다."},
            "관광지", new String[]{" 사진 찍기 너무 좋았어요!", " 한국의 아름다움을 느낄 수 있는 곳입니다.", " 가족과 함께 가기 좋은 곳이에요."},
            "숙박", new String[]{" 시설이 깨끗하고 서비스가 좋았어요.", " 위치가 너무 좋고 편안했습니다.", " 가격 대비 만족스러운 숙박이었어요."}
        );
        
        Map<String, String[]> negativeComments = Map.of(
            "음식점", new String[]{" 음식이 짜고 맛이 별로였어요.", " 서비스가 불친절하고 대기시간이 길어요.", " 가격에 비해 품질이 떨어져요."},
            "카페", new String[]{" 커피가 너무 쓰고 시끄러워요.", " 가격이 비싸고 맛은 그냥 그래요.", " 직원이 불친절하고 분위기가 별로예요."},
            "관광지", new String[]{" 사람이 너무 많고 시설이 낡았어요.", " 입장료가 비싸고 볼 게 별로 없어요.", " 교통이 불편하고 관리가 안 되어 있어요."},
            "숙박", new String[]{" 방이 좁고 시설이 노후되었어요.", " 소음이 심하고 청소가 안 되어 있어요.", " 위치가 불편하고 서비스가 별로예요."}
        );
        
        if (isPositive == null) {
            return ""; // Neutral comments don't need category-specific additions
        }
        
        Map<String, String[]> comments = isPositive ? positiveComments : negativeComments;
        String[] categoryComments = comments.get(category);
        
        if (categoryComments != null) {
            return categoryComments[random.nextInt(categoryComments.length)];
        }
        
        return "";
    }
    
    private String generateDetailedKoreanReview(String category) {
        String baseReview = generateKoreanReviewContent(4 + random.nextInt(2), category);
        
        String[] detailedAdditions = {
            "\n\n방문 팁: 주말에는 사람이 많으니 평일에 가시는 것을 추천해요.",
            "\n\n주차 정보: 근처에 공영주차장이 있어서 편리했습니다.",
            "\n\n가격 정보: 가성비가 좋아서 부담없이 이용할 수 있어요.",
            "\n\n추천 시간: 오후 늦은 시간에 가면 더 여유롭게 즐길 수 있어요.",
            "\n\n함께 방문하면 좋을 곳: 근처에 다른 명소들도 많아서 코스로 다니기 좋아요."
        };
        
        return baseReview + detailedAdditions[random.nextInt(detailedAdditions.length)];
    }
    
    private String generateShortKoreanReview() {
        String[] shortReviews = {
            "좋아요!", "별로예요.", "괜찮네요.", "최고!", "실망이에요.", 
            "추천!", "그냥 그래요.", "다시 갈게요!", "한 번이면 충분해요.", "만족해요!"
        };
        
        return shortReviews[random.nextInt(shortReviews.length)];
    }
    
    private List<String> generateReviewImages() {
        List<String> images = new ArrayList<>();
        
        // 60% chance of having images
        if (random.nextDouble() < 0.6) {
            int imageCount = 1 + random.nextInt(3); // 1-3 images
            
            for (int i = 0; i < imageCount; i++) {
                images.add("https://example.com/review-images/" + random.nextInt(10000) + ".jpg");
            }
        }
        
        return images;
    }
    
    private List<String> generateMultipleReviewImages() {
        List<String> images = new ArrayList<>();
        int imageCount = 3 + random.nextInt(3); // 3-5 images
        
        for (int i = 0; i < imageCount; i++) {
            images.add("https://example.com/review-images/detailed/" + random.nextInt(10000) + ".jpg");
        }
        
        return images;
    }
    
    private LocalDateTime generateVisitDate() {
        // Visit date should be before review creation date
        long minDay = LocalDateTime.now().minusYears(1).toLocalDate().toEpochDay();
        long maxDay = LocalDateTime.now().minusDays(1).toLocalDate().toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return java.time.LocalDate.ofEpochDay(randomDay).atTime(
            random.nextInt(24), random.nextInt(60)
        );
    }
    
    private LocalDateTime generateCreatedDate() {
        // Reviews are created within 3 months of visit
        LocalDateTime visitDate = generateVisitDate();
        return visitDate.plusDays(random.nextInt(90));
    }
}