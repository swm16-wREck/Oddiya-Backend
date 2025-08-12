package com.oddiya.integration;

import com.oddiya.dto.request.CreateItineraryItemRequest;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.entity.ItineraryItem;
import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for complete travel plan creation workflow.
 * Tests creation of places, travel plans, and itinerary items in proper sequence.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TravelPlanCreationIntegrationTest extends OddiyaIntegrationTestBase {

    private String accessToken;
    private String userId;
    private String createdPlaceId;
    private String createdTravelPlanId;

    @BeforeEach
    void authenticateUser() {
        // Authenticate user first
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = response.getBody().getData();
        accessToken = authResponse.getAccessToken();
        userId = authResponse.getUser().getId();
    }

    @Test
    @Order(1)
    @DisplayName("Should create new place successfully")
    void shouldCreateNewPlaceSuccessfully() {
        // Given: New place request
        CreatePlaceRequest placeRequest = createPlaceRequest("Test Cafe Seoul", "cafe");
        
        HttpEntity<CreatePlaceRequest> request = new HttpEntity<>(placeRequest, createAuthHeaders());

        // When: Creating new place
        ResponseEntity<ApiResponse<PlaceResponse>> response = restTemplate.exchange(
            baseUrl + "/places",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<PlaceResponse>>() {}
        );

        // Then: Should create place successfully
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
        assertThat(placeResponse.getLatitude()).isEqualTo(placeRequest.getLatitude());
        assertThat(placeResponse.getLongitude()).isEqualTo(placeRequest.getLongitude());

        createdPlaceId = placeResponse.getId();

        // Verify place is saved in database
        Optional<Place> savedPlace = placeRepository.findById(Long.parseLong(createdPlaceId));
        assertThat(savedPlace).isPresent();
        assertThat(savedPlace.get().getName()).isEqualTo(placeRequest.getName());
        assertThat(savedPlace.get().getNaverPlaceId()).isEqualTo(placeRequest.getNaverPlaceId());
        assertThat(savedPlace.get().isDeleted()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("Should create travel plan successfully")
    void shouldCreateTravelPlanSuccessfully() {
        // Given: Travel plan request
        CreateTravelPlanRequest travelPlanRequest = createTravelPlanRequest(
            "Seoul Cultural Experience", 
            "Seoul"
        );
        
        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(travelPlanRequest, createAuthHeaders());

        // When: Creating travel plan
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should create travel plan successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<TravelPlanResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        TravelPlanResponse planResponse = body.getData();
        assertThat(planResponse).isNotNull();
        assertThat(planResponse.getId()).isNotBlank();
        assertThat(planResponse.getTitle()).isEqualTo(travelPlanRequest.getTitle());
        assertThat(planResponse.getDestination()).isEqualTo(travelPlanRequest.getDestination());
        assertThat(planResponse.getStartDate()).isEqualTo(travelPlanRequest.getStartDate());
        assertThat(planResponse.getEndDate()).isEqualTo(travelPlanRequest.getEndDate());
        assertThat(planResponse.getStatus()).isEqualTo(TravelPlanStatus.DRAFT.toString());
        assertThat(planResponse.isPublic()).isEqualTo(travelPlanRequest.getIsPublic());

        createdTravelPlanId = planResponse.getId();

        // Verify travel plan is saved in database
        Optional<TravelPlan> savedPlan = travelPlanRepository.findById(Long.parseLong(createdTravelPlanId));
        assertThat(savedPlan).isPresent();
        assertThat(savedPlan.get().getTitle()).isEqualTo(travelPlanRequest.getTitle());
        assertThat(savedPlan.get().getUser().getId()).isEqualTo(Long.parseLong(userId));
        assertThat(savedPlan.get().getStatus()).isEqualTo(TravelPlanStatus.DRAFT);
        assertThat(savedPlan.get().isDeleted()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("Should create travel plan with itinerary items successfully")
    void shouldCreateTravelPlanWithItineraryItemsSuccessfully() {
        // Given: Travel plan with itinerary items
        List<CreateItineraryItemRequest> itineraryItems = List.of(
            createItineraryItemRequest(testPlace1.getId().toString(), 1, 1),
            createItineraryItemRequest(testPlace2.getId().toString(), 1, 2),
            createItineraryItemRequest(testPlace1.getId().toString(), 2, 1)
        );

        CreateTravelPlanRequest travelPlanRequest = CreateTravelPlanRequest.builder()
            .title("Seoul with Itinerary")
            .description("Travel plan with detailed itinerary")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(33))
            .isPublic(true)
            .aiGenerated(false)
            .tags(List.of("seoul", "detailed", "integration-test"))
            .itineraryItems(itineraryItems)
            .build();

        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(travelPlanRequest, createAuthHeaders());

        // When: Creating travel plan with itinerary
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should create plan with itinerary successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<TravelPlanResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        TravelPlanResponse planResponse = body.getData();
        assertThat(planResponse).isNotNull();
        assertThat(planResponse.getId()).isNotBlank();

        String planId = planResponse.getId();

        // Verify travel plan and itinerary items are saved in database
        Optional<TravelPlan> savedPlan = travelPlanRepository.findById(Long.parseLong(planId));
        assertThat(savedPlan).isPresent();

        List<ItineraryItem> savedItems = itineraryItemRepository.findByTravelPlanIdOrderByDayNumberAscSequenceAsc(
            Long.parseLong(planId)
        );
        assertThat(savedItems).hasSize(3);

        // Verify itinerary item order and details
        ItineraryItem item1 = savedItems.get(0);
        assertThat(item1.getDayNumber()).isEqualTo(1);
        assertThat(item1.getSequence()).isEqualTo(1);
        assertThat(item1.getPlace().getId()).isEqualTo(testPlace1.getId());
        assertThat(item1.getStartTime()).isEqualTo("09:00");
        assertThat(item1.getEndTime()).isEqualTo("11:00");

        ItineraryItem item2 = savedItems.get(1);
        assertThat(item2.getDayNumber()).isEqualTo(1);
        assertThat(item2.getSequence()).isEqualTo(2);
        assertThat(item2.getPlace().getId()).isEqualTo(testPlace2.getId());

        ItineraryItem item3 = savedItems.get(2);
        assertThat(item3.getDayNumber()).isEqualTo(2);
        assertThat(item3.getSequence()).isEqualTo(1);
        assertThat(item3.getPlace().getId()).isEqualTo(testPlace1.getId());
    }

    @Test
    @Order(4)
    @DisplayName("Should retrieve travel plan with complete details")
    void shouldRetrieveTravelPlanWithCompleteDetails() {
        // Given: Created travel plan with itinerary
        List<CreateItineraryItemRequest> itineraryItems = List.of(
            createItineraryItemRequest(testPlace1.getId().toString(), 1, 1)
        );

        CreateTravelPlanRequest travelPlanRequest = CreateTravelPlanRequest.builder()
            .title("Detailed Seoul Plan")
            .description("Complete plan for Seoul exploration")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(25))
            .endDate(LocalDate.now().plusDays(28))
            .isPublic(true)
            .aiGenerated(false)
            .tags(List.of("seoul", "complete", "test"))
            .itineraryItems(itineraryItems)
            .build();

        HttpEntity<CreateTravelPlanRequest> createRequest = new HttpEntity<>(travelPlanRequest, createAuthHeaders());

        ResponseEntity<ApiResponse<TravelPlanResponse>> createResponse = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            createRequest,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        String planId = createResponse.getBody().getData().getId();

        // When: Retrieving travel plan details
        HttpEntity<Void> getRequest = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<ApiResponse<TravelPlanResponse>> getResponse = restTemplate.exchange(
            baseUrl + "/travel-plans/" + planId,
            HttpMethod.GET,
            getRequest,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should return complete travel plan details
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<TravelPlanResponse> body = getResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        TravelPlanResponse planResponse = body.getData();
        assertThat(planResponse).isNotNull();
        assertThat(planResponse.getId()).isEqualTo(planId);
        assertThat(planResponse.getTitle()).isEqualTo(travelPlanRequest.getTitle());
        assertThat(planResponse.getItineraryItems()).isNotEmpty();
        assertThat(planResponse.getItineraryItems()).hasSize(1);
        
        // Verify itinerary item details
        var itineraryItem = planResponse.getItineraryItems().get(0);
        assertThat(itineraryItem.getPlace()).isNotNull();
        assertThat(itineraryItem.getPlace().getName()).isEqualTo(testPlace1.getName());
        assertThat(itineraryItem.getDayNumber()).isEqualTo(1);
        assertThat(itineraryItem.getSequence()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should handle travel plan creation validation errors")
    void shouldHandleTravelPlanCreationValidationErrors() {
        // Given: Invalid travel plan request (end date before start date)
        CreateTravelPlanRequest invalidRequest = CreateTravelPlanRequest.builder()
            .title("Invalid Plan")
            .description("This plan has invalid dates")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(25)) // End before start
            .isPublic(true)
            .aiGenerated(false)
            .build();

        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(invalidRequest, createAuthHeaders());

        // When: Creating invalid travel plan
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should return validation error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    @DisplayName("Should handle travel plan creation with non-existent place")
    void shouldHandleTravelPlanCreationWithNonExistentPlace() {
        // Given: Travel plan with non-existent place in itinerary
        List<CreateItineraryItemRequest> itineraryItems = List.of(
            createItineraryItemRequest("999999", 1, 1) // Non-existent place ID
        );

        CreateTravelPlanRequest travelPlanRequest = CreateTravelPlanRequest.builder()
            .title("Plan with Invalid Place")
            .description("This plan references non-existent place")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(33))
            .isPublic(true)
            .aiGenerated(false)
            .itineraryItems(itineraryItems)
            .build();

        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(travelPlanRequest, createAuthHeaders());

        // When: Creating travel plan with invalid place reference
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should handle error appropriately
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(7)
    @DisplayName("Should maintain transaction consistency during travel plan creation")
    void shouldMaintainTransactionConsistencyDuringTravelPlanCreation() {
        // Given: Travel plan request that might fail during itinerary creation
        List<CreateItineraryItemRequest> itineraryItems = List.of(
            createItineraryItemRequest(testPlace1.getId().toString(), 1, 1),
            createItineraryItemRequest("999999", 1, 2) // This should cause failure
        );

        CreateTravelPlanRequest travelPlanRequest = CreateTravelPlanRequest.builder()
            .title("Transaction Test Plan")
            .description("This plan tests transaction rollback")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(33))
            .isPublic(true)
            .aiGenerated(false)
            .itineraryItems(itineraryItems)
            .build();

        HttpEntity<CreateTravelPlanRequest> request = new HttpEntity<>(travelPlanRequest, createAuthHeaders());

        // Count plans before request
        long planCountBefore = travelPlanRepository.count();

        // When: Creating travel plan that should fail
        ResponseEntity<ApiResponse<TravelPlanResponse>> response = restTemplate.exchange(
            baseUrl + "/travel-plans",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<TravelPlanResponse>>() {}
        );

        // Then: Should not create partial data (transaction should be rolled back)
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
        
        long planCountAfter = travelPlanRepository.count();
        assertThat(planCountAfter).isEqualTo(planCountBefore);

        // Verify no orphaned itinerary items were created
        List<ItineraryItem> orphanedItems = itineraryItemRepository.findAll().stream()
            .filter(item -> item.getTravelPlan() == null)
            .toList();
        assertThat(orphanedItems).isEmpty();
    }

    @Override
    protected HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }
}