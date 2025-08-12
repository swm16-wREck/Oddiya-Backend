package com.oddiya.testdata;

import com.oddiya.entity.*;
import com.oddiya.testdata.factory.*;
import com.oddiya.testdata.data.KoreanLocationData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive test data generator for Oddiya application
 * Provides methods to generate realistic Korean travel data for testing
 */
@Service
public class TestDataGenerator {
    
    private final TestDataGenerationFramework framework = new TestDataGenerationFramework();
    private final UserTestDataFactory userFactory = new UserTestDataFactory();
    private final PlaceTestDataFactory placeFactory = new PlaceTestDataFactory();
    private final TravelPlanTestDataFactory travelPlanFactory = new TravelPlanTestDataFactory();
    private final ReviewTestDataFactory reviewFactory = new ReviewTestDataFactory();
    private final ItineraryItemTestDataFactory itineraryFactory = new ItineraryItemTestDataFactory();
    private final VideoTestDataFactory videoFactory = new VideoTestDataFactory();
    
    /**
     * Generate complete test dataset
     */
    public CompleteTestData generateCompleteTestData() {
        return generateCompleteTestData(TestDataSize.MEDIUM);
    }
    
    /**
     * Generate complete test dataset with specific size
     */
    public CompleteTestData generateCompleteTestData(TestDataSize size) {
        TestDataGenerationFramework.TestDataSet dataSet = switch (size) {
            case SMALL -> framework.generateSmallDataSet();
            case MEDIUM -> framework.generateCompleteDataSet();
            case LARGE -> framework.generateLargeDataSet();
        };
        
        // Generate additional related data
        List<ItineraryItem> itineraryItems = dataSet.travelPlans.stream()
            .flatMap(plan -> itineraryFactory.createItineraryItems(plan, dataSet.places, 4).stream())
            .collect(Collectors.toList());
            
        List<Video> videos = videoFactory.createKoreanTravelVideos(dataSet.users, dataSet.users.size() * 2);
        
        return new CompleteTestData(
            dataSet.users,
            dataSet.places,
            dataSet.travelPlans,
            dataSet.reviews,
            itineraryItems,
            videos
        );
    }
    
    /**
     * Generate Seoul-specific test data
     */
    public SeoulTestData generateSeoulTestData() {
        List<User> users = userFactory.createUserPersonas();
        List<Place> seoulPlaces = placeFactory.createSeoulPlaces();
        List<TravelPlan> seoulPlans = travelPlanFactory.createSeoulTravelScenarios(users, 
            new KoreanLocationData().getSeoulLandmarks());
        List<Review> reviews = reviewFactory.createKoreanReviews(users, seoulPlaces, 50);
        List<ItineraryItem> itineraryItems = seoulPlans.stream()
            .flatMap(plan -> itineraryFactory.createSeoulItinerary(plan, seoulPlaces).stream())
            .collect(Collectors.toList());
        List<Video> videos = videoFactory.createKoreanTravelVideos(users, 10);
        
        return new SeoulTestData(users, seoulPlaces, seoulPlans, reviews, itineraryItems, videos);
    }
    
    /**
     * Generate persona-based test data
     */
    public PersonaTestData generatePersonaTestData() {
        List<User> personas = userFactory.createUserPersonas();
        List<Place> places = placeFactory.createKoreanPlaces(100);
        List<TravelPlan> personalizedPlans = travelPlanFactory.createPersonaTravelPlans(personas, places);
        List<Review> reviews = reviewFactory.createKoreanReviews(personas, places, 80);
        List<Video> videos = videoFactory.createKoreanTravelVideos(personas, 15);
        
        return new PersonaTestData(personas, places, personalizedPlans, reviews, videos);
    }
    
    /**
     * Generate test data for specific scenarios
     */
    public ScenarioTestData generateScenarioTestData(TravelScenario scenario) {
        List<User> users = userFactory.createUserPersonas();
        List<Place> places = placeFactory.createKoreanPlaces(50);
        
        return switch (scenario) {
            case FAMILY_VACATION -> generateFamilyVacationData(users, places);
            case SOLO_ADVENTURE -> generateSoloAdventureData(users, places);
            case BUSINESS_TRIP -> generateBusinessTripData(users, places);
            case CULTURAL_TOUR -> generateCulturalTourData(users, places);
            case FOODIE_TOUR -> generateFoodieTourData(users, places);
        };
    }
    
