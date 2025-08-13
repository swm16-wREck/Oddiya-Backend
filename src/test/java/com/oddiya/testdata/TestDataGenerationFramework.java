package com.oddiya.testdata;

import com.oddiya.entity.*;
import com.oddiya.testdata.factory.*;
import com.oddiya.testdata.data.KoreanLocationData;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

/**
 * Comprehensive test data generation framework for Oddiya travel application
 * Provides realistic Korean travel data including places, users, and travel plans
 */
@Component
public class TestDataGenerationFramework {
    
    private final UserTestDataFactory userFactory = new UserTestDataFactory();
    private final PlaceTestDataFactory placeFactory = new PlaceTestDataFactory();
    private final TravelPlanTestDataFactory travelPlanFactory = new TravelPlanTestDataFactory();
    private final ReviewTestDataFactory reviewFactory = new ReviewTestDataFactory();
    private final ItineraryItemTestDataFactory itineraryFactory = new ItineraryItemTestDataFactory();
    private final VideoTestDataFactory videoFactory = new VideoTestDataFactory();
    private final KoreanLocationData locationData = new KoreanLocationData();
    private final Random random = new Random(12345); // Fixed seed for reproducible tests
    
    /**
     * Generate a complete dataset for testing
     */
    public TestDataSet generateCompleteDataSet() {
        return generateCompleteDataSet(50, 200, 100, 300);
    }
    
    /**
     * Generate a complete dataset with custom sizes
     */
    public TestDataSet generateCompleteDataSet(int userCount, int placeCount, int travelPlanCount, int reviewCount) {
        List<User> users = userFactory.createUsers(userCount);
        List<Place> places = placeFactory.createKoreanPlaces(placeCount);
        List<TravelPlan> travelPlans = travelPlanFactory.createTravelPlans(travelPlanCount, users, places);
        List<Review> reviews = reviewFactory.createReviews(reviewCount, users, places);
        
        return new TestDataSet(users, places, travelPlans, reviews);
    }
    
    /**
     * Generate small dataset for unit tests
     */
    public TestDataSet generateSmallDataSet() {
        return generateCompleteDataSet(5, 10, 5, 20);
    }
    
    /**
     * Generate large dataset for performance testing
     */
    public TestDataSet generateLargeDataSet() {
        return generateCompleteDataSet(500, 1000, 500, 2000);
    }
    
    /**
     * Generate specific Korean travel scenarios
     */
    public List<TravelPlan> generateSeoulTravelScenarios(List<User> users) {
        return travelPlanFactory.createSeoulTravelScenarios(users, locationData.getSeoulLandmarks());
    }
    
    /**
     * Generate user personas with different travel preferences
     */
    public List<User> generateUserPersonas() {
        return userFactory.createUserPersonas();
    }
    
    /**
     * Generate realistic Korean places data
     */
    public List<Place> generateKoreanPlaces() {
        return placeFactory.createKoreanPlaces(200);
    }
    
    /**
     * Generate reviews with Korean sentiments
     */
    public List<Review> generateKoreanReviews(List<User> users, List<Place> places) {
        return reviewFactory.createKoreanReviews(users, places, 300);
    }
    
    /**
     * Data container class
     */
    public static class TestDataSet {
        public final List<User> users;
        public final List<Place> places;
        public final List<TravelPlan> travelPlans;
        public final List<Review> reviews;
        
        public TestDataSet(List<User> users, List<Place> places, List<TravelPlan> travelPlans, List<Review> reviews) {
            this.users = users;
            this.places = places;
            this.travelPlans = travelPlans;
            this.reviews = reviews;
        }
    }
}