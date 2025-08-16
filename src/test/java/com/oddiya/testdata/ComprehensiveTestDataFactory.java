package com.oddiya.testdata;

import com.oddiya.entity.*;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Comprehensive Test Data Factory for Oddiya Testing Framework
 * 
 * Provides realistic test data generation as per PRD specifications:
 * - Korean location data (Seoul, Busan, Jeju, etc.)
 * - Realistic travel plans with proper duration and preferences
 * - Spatial data for PostGIS testing
 * - Performance-optimized data generation
 * - Support for different test scenarios (unit, integration, performance)
 */
@Component
public class ComprehensiveTestDataFactory {

    private final Faker faker;
    private final Random random;

    // Korean location data as per PRD focus on South Korea
    private final List<KoreanLocation> koreanLocations;
    private final List<String> koreanFoodCategories;
    private final List<String> travelPreferences;

    public ComprehensiveTestDataFactory() {
        this.faker = new Faker();
        this.random = new Random();
        this.koreanLocations = initializeKoreanLocations();
        this.koreanFoodCategories = initializeKoreanFoodCategories();
        this.travelPreferences = initializeTravelPreferences();
    }

    // ============================================================
    // USER DATA GENERATION
    // ============================================================

    /**
     * Create a test user with realistic Korean or international profile
     */
    public User createTestUser() {
        return createTestUser("test-user-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public User createTestUser(String id) {
        boolean isKorean = random.nextBoolean();
        
        return User.builder()
                .id(id)
                .email(faker.internet().emailAddress())
                .nickname(isKorean ? generateKoreanNickname() : faker.internet().username())
                .profileImageUrl(faker.internet().url())
                .provider("google")
                .providerId(UUID.randomUUID().toString())
                .preferences(generateUserPreferences())
                .travelPreferences(generateTravelPreferences())
                .isEmailVerified(true)
                .isPremium(random.nextDouble() < 0.2) // 20% premium users
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(random.nextInt(365)))
                .updatedAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                .build();
    }