    /**
     * Generate SQL seed data
     */
    public String generateSqlSeedData() {
        try {
            return Files.readString(Paths.get("src/test/resources/test-data/sql/seed-test-data.sql"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SQL seed data", e);
        }
    }
    
    /**
     * Generate JSON fixtures for API testing
     */
    public ApiTestFixtures generateApiTestFixtures() {
        try {
            String usersJson = Files.readString(Paths.get("src/test/resources/test-data/fixtures/users.json"));
            String placesJson = Files.readString(Paths.get("src/test/resources/test-data/fixtures/places.json"));
            String travelPlansJson = Files.readString(Paths.get("src/test/resources/test-data/fixtures/travel-plans.json"));
            String reviewsJson = Files.readString(Paths.get("src/test/resources/test-data/fixtures/reviews.json"));
            
            return new ApiTestFixtures(usersJson, placesJson, travelPlansJson, reviewsJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON fixtures", e);
        }
    }
    
    // Private methods for scenario-specific data generation
    
    private ScenarioTestData generateFamilyVacationData(List<User> users, List<Place> places) {
        User familyUser = users.stream()
            .filter(u -> "가족여행맘".equals(u.getNickname()))
            .findFirst()
            .orElse(users.get(0));
            
        TravelPlan familyPlan = travelPlanFactory.createTravelPlanBuilder(familyUser, places)
            .numberOfPeople(4)
            .title("가족과 함께하는 서울 여행")
            .build();
            
        List<Review> familyReviews = reviewFactory.createPositiveReviews(List.of(familyUser), places, 10);
        List<ItineraryItem> familyItinerary = itineraryFactory.createDayItinerary(familyPlan, places, 1, 5);
        
        return new ScenarioTestData(List.of(familyUser), places, List.of(familyPlan), 
            familyReviews, familyItinerary, TravelScenario.FAMILY_VACATION);
    }
    
    private ScenarioTestData generateSoloAdventureData(List<User> users, List<Place> places) {
        User soloUser = users.stream()
            .filter(u -> "모험가민수".equals(u.getNickname()))
            .findFirst()
            .orElse(users.get(0));
            
        TravelPlan soloPlan = travelPlanFactory.createBudgetTravelPlan(soloUser, places);
        List<Review> soloReviews = reviewFactory.createReviews(15, List.of(soloUser), places);
        List<ItineraryItem> soloItinerary = itineraryFactory.createItineraryItems(soloPlan, places, 3);
        
        return new ScenarioTestData(List.of(soloUser), places, List.of(soloPlan), 
            soloReviews, soloItinerary, TravelScenario.SOLO_ADVENTURE);
    }
    
    private ScenarioTestData generateBusinessTripData(List<User> users, List<Place> places) {
        User businessUser = userFactory.createUserBuilder()
            .nickname("출장전문가")
            .travelPreferences(java.util.Map.of(
                "여행스타일", "비즈니스",
                "숙박선호", "비즈니스호텔",
                "동행타입", "솔로"
            ))
            .build();
            
        TravelPlan businessPlan = travelPlanFactory.createTravelPlanBuilder(businessUser, places)
            .title("서울 출장 + 개인 일정")
            .numberOfPeople(1)
            .build();
            
        List<Review> businessReviews = reviewFactory.createReviews(8, List.of(businessUser), places);
        List<ItineraryItem> businessItinerary = itineraryFactory.createItineraryItems(businessPlan, places, 3);
        
        return new ScenarioTestData(List.of(businessUser), places, List.of(businessPlan), 
            businessReviews, businessItinerary, TravelScenario.BUSINESS_TRIP);
    }
    
    private ScenarioTestData generateCulturalTourData(List<User> users, List<Place> places) {
        User culturalUser = users.stream()
            .filter(u -> "케이팝러버".equals(u.getNickname()))
            .findFirst()
            .orElse(users.get(0));
            
        List<Place> culturalPlaces = places.stream()
            .filter(p -> p.getCategory().equals("관광지") || p.getCategory().equals("문화지구"))
            .collect(Collectors.toList());
            
        TravelPlan culturalPlan = travelPlanFactory.createTravelPlanBuilder(culturalUser, culturalPlaces)
            .title("서울 K-pop 문화 투어")
            .build();
            
        List<Review> culturalReviews = reviewFactory.createPositiveReviews(List.of(culturalUser), culturalPlaces, 12);
        List<ItineraryItem> culturalItinerary = itineraryFactory.createItineraryItems(culturalPlan, culturalPlaces, 4);
        
        return new ScenarioTestData(List.of(culturalUser), culturalPlaces, List.of(culturalPlan), 
            culturalReviews, culturalItinerary, TravelScenario.CULTURAL_TOUR);
    }
    
    private ScenarioTestData generateFoodieTourData(List<User> users, List<Place> places) {
        User foodieUser = users.stream()
            .filter(u -> "맛집헌터".equals(u.getNickname()))
            .findFirst()
            .orElse(users.get(0));
            
        List<Place> restaurants = places.stream()
            .filter(p -> p.getCategory().contains("식") || p.getCategory().equals("카페"))
            .collect(Collectors.toList());
            
        TravelPlan foodiePlan = travelPlanFactory.createTravelPlanBuilder(foodieUser, restaurants)
            .title("서울 맛집 완전정복")
            .build();
            
        List<Review> foodieReviews = reviewFactory.createPositiveReviews(List.of(foodieUser), restaurants, 20);
        List<ItineraryItem> foodieItinerary = itineraryFactory.createItineraryItems(foodiePlan, restaurants, 5);
        
        return new ScenarioTestData(List.of(foodieUser), restaurants, List.of(foodiePlan), 
            foodieReviews, foodieItinerary, TravelScenario.FOODIE_TOUR);
    }
    
    // Data container classes
    
    public enum TestDataSize {
        SMALL, MEDIUM, LARGE
    }
    
    public enum TravelScenario {
        FAMILY_VACATION, SOLO_ADVENTURE, BUSINESS_TRIP, CULTURAL_TOUR, FOODIE_TOUR
    }
    
    public record CompleteTestData(
        List<User> users,
        List<Place> places,
        List<TravelPlan> travelPlans,
        List<Review> reviews,
        List<ItineraryItem> itineraryItems,
        List<Video> videos
    ) {}
    
    public record SeoulTestData(
        List<User> users,
        List<Place> places,
        List<TravelPlan> travelPlans,
        List<Review> reviews,
        List<ItineraryItem> itineraryItems,
        List<Video> videos
    ) {}
    
    public record PersonaTestData(
        List<User> personas,
        List<Place> places,
        List<TravelPlan> travelPlans,
        List<Review> reviews,
        List<Video> videos
    ) {}
    
    public record ScenarioTestData(
        List<User> users,
        List<Place> places,
        List<TravelPlan> travelPlans,
        List<Review> reviews,
        List<ItineraryItem> itineraryItems,
        TravelScenario scenario
    ) {}
    
    public record ApiTestFixtures(
        String usersJson,
        String placesJson,
        String travelPlansJson,
        String reviewsJson
    ) {}
}