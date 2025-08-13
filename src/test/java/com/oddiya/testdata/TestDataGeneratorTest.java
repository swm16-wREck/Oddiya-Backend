package com.oddiya.testdata;

import com.oddiya.entity.*;
import com.oddiya.testdata.TestDataGenerator.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class demonstrating the usage of the comprehensive test data generation framework
 * Shows how to generate realistic Korean travel data for various testing scenarios
 */
@SpringBootTest
class TestDataGeneratorTest {
    
    private final TestDataGenerator generator = new TestDataGenerator();
    
    @Test
    void testCompleteDataGeneration() {
        // Generate complete dataset
        CompleteTestData testData = generator.generateCompleteTestData(TestDataSize.SMALL);
        
        // Verify users
        assertThat(testData.users()).hasSize(5);
        assertThat(testData.users()).allSatisfy(user -> {
            assertThat(user.getEmail()).isNotNull();
            assertThat(user.getNickname()).isNotNull();
            assertThat(user.getTravelPreferences()).isNotEmpty();
        });
        
        // Verify places
        assertThat(testData.places()).hasSize(10);
        assertThat(testData.places()).allSatisfy(place -> {
            assertThat(place.getName()).isNotNull();
            assertThat(place.getCategory()).isNotNull();
            assertThat(place.getLatitude()).isBetween(33.0, 39.0); // Korea latitude range
            assertThat(place.getLongitude()).isBetween(124.0, 132.0); // Korea longitude range
        });
        
        // Verify travel plans
        assertThat(testData.travelPlans()).hasSize(5);
        assertThat(testData.travelPlans()).allSatisfy(plan -> {
            assertThat(plan.getTitle()).isNotNull();
            assertThat(plan.getDestination()).isNotNull();
            assertThat(plan.getUser()).isNotNull();
        });
        
        // Verify reviews
        assertThat(testData.reviews()).hasSize(20);
        assertThat(testData.reviews()).allSatisfy(review -> {
            assertThat(review.getContent()).isNotNull();
            assertThat(review.getRating()).isBetween(1, 5);
            assertThat(review.getUser()).isNotNull();
            assertThat(review.getPlace()).isNotNull();
        });
        
        // Verify itinerary items
        assertThat(testData.itineraryItems()).isNotEmpty();
        assertThat(testData.itineraryItems()).allSatisfy(item -> {
            assertThat(item.getTravelPlan()).isNotNull();
            assertThat(item.getPlace()).isNotNull();
            assertThat(item.getDayNumber()).isPositive();
            assertThat(item.getSequence()).isPositive();
        });
        
        // Verify videos
        assertThat(testData.videos()).hasSize(10);
        assertThat(testData.videos()).allSatisfy(video -> {
            assertThat(video.getTitle()).isNotNull();
            assertThat(video.getVideoUrl()).isNotNull();
            assertThat(video.getDurationSeconds()).isPositive();
        });
        
        System.out.println("✅ Complete test data generation successful!");
    }
    
    @Test
    void testSeoulSpecificData() {
        SeoulTestData seoulData = generator.generateSeoulTestData();
        
        // Verify Seoul-specific places
        assertThat(seoulData.places()).isNotEmpty();
        List<String> seoulPlaceNames = seoulData.places().stream()
            .map(Place::getName)
            .toList();
        assertThat(seoulPlaceNames).contains("경복궁", "남산타워", "명동", "홍대", "북촌한옥마을");
        
        // Verify Seoul travel scenarios
        assertThat(seoulData.travelPlans()).hasSize(5);
        assertThat(seoulData.travelPlans()).allSatisfy(plan -> {
            assertThat(plan.getDestination()).isEqualTo("서울");
        });
        
        // Verify Korean reviews
        assertThat(seoulData.reviews()).hasSize(50);
        assertThat(seoulData.reviews()).allSatisfy(review -> {
            assertThat(review.getContent()).matches(".*[가-힣].*"); // Contains Korean characters
        });
        
        System.out.println("✅ Seoul-specific test data generation successful!");
        System.out.println("Generated places: " + seoulPlaceNames);
    }
    
