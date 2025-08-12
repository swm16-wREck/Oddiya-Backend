package com.oddiya.testdata.factory;

import com.oddiya.entity.Place;
import com.oddiya.testdata.data.KoreanLocationData;
import com.oddiya.testdata.data.KoreanLocationData.LocationData;
import net.datafaker.Faker;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for Place entities using Builder pattern
 * Generates realistic Korean place data including Seoul landmarks, restaurants, and hotels
 */
public class PlaceTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final KoreanLocationData locationData = new KoreanLocationData();
    private final Random random = new Random(12345);
    
    /**
     * Create a single place with default settings
     */
    public Place createPlace() {
        return createPlaceBuilder().build();
    }
    
    /**
     * Create multiple places
     */
    public List<Place> createPlaces(int count) {
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            places.add(createPlaceBuilder().build());
        }
        return places;
    }
    
    /**
     * Create realistic Korean places from predefined data
     */
    public List<Place> createKoreanPlaces(int count) {
        List<Place> places = new ArrayList<>();
        
        // Add all predefined Seoul landmarks
        for (LocationData location : locationData.getSeoulLandmarks()) {
            places.add(createPlaceFromLocationData(location));
        }
        
        // Add all predefined Seoul restaurants
        for (LocationData location : locationData.getSeoulRestaurants()) {
            places.add(createPlaceFromLocationData(location));
        }
        
        // Add all predefined Seoul hotels
        for (LocationData location : locationData.getSeoulHotels()) {
            places.add(createPlaceFromLocationData(location));
        }
        
        // Add Busan attractions
        for (LocationData location : locationData.getBusanAttractions()) {
            places.add(createPlaceFromLocationData(location));
        }
        
        // Fill remaining with random places
        while (places.size() < count) {
            places.add(createRandomKoreanPlace());
        }
        
        return places.subList(0, Math.min(count, places.size()));
    }
    
    /**
     * Create places specifically in Seoul
     */
    public List<Place> createSeoulPlaces() {
        List<Place> places = new ArrayList<>();
        
        locationData.getSeoulLandmarks().forEach(location -> 
            places.add(createPlaceFromLocationData(location)));
        locationData.getSeoulRestaurants().forEach(location -> 
            places.add(createPlaceFromLocationData(location)));
        locationData.getSeoulHotels().forEach(location -> 
            places.add(createPlaceFromLocationData(location)));
        
        return places;
    }
    
    /**
     * Create popular tourist attractions
     */
    public List<Place> createTouristAttractions(int count) {
        List<Place> attractions = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            attractions.add(createPlaceBuilder()
                .category("관광지")
                .rating(4.0 + random.nextDouble() * 1.0) // 4.0-5.0 rating
                .isVerified(true)
                .popularityScore(70.0 + random.nextDouble() * 30.0) // High popularity
                .build());
        }
        
        return attractions;
    }
    
    /**
     * Create restaurants with various categories
     */
    public List<Place> createRestaurants(int count) {
        String[] restaurantCategories = {"한식", "중식", "일식", "양식", "분식", "카페", "치킨", "피자"};
        List<Place> restaurants = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            restaurants.add(createPlaceBuilder()
                .category(restaurantCategories[random.nextInt(restaurantCategories.length)])
                .rating(3.5 + random.nextDouble() * 1.5) // 3.5-5.0 rating
                .openingHours(generateOpeningHours())
                .build());
        }
        
        return restaurants;
    }
    
    /**
     * Create place builder with randomized Korean data
     */
    public Place.PlaceBuilder<?, ?> createPlaceBuilder() {
        String category = getRandomCategory();
        
        return Place.builder()
            .naverPlaceId(generateNaverPlaceId())
            .name(generatePlaceName(category))
            .category(category)
            .description(generateDescription(category))
            .address(generateAddress())
            .roadAddress(generateRoadAddress())
            .latitude(generateSeoulLatitude())
            .longitude(generateSeoulLongitude())
            .phoneNumber(faker.phoneNumber().phoneNumber())
            .website(faker.internet().url())
            .openingHours(generateOpeningHours())
            .images(generateImages())
            .tags(generateTags(category))
            .rating(generateRating())
            .reviewCount(random.nextInt(500))
            .bookmarkCount(random.nextInt(100))
            .viewCount(random.nextLong(10000))
            .isVerified(random.nextBoolean())
            .popularityScore(random.nextDouble() * 100)
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create place from predefined location data
     */
    private Place createPlaceFromLocationData(LocationData location) {
        return Place.builder()
            .naverPlaceId(generateNaverPlaceId())
            .name(location.koreanName)
            .category(location.category)
            .description(location.description)
            .address(location.address)
            .roadAddress(location.address) // Use same address for simplicity
            .latitude(location.latitude)
            .longitude(location.longitude)
            .phoneNumber(faker.phoneNumber().phoneNumber())
            .website(faker.internet().url())
            .openingHours(generateOpeningHours())
            .images(generateImages())
            .tags(new ArrayList<>(location.tags))
            .rating(4.0 + random.nextDouble() * 1.0) // Good ratings for known places
            .reviewCount(50 + random.nextInt(450))
            .bookmarkCount(10 + random.nextInt(90))
            .viewCount(1000L + random.nextLong(9000))
            .isVerified(true) // Known places are verified
            .popularityScore(60.0 + random.nextDouble() * 40.0)
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create random Korean place
     */
    private Place createRandomKoreanPlace() {
        String[] categories = {"관광지", "음식점", "카페", "숙박", "쇼핑", "문화시설", "공원", "체험"};
        String category = categories[random.nextInt(categories.length)];
        
        return createPlaceBuilder()
            .category(category)
            .build();
    }
    
    private String generateNaverPlaceId() {
        return "naver_" + random.nextInt(Integer.MAX_VALUE);
    }
    
    private String getRandomCategory() {
        String[] categories = {
            "관광지", "음식점", "카페", "숙박", "쇼핑몰", "문화시설", "공원", 
            "체험장", "박물관", "갤러리", "전시장", "클럽", "바"
        };
        return categories[random.nextInt(categories.length)];
    }
    
    private String generatePlaceName(String category) {
        String[] prefixes = {"서울", "강남", "홍대", "명동", "이태원", "압구정", "신촌", "동대문"};
        String[] suffixes;
        
        switch (category) {
            case "음식점":
                suffixes = new String[]{"맛집", "식당", "레스토랑", "한정식", "바베큐"};
                break;
            case "카페":
                suffixes = new String[]{"카페", "커피숍", "디저트", "브런치"};
                break;
            case "숙박":
                suffixes = new String[]{"호텔", "모텔", "게스트하우스", "펜션", "리조트"};
                break;
            case "관광지":
                suffixes = new String[]{"타워", "궁", "공원", "박물관", "전시관"};
                break;
            default:
                suffixes = new String[]{"플레이스", "센터", "스팟", "하우스", "스튜디오"};
        }
        
        return prefixes[random.nextInt(prefixes.length)] + " " + 
               suffixes[random.nextInt(suffixes.length)];
    }
    
    private String generateDescription(String category) {
        Map<String, String[]> descriptions = Map.of(
            "관광지", new String[]{
                "서울의 대표적인 관광명소로 많은 사람들이 찾는 곳입니다.",
                "한국의 전통과 현대가 어우러진 아름다운 장소입니다.",
                "사진 촬영하기 좋은 인스타그램 핫플레이스입니다."
            },
            "음식점", new String[]{
                "정통 한식을 맛볼 수 있는 현지인들이 인정하는 맛집입니다.",
                "신선한 재료로 만든 건강하고 맛있는 음식을 제공합니다.",
                "가족 단위 방문객들에게 인기가 높은 레스토랑입니다."
            },
            "카페", new String[]{
                "분위기 좋은 인테리어와 맛있는 커피로 유명한 카페입니다.",
                "조용하고 편안한 공간에서 여유로운 시간을 보낼 수 있습니다.",
                "디저트가 맛있기로 소문난 트렌디한 카페입니다."
            }
        );
        
        String[] categoryDescriptions = descriptions.get(category);
        if (categoryDescriptions != null) {
            return categoryDescriptions[random.nextInt(categoryDescriptions.length)];
        }
        
        return "특별한 경험을 제공하는 추천 장소입니다.";
    }
    
    private String generateAddress() {
        String[] districts = {"종로구", "중구", "강남구", "서초구", "마포구", "용산구", "성동구", "광진구"};
        String[] streets = {"세종대로", "테헤란로", "강남대로", "홍익로", "이태원로", "명동길", "인사동길", "압구정로"};
        
        return "서울 " + districts[random.nextInt(districts.length)] + " " + 
               streets[random.nextInt(streets.length)] + " " + (random.nextInt(999) + 1);
    }
    
    private String generateRoadAddress() {
        return generateAddress() + "번길 " + (random.nextInt(99) + 1);
    }
    
    private Double generateSeoulLatitude() {
        // Seoul latitude range: 37.4-37.7
        return 37.4 + random.nextDouble() * 0.3;
    }
    
    private Double generateSeoulLongitude() {
        // Seoul longitude range: 126.8-127.2
        return 126.8 + random.nextDouble() * 0.4;
    }
    
    private Map<String, String> generateOpeningHours() {
        Map<String, String> hours = new HashMap<>();
        String[] days = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
        
        boolean isRestaurant = random.nextBoolean();
        
        for (String day : days) {
            if (random.nextDouble() < 0.1) { // 10% chance of being closed
                hours.put(day, "휴무");
            } else if (isRestaurant) {
                hours.put(day, "11:00-22:00");
            } else {
                hours.put(day, "09:00-18:00");
            }
        }
        
        return hours;
    }
    
    private List<String> generateImages() {
        List<String> images = new ArrayList<>();
        int imageCount = 1 + random.nextInt(5); // 1-5 images
        
        for (int i = 0; i < imageCount; i++) {
            images.add("https://example.com/images/place" + random.nextInt(1000) + ".jpg");
        }
        
        return images;
    }
    
    private List<String> generateTags(String category) {
        Map<String, String[]> categoryTags = Map.of(
            "관광지", new String[]{"사진촬영", "데이트", "가족여행", "역사", "문화", "전망"},
            "음식점", new String[]{"맛집", "현지음식", "가성비", "분위기", "데이트", "회식"},
            "카페", new String[]{"커피", "디저트", "분위기", "와이파이", "스터디", "모임"},
            "숙박", new String[]{"깔끔", "위치좋음", "서비스", "가성비", "편의시설", "조용함"}
        );
        
        String[] availableTags = categoryTags.getOrDefault(category, 
            new String[]{"추천", "인기", "좋음", "깨끗함", "친절", "편리"});
        
        List<String> tags = new ArrayList<>();
        int tagCount = 2 + random.nextInt(4); // 2-5 tags
        
        for (int i = 0; i < tagCount; i++) {
            String tag = availableTags[random.nextInt(availableTags.length)];
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        
        return tags;
    }
    
    private Double generateRating() {
        // Generate ratings between 2.0 and 5.0, with bias towards higher ratings
        double rating = 2.0 + random.nextGaussian() * 0.8 + 1.5;
        return Math.max(1.0, Math.min(5.0, Math.round(rating * 10.0) / 10.0));
    }
    
    private LocalDateTime generateCreatedDate() {
        // Generate dates within the last 3 years
        long minDay = LocalDateTime.now().minusYears(3).toLocalDate().toEpochDay();
        long maxDay = LocalDateTime.now().toLocalDate().toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return java.time.LocalDate.ofEpochDay(randomDay).atStartOfDay();
    }
}