    /**
     * Create multiple test users for bulk testing
     */
    public List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createTestUser());
        }
        return users;
    }

    // ============================================================
    // PLACE DATA GENERATION (PostGIS Spatial Testing)
    // ============================================================

    /**
     * Create a test place with realistic Korean location data
     */
    public Place createTestPlace() {
        return createTestPlace("test-place-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public Place createTestPlace(String id) {
        KoreanLocation location = koreanLocations.get(random.nextInt(koreanLocations.size()));
        String category = getRandomCategory();
        
        return Place.builder()
                .id(id)
                .name(generatePlaceName(category, location.city()))
                .description(generatePlaceDescription(category))
                .category(category)
                .address(location.generateAddress(faker))
                .latitude(location.latitude() + (random.nextGaussian() * 0.01)) // Add some variance
                .longitude(location.longitude() + (random.nextGaussian() * 0.01))
                .rating(3.0 + random.nextDouble() * 2.0) // Rating between 3.0-5.0
                .reviewCount(random.nextInt(500) + 10)
                .phoneNumber(generateKoreanPhoneNumber())
                .website(faker.internet().url())
                .openingHours(generateOpeningHours())
                // priceRange field doesn't exist in Place entity
                .images(generateImageUrls())
                .isVerified(random.nextBoolean())
                .popularityScore(random.nextDouble() * 100)
                .build();
    }

    /**
     * Create places within a specific radius for spatial testing
     */
    public List<Place> createPlacesNear(double centerLat, double centerLng, double radiusKm, int count) {
        List<Place> places = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Generate random coordinates within radius
            double[] coords = generateRandomCoordinatesInRadius(centerLat, centerLng, radiusKm);
            
            Place place = createTestPlace();
            place.setLatitude(coords[0]);
            place.setLongitude(coords[1]);
            places.add(place);
        }
        
        return places;
    }

    // ============================================================
    // TRAVEL PLAN DATA GENERATION
    // ============================================================

    /**
     * Create a comprehensive test travel plan
     */
    public TravelPlan createTestTravelPlan() {
        return createTestTravelPlan("test-plan-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public TravelPlan createTestTravelPlan(String id) {
        KoreanLocation destination = koreanLocations.get(random.nextInt(koreanLocations.size()));
        int durationDays = random.nextInt(10) + 1; // 1-10 days
        LocalDate startDate = LocalDate.now().plusDays(random.nextInt(90) + 1); // 1-90 days from now
        
        return TravelPlan.builder()
                .id(id)
                .title(generateTravelPlanTitle(destination.city(), durationDays))
                .description(generateTravelPlanDescription(destination.city(), durationDays))
                .destination(destination.city())
                .startDate(startDate)
                .endDate(startDate.plusDays(durationDays - 1))
                .isPublic(random.nextBoolean())
                .isAiGenerated(random.nextBoolean())
                .coverImageUrl(faker.internet().url())
                .tags(generateTravelTags())
                .status(getRandomTravelPlanStatus())
                .viewCount((long) random.nextInt(1000))
                .likeCount((long) random.nextInt(200))
                .shareCount((long) random.nextInt(50))
                .saveCount((long) random.nextInt(100))
                .createdAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                .updatedAt(LocalDateTime.now().minusDays(random.nextInt(7)))
                .build();
    }

    /**
     * Create travel plan with itinerary items for comprehensive testing
     */
    public TravelPlan createTravelPlanWithItinerary(User user, int days) {
        TravelPlan plan = createTestTravelPlan();
        plan.setUser(user);
        
        List<ItineraryItem> items = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            int activitiesPerDay = random.nextInt(4) + 2; // 2-5 activities per day
            for (int seq = 1; seq <= activitiesPerDay; seq++) {
                ItineraryItem item = createItineraryItem(plan, day, seq);
                items.add(item);
            }
        }
        
        plan.setItineraryItems(items);
        return plan;
    }

    // ============================================================
    // ITINERARY ITEM DATA GENERATION
    // ============================================================

    public ItineraryItem createItineraryItem(TravelPlan plan, int dayNumber, int sequence) {
        Place place = createTestPlace();
        LocalTime startTime = LocalTime.of(8 + (sequence - 1) * 3, 0); // Spread throughout day
        LocalTime endTime = startTime.plusHours(random.nextInt(3) + 1); // 1-3 hours per activity
        
        return ItineraryItem.builder()
                // id is auto-generated by JPA
                .travelPlan(plan)
                .place(place)
                .title("Visit " + place.getName())
                .description("Explore this amazing place")
                .placeName(place.getName())
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .dayNumber(dayNumber)
                .sequence(sequence)
                .startTime(LocalDateTime.of(plan.getStartDate().plusDays(dayNumber - 1), startTime))
                .endTime(LocalDateTime.of(plan.getStartDate().plusDays(dayNumber - 1), endTime))
                .durationMinutes((int) java.time.Duration.between(startTime, endTime).toMinutes())
                .notes(generateActivityNotes())
                .transportMode(getRandomTransportMode())
                .transportDurationMinutes(random.nextInt(30) + 5) // 5-35 minutes
                .estimatedCost(BigDecimal.valueOf(random.nextInt(50000) + 10000)) // 10,000-60,000 KRW
                .isCompleted(false)
                .build();
    }

    // ============================================================
    // PERFORMANCE TESTING DATA GENERATION
    // ============================================================

    /**
     * Generate large dataset for performance testing
     * As per PRD: support 10,000+ concurrent users
     */
    public PerformanceTestData createPerformanceTestData() {
        int userCount = 1000; // Reasonable size for local testing
        int placeCount = 5000;
        int planCount = 2000;
        
        List<User> users = createTestUsers(userCount);
        List<Place> places = new ArrayList<>();
        List<TravelPlan> plans = new ArrayList<>();
        
        // Create places across major Korean cities
        for (KoreanLocation location : koreanLocations) {
            int placesPerCity = placeCount / koreanLocations.size();
            places.addAll(createPlacesNear(location.latitude(), location.longitude(), 20.0, placesPerCity));
        }
        
        // Create travel plans with realistic distributions
        for (int i = 0; i < planCount; i++) {
            User user = users.get(random.nextInt(users.size()));
            TravelPlan plan = createTravelPlanWithItinerary(user, random.nextInt(7) + 1);
            plans.add(plan);
        }
        
        return new PerformanceTestData(users, places, plans);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private List<KoreanLocation> initializeKoreanLocations() {
        return Arrays.asList(
            new KoreanLocation("Seoul", 37.5665, 126.9780),
            new KoreanLocation("Busan", 35.1796, 129.0756),
            new KoreanLocation("Incheon", 37.4563, 126.7052),
            new KoreanLocation("Daegu", 35.8714, 128.6014),
            new KoreanLocation("Daejeon", 36.3504, 127.3845),
            new KoreanLocation("Gwangju", 35.1595, 126.8526),
            new KoreanLocation("Suwon", 37.2636, 127.0286),
            new KoreanLocation("Jeju City", 33.4996, 126.5312),
            new KoreanLocation("Gangneung", 37.7519, 128.8761),
            new KoreanLocation("Gyeongju", 35.8562, 129.2247)
        );
    }

    private List<String> initializeKoreanFoodCategories() {
        return Arrays.asList(
            "Korean BBQ", "Korean Traditional", "Korean Fried Chicken", "Korean Dessert",
            "Kimchi Stew", "Bibimbap", "Bulgogi", "Korean Street Food", "Korean Bakery",
            "Korean Hot Pot", "Korean Noodles", "Korean Seafood", "Korean Vegetarian"
        );
    }

    private List<String> initializeTravelPreferences() {
        return Arrays.asList(
            "culture", "food", "history", "nature", "shopping", "nightlife", 
            "adventure", "relaxation", "photography", "architecture", "art", "music"
        );
    }

    private String generateKoreanNickname() {
        String[] koreanPrefixes = {"Seoul", "Busan", "Hanbok", "Kimchi", "Bibim", "Gang", "Min", "Park", "Kim", "Lee"};
        String[] koreanSuffixes = {"Lover", "Explorer", "Traveler", "Fan", "Seeker", "Hunter", "Master", "Pro"};
        return koreanPrefixes[random.nextInt(koreanPrefixes.length)] + 
               koreanSuffixes[random.nextInt(koreanSuffixes.length)] + 
               random.nextInt(1000);
    }

    private Map<String, String> generateUserPreferences() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("language", random.nextBoolean() ? "ko" : "en");
        preferences.put("currency", "KRW");
        preferences.put("timezone", "Asia/Seoul");
        preferences.put("notifications", String.valueOf(random.nextBoolean()));
        return preferences;
    }

    private Map<String, String> generateTravelPreferences() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("budget", getRandomBudgetRange());
        prefs.put("style", getRandomTravelStyle());
        prefs.put("pace", getRandomTravelPace());
        
        // Add 2-4 interest categories
        Set<String> interests = new HashSet<>();
        while (interests.size() < random.nextInt(3) + 2) {
            interests.add(travelPreferences.get(random.nextInt(travelPreferences.size())));
        }
        prefs.put("interests", String.join(",", interests));
        
        return prefs;
    }

    private String generatePlaceName(String category, String city) {
        if (category.contains("restaurant") || category.contains("Korean")) {
            return city + " " + koreanFoodCategories.get(random.nextInt(koreanFoodCategories.size())) + " House";
        } else if (category.equals("attraction")) {
            return city + " " + faker.company().name() + " Tower";
        } else {
            return faker.company().name() + " " + city;
        }
    }

    private String generatePlaceDescription(String category) {
        return "A wonderful " + category.toLowerCase() + " experience in the heart of Korea. " +
               "Perfect for travelers looking for authentic Korean culture and hospitality.";
    }

    private String getRandomCategory() {
        String[] categories = {"restaurant", "attraction", "hotel", "shopping", "entertainment", "cultural", "nature"};
        return categories[random.nextInt(categories.length)];
    }

    private String generateKoreanPhoneNumber() {
        return "+82-" + (random.nextInt(9) + 1) + "-" + 
               (1000 + random.nextInt(9000)) + "-" + 
               (1000 + random.nextInt(9000));
    }

    private Map<String, String> generateOpeningHours() {
        Map<String, String> hours = new HashMap<>();
        String openTime = (8 + random.nextInt(4)) + ":00"; // 8-11 AM
        String closeTime = (18 + random.nextInt(6)) + ":00"; // 6-11 PM
        
        hours.put("monday", openTime + "-" + closeTime);
        hours.put("tuesday", openTime + "-" + closeTime);
        hours.put("wednesday", openTime + "-" + closeTime);
        hours.put("thursday", openTime + "-" + closeTime);
        hours.put("friday", openTime + "-" + closeTime);
        hours.put("saturday", (9 + random.nextInt(2)) + ":00-" + (19 + random.nextInt(5)) + ":00");
        hours.put("sunday", random.nextBoolean() ? "Closed" : (10 + random.nextInt(2)) + ":00-" + (17 + random.nextInt(3)) + ":00");
        
        return hours;
    }

    private String getRandomPriceRange() {
        String[] ranges = {"₩", "₩₩", "₩₩₩", "₩₩₩₩"};
        return ranges[random.nextInt(ranges.length)];
    }

    private List<String> generateImageUrls() {
        List<String> urls = new ArrayList<>();
        int imageCount = random.nextInt(3) + 1; // 1-3 images
        for (int i = 0; i < imageCount; i++) {
            urls.add(faker.internet().url());
        }
        return urls;
    }

    private Set<String> generateAmenities(String category) {
        Set<String> amenities = new HashSet<>();
        String[] commonAmenities = {"WiFi", "Parking", "Air Conditioning", "Credit Cards Accepted"};
        String[] restaurantAmenities = {"Delivery", "Takeout", "Reservations", "Private Dining"};
        String[] hotelAmenities = {"Spa", "Fitness Center", "Pool", "Room Service", "Breakfast"};
        
        // Add common amenities
        for (String amenity : commonAmenities) {
            if (random.nextBoolean()) amenities.add(amenity);
        }
        
        // Add category-specific amenities
        if (category.contains("restaurant") || category.contains("Korean")) {
            for (String amenity : restaurantAmenities) {
                if (random.nextBoolean()) amenities.add(amenity);
            }
        } else if (category.equals("hotel")) {
            for (String amenity : hotelAmenities) {
                if (random.nextBoolean()) amenities.add(amenity);
            }
        }
        
        return amenities;
    }

    private String generateTravelPlanTitle(String destination, int days) {
        String[] adjectives = {"Amazing", "Ultimate", "Perfect", "Incredible", "Unforgettable", "Epic", "Magical"};
        return adjectives[random.nextInt(adjectives.length)] + " " + days + "-Day " + destination + " Adventure";
    }

    private String generateTravelPlanDescription(String destination, int days) {
        return "Explore the best of " + destination + " in " + days + " days! " +
               "This carefully crafted itinerary includes must-see attractions, delicious local cuisine, " +
               "and authentic cultural experiences that will make your trip unforgettable.";
    }

    private List<String> generateTravelTags() {
        List<String> allTags = Arrays.asList(
            "korea", "seoul", "busan", "culture", "food", "history", "temple", "palace", 
            "shopping", "nightlife", "nature", "mountain", "beach", "traditional", "modern",
            "family-friendly", "romantic", "adventure", "budget-friendly", "luxury"
        );
        
        List<String> tags = new ArrayList<>();
        int tagCount = random.nextInt(5) + 2; // 2-6 tags
        Collections.shuffle(allTags);
        
        for (int i = 0; i < tagCount && i < allTags.size(); i++) {
            tags.add(allTags.get(i));
        }
        
        return tags;
    }

    private TravelPlanStatus getRandomTravelPlanStatus() {
        TravelPlanStatus[] statuses = TravelPlanStatus.values();
        return statuses[random.nextInt(statuses.length)];
    }

    private String generateActivityNotes() {
        String[] notes = {
            "Don't forget to bring your camera!",
            "Try the local specialty here.",
            "Book in advance during peak season.",
            "Great spot for photos.",
            "Ask locals for hidden gems nearby.",
            "Perfect for sunset views.",
            "Family-friendly activity.",
            "Best visited in the morning."
        };
        return notes[random.nextInt(notes.length)];
    }

    private String getRandomTransportMode() {
        String[] modes = {"walking", "subway", "bus", "taxi", "car", "bike"};
        return modes[random.nextInt(modes.length)];
    }

    private String getRandomBudgetRange() {
        String[] budgets = {"budget", "mid-range", "luxury"};
        return budgets[random.nextInt(budgets.length)];
    }

    private String getRandomTravelStyle() {
        String[] styles = {"cultural", "adventure", "relaxation", "foodie", "family", "romantic", "business"};
        return styles[random.nextInt(styles.length)];
    }

    private String getRandomTravelPace() {
        String[] paces = {"slow", "moderate", "fast"};
        return paces[random.nextInt(paces.length)];
    }

    /**
     * Generate random coordinates within a radius (in kilometers)
     */
    private double[] generateRandomCoordinatesInRadius(double centerLat, double centerLng, double radiusKm) {
        double radiusInDegrees = radiusKm / 111.0; // Rough conversion
        
        double u = random.nextDouble();
        double v = random.nextDouble();
        
        double w = radiusInDegrees * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        
        double deltaLat = w * Math.cos(t);
        double deltaLng = w * Math.sin(t);
        
        return new double[]{centerLat + deltaLat, centerLng + deltaLng};
    }

    /**
     * Korean location record for spatial testing
     */
    public record KoreanLocation(String city, double latitude, double longitude) {
        public String generateAddress(Faker faker) {
            return faker.address().streetAddress() + ", " + city + ", South Korea";
        }
    }

    /**
     * Performance test data container
     */
    public record PerformanceTestData(
        List<User> users,
        List<Place> places,
        List<TravelPlan> travelPlans
    ) {}
}