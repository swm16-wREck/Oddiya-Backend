package com.oddiya.integration;

import com.oddiya.dto.request.*;
import com.oddiya.dto.response.*;
import com.oddiya.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive end-to-end integration test covering the complete user journey:
 * 1. User registration/authentication
 * 2. Place creation and discovery
 * 3. Travel plan creation with itinerary
 * 4. Review submission and rating updates
 * 5. Data consistency and transaction integrity across all services
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EndToEndWorkflowIntegrationTest extends OddiyaIntegrationTestBase {

    // Test data that persists across test methods
    private static String userAccessToken;
    private static String userId;
    private static String createdPlaceId;
    private static String createdTravelPlanId;
    private static String createdReviewId;

    @Test
    @Order(1)
    @DisplayName("Step 1: Complete user authentication workflow")
    void step1_CompleteUserAuthenticationWorkflow() {
        // Given: New user attempting to login via OAuth
        LoginRequest loginRequest = LoginRequest.builder()
            .provider("google")
            .providerId("google-e2e-test-user-123")
            .email("e2e-test-user@oddiya.com")
            .nickname("E2E Test User")
            .build();

        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        // When: User performs OAuth login
        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Authentication should succeed and create user account
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<AuthResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        AuthResponse authResponse = body.getData();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();
        assertThat(authResponse.getUser()).isNotNull();
        assertThat(authResponse.getUser().getEmail()).isEqualTo(loginRequest.getEmail());

        // Store for subsequent tests
        userAccessToken = authResponse.getAccessToken();
        userId = authResponse.getUser().getId();

        // Verify user is properly stored in database
        Optional<User> savedUser = userRepository.findById(Long.parseLong(userId));
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo(loginRequest.getEmail());
        assertThat(savedUser.get().getProvider()).isEqualTo("google");
        assertThat(savedUser.get().isActive()).isTrue();
        assertThat(savedUser.get().getRefreshToken()).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Create new travel destination place")
    void step2_CreateNewTravelDestinationPlace() {
        // Given: Authenticated user wants to add a new place
        CreatePlaceRequest placeRequest = CreatePlaceRequest.builder()
            .naverPlaceId("naver-e2e-cafe-seoul-123")
            .name("E2E Test Cafe Seoul")
            .category("cafe")
            .description("A cozy cafe in Seoul perfect for meeting friends and working. " +
                       "Known for excellent coffee and comfortable seating.")
            .address("123 Gangnam-gu, Seoul, South Korea")
            .roadAddress("123 Teheran-ro, Gangnam-gu, Seoul")
            .latitude(37.5665)
            .longitude(126.9780)
            .phoneNumber("02-1234-5678")
            .website("https://e2e-test-cafe.com")
            .tags(List.of("cafe", "wifi", "study", "seoul", "gangnam"))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        HttpEntity<CreatePlaceRequest> request = new HttpEntity<>(placeRequest, headers);

        // When: Creating the place
        ResponseEntity<ApiResponse<PlaceResponse>> response = restTemplate.exchange(
            baseUrl + "/places",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<PlaceResponse>>() {}
        );

        // Then: Place should be created successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<PlaceResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        PlaceResponse placeResponse = body.getData();
        assertThat(placeResponse).isNotNull();
        assertThat(placeResponse.getId()).isNotBlank();
        assertThat(placeResponse.getName()).isEqualTo(placeRequest.getName());
        assertThat(placeResponse.getCategory()).isEqualTo(placeRequest.getCategory());
        assertThat(placeResponse.getNaverPlaceId()).isEqualTo(placeRequest.getNaverPlaceId());
        assertThat(placeResponse.getRating()).isNotNull();
        assertThat(placeResponse.getReviewCount()).isEqualTo(0);

        createdPlaceId = placeResponse.getId();

        // Verify place is stored in database with proper initial values
        Optional<Place> savedPlace = placeRepository.findById(Long.parseLong(createdPlaceId));
        assertThat(savedPlace).isPresent();
        assertThat(savedPlace.get().getName()).isEqualTo(placeRequest.getName());
        assertThat(savedPlace.get().getReviewCount()).isEqualTo(0);
        assertThat(savedPlace.get().getRating()).isNotNull();
        assertThat(savedPlace.get().isDeleted()).isFalse();
        assertThat(savedPlace.get().getTags()).containsExactlyInAnyOrderElementsOf(placeRequest.getTags());
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Create comprehensive travel plan with itinerary")
    void step3_CreateComprehensiveTravelPlanWithItinerary() {
        // Given: User wants to create a detailed travel plan including the new place
        List<CreateItineraryItemRequest> itineraryItems = List.of(
            CreateItineraryItemRequest.builder()
                .placeId(testPlace1.getId().toString()) // Use pre-existing test place
                .dayNumber(1)
                .sequence(1)
                .startTime("09:00")
                .endTime("11:30")
                .notes("Start the day at Seoul Tower for panoramic city views")
                .build(),
            CreateItineraryItemRequest.builder()
                .placeId(createdPlaceId) // Use newly created cafe
                .dayNumber(1)
                .sequence(2)
                .startTime("12:00")
                .endTime("14:00")
                .notes("Lunch and coffee break at cozy cafe")
                .build(),
            CreateItineraryItemRequest.builder()
                .placeId(testPlace2.getId().toString()) // Use pre-existing test place
                .dayNumber(2)
                .sequence(1)
                .startTime("10:00")
                .endTime("16:00")
                .notes("Relax at the beach and enjoy water activities")
                .build()
        );

        CreateTravelPlanRequest travelPlanRequest = CreateTravelPlanRequest.builder()
            .title("E2E Seoul & Busan Adventure")
            .description("A comprehensive 2-day journey exploring Seoul's landmarks and Busan's beautiful beaches. " +
                       "Perfect blend of city culture and coastal relaxation.")
            .destination("Seoul & Busan")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(31))
            .isPublic(true)
            .aiGenerated(false)
            .tags(List.of("seoul", "busan", "adventure", "culture", "beach", "e2e-test"))
            .itineraryItems(itineraryItems)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(travelPlanRequest, headers);

        // When: Creating the travel plan
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Travel plan should be created with all itinerary items
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<TravelPlanResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        TravelPlanResponse planResponse = body.getData();
        assertThat(planResponse).isNotNull();
        assertThat(planResponse.getId()).isNotBlank();
        assertThat(planResponse.getTitle()).isEqualTo(travelPlanRequest.getTitle());
        assertThat(planResponse.getDestination()).isEqualTo(travelPlanRequest.getDestination());
        assertThat(planResponse.getStatus()).isEqualTo(TravelPlanStatus.DRAFT.toString());
        assertThat(planResponse.getItineraryItems()).hasSize(3);

        createdTravelPlanId = planResponse.getId();

        // Verify travel plan is stored with proper relationships
        Optional<TravelPlan> savedPlan = travelPlanRepository.findById(Long.parseLong(createdTravelPlanId));
        assertThat(savedPlan).isPresent();
        assertThat(savedPlan.get().getUser().getId()).isEqualTo(Long.parseLong(userId));
        assertThat(savedPlan.get().getTitle()).isEqualTo(travelPlanRequest.getTitle());
        assertThat(savedPlan.get().isDeleted()).isFalse();

        // Verify all itinerary items are created and properly ordered
        List<ItineraryItem> savedItems = itineraryItemRepository
            .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(Long.parseLong(createdTravelPlanId));
        assertThat(savedItems).hasSize(3);
        
        // Verify first item (Day 1, Sequence 1 - Seoul Tower)
        ItineraryItem item1 = savedItems.get(0);
        assertThat(item1.getDayNumber()).isEqualTo(1);
        assertThat(item1.getSequence()).isEqualTo(1);
        assertThat(item1.getPlace().getId()).isEqualTo(testPlace1.getId());
        assertThat(item1.getStartTime()).isEqualTo("09:00");
        assertThat(item1.getEndTime()).isEqualTo("11:30");

        // Verify second item (Day 1, Sequence 2 - New Cafe)
        ItineraryItem item2 = savedItems.get(1);
        assertThat(item2.getDayNumber()).isEqualTo(1);
        assertThat(item2.getSequence()).isEqualTo(2);
        assertThat(item2.getPlace().getId()).isEqualTo(Long.parseLong(createdPlaceId));

        // Verify third item (Day 2, Sequence 1 - Beach)
        ItineraryItem item3 = savedItems.get(2);
        assertThat(item3.getDayNumber()).isEqualTo(2);
        assertThat(item3.getSequence()).isEqualTo(1);
        assertThat(item3.getPlace().getId()).isEqualTo(testPlace2.getId());
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Submit detailed reviews and verify rating calculations")
    void step4_SubmitDetailedReviewsAndVerifyRatingCalculations() throws InterruptedException {
        // Given: User has visited places from the travel plan and wants to leave reviews
        Place cafePlace = placeRepository.findById(Long.parseLong(createdPlaceId)).orElse(null);
        assertThat(cafePlace).isNotNull();
        
        Double initialRating = cafePlace.getRating();
        Integer initialReviewCount = cafePlace.getReviewCount();

        CreateReviewRequest reviewRequest = CreateReviewRequest.builder()
            .placeId(createdPlaceId)
            .rating(5)
            .content("Absolutely fantastic cafe! The atmosphere was perfect for both work and relaxation. " +
                   "The coffee quality exceeded my expectations - rich, smooth, and perfectly brewed. " +
                   "The staff was incredibly friendly and attentive. The WiFi was fast and reliable, " +
                   "making it an ideal spot for digital nomads. The interior design is modern yet cozy, " +
                   "with comfortable seating options. I especially loved the natural lighting from the " +
                   "large windows. Will definitely visit again when I'm in Seoul!")
            .images(List.of(
                "https://example.com/cafe-interior.jpg",
                "https://example.com/coffee-latte-art.jpg"
            ))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        HttpEntity<CreateReviewRequest> request = new HttpEntity<>(reviewRequest, headers);

        // When: Submitting the review
        ResponseEntity<ApiResponse<ReviewResponse>> response = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Then: Review should be created and place rating updated
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<ReviewResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        ReviewResponse reviewResponse = body.getData();
        assertThat(reviewResponse).isNotNull();
        assertThat(reviewResponse.getId()).isNotBlank();
        assertThat(reviewResponse.getRating()).isEqualTo(5);
        assertThat(reviewResponse.getContent()).isEqualTo(reviewRequest.getContent());
        assertThat(reviewResponse.getUser().getId()).isEqualTo(userId);
        assertThat(reviewResponse.getPlace().getId()).isEqualTo(createdPlaceId);
        assertThat(reviewResponse.getImages()).containsExactlyInAnyOrderElementsOf(reviewRequest.getImages());

        createdReviewId = reviewResponse.getId();

        // Wait for any async rating calculations
        waitForAsyncOperations();

        // Verify review is stored in database
        Optional<User> userWithReviews = userRepository.findById(Long.parseLong(userId));
        assertThat(userWithReviews).isPresent();
        
        boolean reviewExists = userWithReviews.get().getReviews().stream()
            .anyMatch(r -> r.getPlace().getId().equals(Long.parseLong(createdPlaceId)) && 
                          r.getRating().equals(5));
        assertThat(reviewExists).isTrue();

        // Verify place rating is recalculated correctly
        Place updatedPlace = placeRepository.findById(Long.parseLong(createdPlaceId)).orElse(null);
        assertThat(updatedPlace).isNotNull();
        assertThat(updatedPlace.getReviewCount()).isEqualTo(initialReviewCount + 1);
        
        Double expectedRating = (initialRating * initialReviewCount + 5.0) / (initialReviewCount + 1);
        assertThat(updatedPlace.getRating()).isCloseTo(expectedRating, within(0.01));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Verify complete data integrity and relationships")
    void step5_VerifyCompleteDataIntegrityAndRelationships() {
        // Verify User data integrity
        Optional<User> savedUser = userRepository.findById(Long.parseLong(userId));
        assertThat(savedUser).isPresent();
        
        User user = savedUser.get();
        assertThat(user.getEmail()).isEqualTo("e2e-test-user@oddiya.com");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getTravelPlans()).hasSize(1);
        assertThat(user.getReviews()).hasSize(1);

        // Verify Place data integrity
        Optional<Place> savedPlace = placeRepository.findById(Long.parseLong(createdPlaceId));
        assertThat(savedPlace).isPresent();
        
        Place place = savedPlace.get();
        assertThat(place.getName()).isEqualTo("E2E Test Cafe Seoul");
        assertThat(place.getReviewCount()).isEqualTo(1);
        assertThat(place.getRating()).isGreaterThan(0.0);
        assertThat(place.getReviews()).hasSize(1);

        // Verify TravelPlan data integrity and relationships
        Optional<TravelPlan> savedPlan = travelPlanRepository.findById(Long.parseLong(createdTravelPlanId));
        assertThat(savedPlan).isPresent();
        
        TravelPlan plan = savedPlan.get();
        assertThat(plan.getTitle()).isEqualTo("E2E Seoul & Busan Adventure");
        assertThat(plan.getUser().getId()).isEqualTo(Long.parseLong(userId));
        assertThat(plan.getItineraryItems()).hasSize(3);
        assertThat(plan.getStatus()).isEqualTo(TravelPlanStatus.DRAFT);

        // Verify ItineraryItem relationships
        List<ItineraryItem> itineraryItems = plan.getItineraryItems();
        
        // Check that itinerary items reference correct places
        boolean containsSeoulTower = itineraryItems.stream()
            .anyMatch(item -> item.getPlace().getId().equals(testPlace1.getId()));
        assertThat(containsSeoulTower).isTrue();

        boolean containsNewCafe = itineraryItems.stream()
            .anyMatch(item -> item.getPlace().getId().equals(Long.parseLong(createdPlaceId)));
        assertThat(containsNewCafe).isTrue();

        boolean containsBeach = itineraryItems.stream()
            .anyMatch(item -> item.getPlace().getId().equals(testPlace2.getId()));
        assertThat(containsBeach).isTrue();

        // Verify Review relationships
        List<Review> userReviews = user.getReviews();
        assertThat(userReviews).hasSize(1);
        
        Review review = userReviews.get(0);
        assertThat(review.getPlace().getId()).isEqualTo(Long.parseLong(createdPlaceId));
        assertThat(review.getUser().getId()).isEqualTo(Long.parseLong(userId));
        assertThat(review.getRating()).isEqualTo(5);

        // Verify bidirectional relationships
        assertThat(place.getReviews().get(0).getUser().getId()).isEqualTo(Long.parseLong(userId));
        
        // Verify no orphaned data exists
        assertThat(plan.getUser()).isNotNull();
        itineraryItems.forEach(item -> {
            assertThat(item.getTravelPlan()).isNotNull();
            assertThat(item.getPlace()).isNotNull();
        });
        
        assertThat(review.getUser()).isNotNull();
        assertThat(review.getPlace()).isNotNull();
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Test complete workflow with API retrieval operations")
    void step6_TestCompleteWorkflowWithAPIRetrievalOperations() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);

        // Test 1: Retrieve user's travel plans
        HttpEntity<Void> getUserPlansRequest = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<List<TravelPlanResponse>>> userPlansResponse = restTemplate.exchange(
            baseUrl + "/travel-plans?userId=" + userId + "&page=0&size=10",
            HttpMethod.GET,
            getUserPlansRequest,
            new ParameterizedTypeReference<ApiResponse<List<TravelPlanResponse>>>() {}
        );

        assertThat(userPlansResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TravelPlanResponse> userPlans = userPlansResponse.getBody().getData();
        assertThat(userPlans).hasSize(1);
        assertThat(userPlans.get(0).getId()).isEqualTo(createdTravelPlanId);

        // Test 2: Retrieve specific travel plan with full details
        HttpEntity<Void> getPlanDetailsRequest = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<TravelPlanResponse>> planDetailsResponse = restTemplate.exchange(
            baseUrl + "/travel-plans/" + createdTravelPlanId,
            HttpMethod.GET,
            getPlanDetailsRequest,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        assertThat(planDetailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TravelPlanResponse planDetails = planDetailsResponse.getBody().getData();
        assertThat(planDetails.getItineraryItems()).hasSize(3);
        assertThat(planDetails.getItineraryItems()).allSatisfy(item -> {
            assertThat(item.getPlace()).isNotNull();
            assertThat(item.getPlace().getName()).isNotBlank();
        });

        // Test 3: Retrieve place with reviews
        HttpEntity<Void> getPlaceRequest = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<PlaceResponse>> placeResponse = restTemplate.exchange(
            baseUrl + "/places/" + createdPlaceId,
            HttpMethod.GET,
            getPlaceRequest,
            new ParameterizedTypeReference<ApiResponse<PlaceResponse>>() {}
        );

        assertThat(placeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceResponse placeDetails = placeResponse.getBody().getData();
        assertThat(placeDetails.getReviewCount()).isEqualTo(1);
        assertThat(placeDetails.getRating()).isGreaterThan(0.0);

        // Test 4: Retrieve reviews for the place
        HttpEntity<Void> getReviewsRequest = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<List<ReviewResponse>>> reviewsResponse = restTemplate.exchange(
            baseUrl + "/places/" + createdPlaceId + "/reviews?page=0&size=10",
            HttpMethod.GET,
            getReviewsRequest,
            new ParameterizedTypeReference<ApiResponse<List<ReviewResponse>>>() {}
        );

        assertThat(reviewsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ReviewResponse> placeReviews = reviewsResponse.getBody().getData();
        assertThat(placeReviews).hasSize(1);
        assertThat(placeReviews.get(0).getId()).isEqualTo(createdReviewId);
        assertThat(placeReviews.get(0).getUser().getId()).isEqualTo(userId);
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Test token refresh and session continuity")
    void step7_TestTokenRefreshAndSessionContinuity() throws InterruptedException {
        // Given: Current refresh token
        Optional<User> currentUser = userRepository.findById(Long.parseLong(userId));
        assertThat(currentUser).isPresent();
        String currentRefreshToken = currentUser.get().getRefreshToken();

        // Small delay to ensure different token generation
        Thread.sleep(100);

        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken(currentRefreshToken)
            .build();

        HttpEntity<RefreshTokenRequest> request = new HttpEntity<>(refreshRequest, createHeaders());

        // When: Refreshing the token
        ResponseEntity<ApiResponse<AuthResponse>> refreshResponse = restTemplate.exchange(
            baseUrl + "/auth/refresh",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Should get new tokens
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = refreshResponse.getBody().getData();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();

        // Verify new token works for API calls
        String newAccessToken = authResponse.getAccessToken();
        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.setContentType(MediaType.APPLICATION_JSON);
        newHeaders.setBearerAuth(newAccessToken);

        HttpEntity<Void> testRequest = new HttpEntity<>(newHeaders);
        ResponseEntity<ApiResponse<TravelPlanResponse>> testResponse = restTemplate.exchange(
            baseUrl + "/travel-plans/" + createdTravelPlanId,
            HttpMethod.GET,
            testRequest,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Verify complete system state and cleanup validation")
    void step8_VerifyCompleteSystemStateAndCleanupValidation() {
        // Verify final counts and relationships
        long totalUsers = userRepository.count();
        long totalPlaces = placeRepository.count();
        long totalTravelPlans = travelPlanRepository.count();
        long totalItineraryItems = itineraryItemRepository.count();

        // Should have test data plus our created entities
        assertThat(totalUsers).isGreaterThanOrEqualTo(4); // 3 from base setup + 1 created
        assertThat(totalPlaces).isGreaterThanOrEqualTo(4); // 3 from base setup + 1 created
        assertThat(totalTravelPlans).isGreaterThanOrEqualTo(2); // 1 from base setup + 1 created
        assertThat(totalItineraryItems).isEqualTo(3); // 3 items created in our travel plan

        // Verify no data corruption occurred during the workflow
        Optional<User> finalUser = userRepository.findById(Long.parseLong(userId));
        assertThat(finalUser).isPresent();
        assertThat(finalUser.get().isDeleted()).isFalse();
        assertThat(finalUser.get().isActive()).isTrue();

        Optional<Place> finalPlace = placeRepository.findById(Long.parseLong(createdPlaceId));
        assertThat(finalPlace).isPresent();
        assertThat(finalPlace.get().isDeleted()).isFalse();
        assertThat(finalPlace.get().getReviewCount()).isEqualTo(1);

        Optional<TravelPlan> finalPlan = travelPlanRepository.findById(Long.parseLong(createdTravelPlanId));
        assertThat(finalPlan).isPresent();
        assertThat(finalPlan.get().isDeleted()).isFalse();
        assertThat(finalPlan.get().getItineraryItems()).hasSize(3);

        // Verify all foreign key relationships are intact
        List<ItineraryItem> allItems = itineraryItemRepository.findAll();
        assertThat(allItems).allSatisfy(item -> {
            assertThat(item.getTravelPlan()).isNotNull();
            assertThat(item.getPlace()).isNotNull();
        });

        // Log final state for debugging
        System.out.println("=== End-to-End Workflow Completed Successfully ===");
        System.out.println("Created User ID: " + userId);
        System.out.println("Created Place ID: " + createdPlaceId);
        System.out.println("Created Travel Plan ID: " + createdTravelPlanId);
        System.out.println("Created Review ID: " + createdReviewId);
        System.out.println("Total Users: " + totalUsers);
        System.out.println("Total Places: " + totalPlaces);
        System.out.println("Total Travel Plans: " + totalTravelPlans);
        System.out.println("Total Itinerary Items: " + totalItineraryItems);
        System.out.println("=============================================");
    }
}