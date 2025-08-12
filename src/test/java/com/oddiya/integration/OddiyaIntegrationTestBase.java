package com.oddiya.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.OddiyaApplication;
import com.oddiya.entity.*;
import com.oddiya.dto.request.*;
import com.oddiya.dto.response.*;
import com.oddiya.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for comprehensive integration tests.
 * Uses @SpringBootTest with real beans and H2 database.
 * Tests complete workflows end-to-end without mocking internal services.
 */
@SpringBootTest(
    classes = OddiyaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
public abstract class OddiyaIntegrationTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    // Repositories for data verification
    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PlaceRepository placeRepository;

    @Autowired
    protected TravelPlanRepository travelPlanRepository;

    @Autowired
    protected ItineraryItemRepository itineraryItemRepository;

    // Test data
    protected User testUser;
    protected Place testPlace1;
    protected Place testPlace2;
    protected TravelPlan testTravelPlan;
    
    // Authentication token for API calls
    protected String authToken;
    protected String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        setupTestData();
    }

    /**
     * Set up common test data for integration tests
     */
    protected void setupTestData() {
        // Create test user
        testUser = User.builder()
            .email("integration-test@oddiya.com")
            .username("integrationtest")
            .nickname("Integration Test User")
            .provider("google")
            .providerId("google-integration-test-123")
            .bio("Test user for integration testing")
            .isActive(true)
            .isEmailVerified(true)
            .isDeleted(false)
            .preferences(Map.of(
                "language", "ko",
                "theme", "light",
                "notifications", "enabled"
            ))
            .travelPreferences(Map.of(
                "budget", "medium",
                "style", "adventure",
                "accommodation", "hotel"
            ))
            .build();
        testUser = userRepository.save(testUser);

        // Create test places
        testPlace1 = Place.builder()
            .naverPlaceId("naver-integration-place-1")
            .name("Seoul Tower Integration Test")
            .category("tourist_attraction")
            .description("Famous landmark in Seoul for integration testing")
            .address("105 Namsangongwon-gil, Yongsan-gu, Seoul")
            .roadAddress("105 Namsangongwon-gil, Yongsan-gu, Seoul")
            .latitude(37.5512)
            .longitude(126.9882)
            .phoneNumber("02-3455-9277")
            .website("http://www.seoultower.co.kr")
            .rating(4.5)
            .reviewCount(100)
            .bookmarkCount(50)
            .isVerified(true)
            .popularityScore(95.0)
            .isDeleted(false)
            .tags(List.of("landmark", "view", "romantic", "seoul"))
            .openingHours(Map.of(
                "Monday", "09:00-22:00",
                "Tuesday", "09:00-22:00",
                "Wednesday", "09:00-22:00",
                "Thursday", "09:00-22:00",
                "Friday", "09:00-23:00",
                "Saturday", "09:00-23:00",
                "Sunday", "09:00-22:00"
            ))
            .images(List.of(
                "https://example.com/seoul-tower-1.jpg",
                "https://example.com/seoul-tower-2.jpg"
            ))
            .build();
        testPlace1 = placeRepository.save(testPlace1);

        testPlace2 = Place.builder()
            .naverPlaceId("naver-integration-place-2")
            .name("Busan Beach Integration Test")
            .category("beach")
            .description("Beautiful beach in Busan for integration testing")
            .address("Haeundae-gu, Busan")
            .roadAddress("Haeundae Beach-ro, Haeundae-gu, Busan")
            .latitude(35.1595)
            .longitude(129.1604)
            .rating(4.2)
            .reviewCount(80)
            .bookmarkCount(30)
            .isVerified(true)
            .popularityScore(85.0)
            .isDeleted(false)
            .tags(List.of("beach", "swimming", "summer", "busan"))
            .openingHours(Map.of(
                "Monday", "24시간",
                "Tuesday", "24시간",
                "Wednesday", "24시간",
                "Thursday", "24시간",
                "Friday", "24시간",
                "Saturday", "24시간",
                "Sunday", "24시간"
            ))
            .build();
        testPlace2 = placeRepository.save(testPlace2);

        // Create test travel plan
        testTravelPlan = TravelPlan.builder()
            .user(testUser)
            .title("Seoul Adventure Integration Test")
            .description("Exploring Seoul landmarks for integration testing")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(10))
            .endDate(LocalDate.now().plusDays(15))
            .numberOfPeople(2)
            .budget(new BigDecimal("1500000"))
            .status(TravelPlanStatus.DRAFT)
            .isPublic(true)
            .isAiGenerated(false)
            .isDeleted(false)
            .viewCount(0L)
            .likeCount(0L)
            .shareCount(0L)
            .saveCount(0L)
            .preferences(Map.of(
                "transportation", "public",
                "accommodation", "hotel",
                "activity_level", "moderate"
            ))
            .tags(List.of("seoul", "adventure", "culture", "integration-test"))
            .coverImageUrl("https://example.com/seoul-plan-cover.jpg")
            .build();
        testTravelPlan = travelPlanRepository.save(testTravelPlan);
    }

    /**
     * Create HTTP headers with authentication token
     */
    protected HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authToken != null && !authToken.isEmpty()) {
            headers.setBearerAuth(authToken);
        }
        return headers;
    }

    /**
     * Create HTTP headers without authentication (for login/signup)
     */
    protected HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Helper method to create a mock OAuth login request
     */
    protected LoginRequest createMockLoginRequest() {
        return LoginRequest.builder()
            .provider("google")
            .idToken("mock-google-id-token-for-integration-testing")
            .deviceId("test-device-123")
            .deviceType("web")
            .build();
    }

    /**
     * Helper method to create a travel plan request
     */
    protected CreateTravelPlanRequest createTravelPlanRequest(String title, String destination) {
        return CreateTravelPlanRequest.builder()
            .title(title)
            .description("Integration test travel plan: " + title)
            .destination(destination)
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(35))
            .isPublic(true)
            .aiGenerated(false)
            .tags(List.of("integration-test", destination.toLowerCase()))
            .build();
    }

    /**
     * Helper method to create an itinerary item request
     */
    protected CreateItineraryItemRequest createItineraryItemRequest(String placeId, int dayNumber, int sequence) {
        return CreateItineraryItemRequest.builder()
            .placeId(placeId)
            .dayNumber(dayNumber)
            .sequence(sequence)
            .startTime("09:00")
            .endTime("11:00")
            .notes("Integration test itinerary item")
            .build();
    }

    /**
     * Helper method to create a review request
     */
    protected CreateReviewRequest createReviewRequest(String placeId, int rating, String content) {
        return CreateReviewRequest.builder()
            .placeId(placeId)
            .rating(rating)
            .content(content)
            .images(List.of("https://example.com/review-image-1.jpg"))
            .build();
    }

    /**
     * Helper method to create a place request
     */
    protected CreatePlaceRequest createPlaceRequest(String name, String category) {
        return CreatePlaceRequest.builder()
            .naverPlaceId("naver-" + name.toLowerCase().replace(" ", "-"))
            .name(name)
            .category(category)
            .description("Integration test place: " + name)
            .address("Test Address for " + name)
            .roadAddress("Test Road Address for " + name)
            .latitude(37.5665 + Math.random() * 0.1)
            .longitude(126.9780 + Math.random() * 0.1)
            .phoneNumber("02-1234-5678")
            .website("https://example.com/" + name.toLowerCase().replace(" ", "-"))
            .tags(List.of("integration-test", category))
            .build();
    }

    /**
     * Wait for async operations to complete
     */
    protected void waitForAsyncOperations() throws InterruptedException {
        Thread.sleep(100); // Small delay for async operations
    }

    /**
     * Clear all test data from repositories
     */
    protected void clearTestData() {
        itineraryItemRepository.deleteAll();
        travelPlanRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();
    }
}