    @Test
    void testUserPersonas() {
        PersonaTestData personaData = generator.generatePersonaTestData();
        
        // Verify different user personas
        List<String> nicknames = personaData.personas().stream()
            .map(User::getNickname)
            .toList();
        
        assertThat(nicknames).contains(
            "모험가민수", "가족여행맘", "럭셔리지영", "맛집헌터", "케이팝러버"
        );
        
        // Verify persona-specific travel preferences
        User adventurer = personaData.personas().stream()
            .filter(u -> "모험가민수".equals(u.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(adventurer.getTravelPreferences().get("동행타입")).isEqualTo("솔로");
        assertThat(adventurer.getTravelPreferences().get("여행스타일")).isEqualTo("모험적");
        
        User familyTraveler = personaData.personas().stream()
            .filter(u -> "가족여행맘".equals(u.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(familyTraveler.getTravelPreferences().get("동행타입")).isEqualTo("가족");
        assertThat(familyTraveler.getTravelPreferences().get("여행스타일")).isEqualTo("여유로운");
        
        System.out.println("✅ User persona test data generation successful!");
        System.out.println("Generated personas: " + nicknames);
    }
    
    @Test
    void testFamilyVacationScenario() {
        ScenarioTestData scenarioData = generator.generateScenarioTestData(TravelScenario.FAMILY_VACATION);
        
        assertThat(scenarioData.scenario()).isEqualTo(TravelScenario.FAMILY_VACATION);
        assertThat(scenarioData.users()).hasSize(1);
        assertThat(scenarioData.travelPlans()).hasSize(1);
        
        TravelPlan familyPlan = scenarioData.travelPlans().get(0);
        assertThat(familyPlan.getNumberOfPeople()).isEqualTo(4);
        assertThat(familyPlan.getTitle()).contains("가족");
        
        // Verify family-friendly reviews (should be mostly positive)
        long positiveReviews = scenarioData.reviews().stream()
            .mapToInt(Review::getRating)
            .filter(rating -> rating >= 4)
            .count();
        assertThat(positiveReviews).isGreaterThan(scenarioData.reviews().size() / 2);
        
        System.out.println("✅ Family vacation scenario generation successful!");
    }
    
    @Test
    void testSoloAdventureScenario() {
        ScenarioTestData scenarioData = generator.generateScenarioTestData(TravelScenario.SOLO_ADVENTURE);
        
        assertThat(scenarioData.scenario()).isEqualTo(TravelScenario.SOLO_ADVENTURE);
        assertThat(scenarioData.users()).hasSize(1);
        
        User soloUser = scenarioData.users().get(0);
        assertThat(soloUser.getNickname()).contains("모험");
        
        TravelPlan soloPlan = scenarioData.travelPlans().get(0);
        assertThat(soloPlan.getNumberOfPeople()).isEqualTo(1);
        
        System.out.println("✅ Solo adventure scenario generation successful!");
    }
    
    @Test
    void testCulturalTourScenario() {
        ScenarioTestData scenarioData = generator.generateScenarioTestData(TravelScenario.CULTURAL_TOUR);
        
        assertThat(scenarioData.scenario()).isEqualTo(TravelScenario.CULTURAL_TOUR);
        
        // Verify cultural places (tourist attractions and cultural districts)
        assertThat(scenarioData.places()).allSatisfy(place -> {
            assertThat(place.getCategory()).isIn("관광지", "문화지구");
        });
        
        TravelPlan culturalPlan = scenarioData.travelPlans().get(0);
        assertThat(culturalPlan.getTitle()).containsIgnoringCase("문화");
        
        System.out.println("✅ Cultural tour scenario generation successful!");
    }
    
    @Test
    void testFoodieTourScenario() {
        ScenarioTestData scenarioData = generator.generateScenarioTestData(TravelScenario.FOODIE_TOUR);
        
        assertThat(scenarioData.scenario()).isEqualTo(TravelScenario.FOODIE_TOUR);
        
        User foodieUser = scenarioData.users().get(0);
        assertThat(foodieUser.getNickname()).contains("맛집");
        
        // Verify food-related places
        assertThat(scenarioData.places()).allSatisfy(place -> {
            assertThat(place.getCategory()).containsAnyOf("식", "카페");
        });
        
        TravelPlan foodiePlan = scenarioData.travelPlans().get(0);
        assertThat(foodiePlan.getTitle()).containsIgnoringCase("맛집");
        
        System.out.println("✅ Foodie tour scenario generation successful!");
    }
    
    @Test
    void testApiTestFixtures() {
        ApiTestFixtures fixtures = generator.generateApiTestFixtures();
        
        assertThat(fixtures.usersJson()).isNotNull();
        assertThat(fixtures.placesJson()).isNotNull();
        assertThat(fixtures.travelPlansJson()).isNotNull();
        assertThat(fixtures.reviewsJson()).isNotNull();
        
        // Verify JSON contains Korean content
        assertThat(fixtures.usersJson()).contains("모험가민수", "가족여행맘");
        assertThat(fixtures.placesJson()).contains("경복궁", "남산타워");
        assertThat(fixtures.travelPlansJson()).contains("서울", "힐링");
        assertThat(fixtures.reviewsJson()).contains("맛있고", "친절");
        
        System.out.println("✅ API test fixtures generation successful!");
    }
    
    @Test
    void testSqlSeedData() {
        String sqlData = generator.generateSqlSeedData();
        
        assertThat(sqlData).isNotNull();
        assertThat(sqlData).contains("INSERT INTO users");
        assertThat(sqlData).contains("INSERT INTO places");
        assertThat(sqlData).contains("INSERT INTO travel_plans");
        assertThat(sqlData).contains("INSERT INTO reviews");
        
        // Verify Korean data in SQL
        assertThat(sqlData).contains("모험가민수", "경복궁", "서울");
        
        System.out.println("✅ SQL seed data generation successful!");
    }
    
    @Test 
    void demonstrateReviewSentimentDistribution() {
        CompleteTestData testData = generator.generateCompleteTestData(TestDataSize.MEDIUM);
        
        // Analyze review sentiment distribution
        long positiveReviews = testData.reviews().stream()
            .mapToInt(Review::getRating)
            .filter(rating -> rating >= 4)
            .count();
            
        long neutralReviews = testData.reviews().stream()
            .mapToInt(Review::getRating)
            .filter(rating -> rating == 3)
            .count();
            
        long negativeReviews = testData.reviews().stream()
            .mapToInt(Review::getRating)
            .filter(rating -> rating <= 2)
            .count();
            
        System.out.println("📊 Review Sentiment Distribution:");
        System.out.println("Positive (4-5 stars): " + positiveReviews + " (" + (positiveReviews * 100 / testData.reviews().size()) + "%)");
        System.out.println("Neutral (3 stars): " + neutralReviews + " (" + (neutralReviews * 100 / testData.reviews().size()) + "%)");
        System.out.println("Negative (1-2 stars): " + negativeReviews + " (" + (negativeReviews * 100 / testData.reviews().size()) + "%)");
        
        // Verify realistic distribution (more positive than negative)
        assertThat(positiveReviews).isGreaterThan(negativeReviews);
    }
    
    @Test
    void demonstrateKoreanContentGeneration() {
        CompleteTestData testData = generator.generateCompleteTestData(TestDataSize.SMALL);
        
        System.out.println("🇰🇷 Korean Content Examples:");
        
        // Show Korean user nicknames
        System.out.println("\nUser Nicknames:");
        testData.users().stream()
            .map(User::getNickname)
            .limit(3)
            .forEach(nickname -> System.out.println("- " + nickname));
            
        // Show Korean place names
        System.out.println("\nPlace Names:");
        testData.places().stream()
            .map(Place::getName)
            .limit(3)
            .forEach(name -> System.out.println("- " + name));
            
        // Show Korean travel plan titles
        System.out.println("\nTravel Plan Titles:");
        testData.travelPlans().stream()
            .map(TravelPlan::getTitle)
            .limit(3)
            .forEach(title -> System.out.println("- " + title));
            
        // Show Korean review content
        System.out.println("\nReview Content:");
        testData.reviews().stream()
            .map(Review::getContent)
            .limit(2)
            .forEach(content -> System.out.println("- " + content.substring(0, Math.min(50, content.length())) + "..."));
    }
}