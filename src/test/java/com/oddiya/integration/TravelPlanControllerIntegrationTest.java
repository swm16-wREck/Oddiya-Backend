package com.oddiya.integration;

import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import com.oddiya.entity.User;
import com.oddiya.repository.TravelPlanRepository;
import com.oddiya.repository.UserRepository;
import com.oddiya.testdata.ComprehensiveTestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Travel Plan Controller Integration Test
 * 
 * Tests the complete API layer as per PRD specifications:
 * - RESTful API endpoints for travel plan management
 * - Authentication and authorization
 * - Request/response validation
 * - Performance requirements (<200ms response times)
 * - Spatial query integration with PostGIS
 * - Error handling and status codes
 * - Pagination and sorting
 * - Korean language support
 */
@DisplayName("Travel Plan Controller Integration Tests")
class TravelPlanControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private UserRepository userRepository;

    private ComprehensiveTestDataFactory testDataFactory;
    private User testUser;
    private TravelPlan testTravelPlan;

    @BeforeEach
    void setUpIntegrationTest() {
        testDataFactory = new ComprehensiveTestDataFactory();
        
        // Create test user and save to database
        testUser = testDataFactory.createTestUser(TEST_USER_ID);
        userRepository.save(testUser);
        
        // Create test travel plan
        testTravelPlan = testDataFactory.createTestTravelPlan(TEST_TRAVEL_PLAN_ID);
        testTravelPlan.setUser(testUser);
        travelPlanRepository.save(testTravelPlan);
    }

    // ============================================================
    // API ENDPOINT TESTS
    // ============================================================

    @Nested
    @DisplayName("Travel Plan CRUD Operations")
    class TravelPlanCRUDTests {

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should create travel plan via API within performance limits")
        void shouldCreateTravelPlanViaAPIWithinPerformanceLimits() throws Exception {
            // Given - Realistic travel plan creation request
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                    .title("Seoul Cultural Journey")
                    .description("Experience traditional and modern Seoul")
                    .destination("Seoul")
                    .startDate(LocalDate.now().plusDays(14))
                    .endDate(LocalDate.now().plusDays(18))
                    .isPublic(true)
                    .aiGenerated(false)
                    .tags(Arrays.asList("culture", "food", "history"))
                    .build();

            // When & Then - Measure API response time
            long startTime = System.currentTimeMillis();
            
            MvcResult result = mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("Seoul Cultural Journey"))
                    .andExpect(jsonPath("$.data.destination").value("Seoul"))
                    .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID))
                    .andReturn();

            long responseTime = System.currentTimeMillis() - startTime;
            
            // Verify performance requirement: <200ms API response time
            assertThat(responseTime)
                    .as("API response time should be under 200ms")
                    .isLessThan(MAX_API_RESPONSE_TIME_MS);

            // Verify database persistence
            String responseContent = result.getResponse().getContentAsString();
            String planId = objectMapper.readTree(responseContent)
                    .path("data").path("id").asText();
            
            assertThat(travelPlanRepository.findById(planId))
                    .isPresent()
                    .get()
                    .satisfies(plan -> {
                        assertThat(plan.getTitle()).isEqualTo("Seoul Cultural Journey");
                        assertThat(plan.getUser().getId()).isEqualTo(TEST_USER_ID);
                    });
        }

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER") 
        @DisplayName("Should create travel plan with Korean title and destination")
        void shouldCreateTravelPlanWithKoreanContent() throws Exception {
            // Given - Korean language content as per PRD Korea focus
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                    .title("서울 문화 여행") // Seoul Cultural Trip in Korean
                    .description("전통과 현대가 공존하는 서울 탐험") // Explore Seoul where tradition meets modernity
                    .destination("서울") // Seoul in Korean
                    .startDate(LocalDate.now().plusDays(7))
                    .endDate(LocalDate.now().plusDays(10))
                    .isPublic(true)
                    .tags(Arrays.asList("문화", "음식", "역사")) // culture, food, history in Korean
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("서울 문화 여행"))
                    .andExpect(jsonPath("$.data.destination").value("서울"))
                    .andExpect(jsonPath("$.data.tags").isArray());
        }

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should retrieve travel plan with full details")
        void shouldRetrieveTravelPlanWithFullDetails() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID)
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(TEST_TRAVEL_PLAN_ID))
                    .andExpect(jsonPath("$.data.title").isNotEmpty())
                    .andExpect(jsonPath("$.data.destination").isNotEmpty())
                    .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID))
                    .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
        }

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should update travel plan with authorization check")
        void shouldUpdateTravelPlanWithAuthorizationCheck() throws Exception {
            // Given
            UpdateTravelPlanRequest updateRequest = UpdateTravelPlanRequest.builder()
                    .title("Updated Seoul Adventure")
                    .description("Enhanced Seoul exploration with new locations")
                    .status(TravelPlanStatus.CONFIRMED.name())
                    .build();

            // When & Then
            mockMvc.perform(put("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID)
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("Updated Seoul Adventure"))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

            // Verify database update
            TravelPlan updatedPlan = travelPlanRepository.findById(TEST_TRAVEL_PLAN_ID).orElseThrow();
            assertThat(updatedPlan.getTitle()).isEqualTo("Updated Seoul Adventure");
            assertThat(updatedPlan.getStatus()).isEqualTo(TravelPlanStatus.CONFIRMED);
        }

        @Test
        @WithMockUser(username = "different-user", roles = "USER")
        @DisplayName("Should prevent unauthorized travel plan updates")
        void shouldPreventUnauthorizedTravelPlanUpdates() throws Exception {
            // Given - Different user trying to update
            UpdateTravelPlanRequest updateRequest = UpdateTravelPlanRequest.builder()
                    .title("Malicious Update")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID)
                    .with(jwt().jwt(jwt -> jwt.subject("different-user")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should delete travel plan with proper authorization")
        void shouldDeleteTravelPlanWithProperAuthorization() throws Exception {
            // When
            mockMvc.perform(delete("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID)
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isNoContent());

            // Then - Verify deletion
            assertThat(travelPlanRepository.findById(TEST_TRAVEL_PLAN_ID))
                    .isEmpty();
        }
    }

    // ============================================================
    // SEARCH AND PAGINATION TESTS
    // ============================================================

    @Nested
    @DisplayName("Search and Pagination Tests")
    class SearchAndPaginationTests {

        @Test
        @DisplayName("Should search travel plans with pagination")
        void shouldSearchTravelPlansWithPagination() throws Exception {
            // Given - Create multiple travel plans for search
            createMultipleTravelPlans();

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("q", "Seoul")
                    .param("page", "0")
                    .param("size", "5")
                    .param("sort", "createdAt,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.pageNumber").value(0))
                    .andExpect(jsonPath("$.data.pageSize").value(5))
                    .andExpect(jsonPath("$.data.totalElements").isNumber());
        }

        @Test
        @DisplayName("Should search with Korean query terms")
        void shouldSearchWithKoreanQueryTerms() throws Exception {
            // Given - Travel plan with Korean content
            TravelPlan koreanPlan = testDataFactory.createTestTravelPlan();
            koreanPlan.setTitle("부산 해변 여행");
            koreanPlan.setDestination("부산");
            koreanPlan.setUser(testUser);
            travelPlanRepository.save(koreanPlan);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("q", "부산")
                    .characterEncoding("UTF-8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("Should get user travel plans with proper pagination")
        void shouldGetUserTravelPlansWithPagination() throws Exception {
            // Given - Create multiple plans for the user
            for (int i = 0; i < 15; i++) {
                TravelPlan plan = testDataFactory.createTestTravelPlan();
                plan.setUser(testUser);
                plan.setTitle("Plan " + i);
                travelPlanRepository.save(plan);
            }

            // When & Then
            mockMvc.perform(get("/api/v1/users/{userId}/travel-plans", TEST_USER_ID)
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(16)); // 15 + 1 original
        }
    }

    // ============================================================
    // PERFORMANCE AND LOAD TESTS
    // ============================================================

    @Nested
    @DisplayName("Performance and Load Tests")
    class PerformanceAndLoadTests {

        @Test
        @DisplayName("Should handle concurrent API requests efficiently")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void shouldHandleConcurrentAPIRequestsEfficiently() throws Exception {
            // Given - Multiple concurrent requests
            int concurrentRequests = 50;
            List<CompletableFuture<Long>> futures = new ArrayList<>();

            // When - Execute concurrent requests
            for (int i = 0; i < concurrentRequests; i++) {
                final int requestId = i;
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        
                        mockMvc.perform(get("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID)
                                .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                                .andExpect(status().isOk());
                        
                        return System.currentTimeMillis() - startTime;
                    } catch (Exception e) {
                        throw new RuntimeException("Request " + requestId + " failed", e);
                    }
                });
                futures.add(future);
            }

            // Then - All requests should complete within performance limits
            List<Long> responseTimes = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // Verify performance requirements
            double averageResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);

            assertThat(averageResponseTime)
                    .as("Average response time should be under 200ms")
                    .isLessThan(MAX_API_RESPONSE_TIME_MS);

            // Verify no requests failed
            assertThat(responseTimes)
                    .as("All concurrent requests should complete")
                    .hasSize(concurrentRequests);
        }

        @Test
        @DisplayName("Should maintain performance with large result sets")
        void shouldMaintainPerformanceWithLargeResultSets() throws Exception {
            // Given - Create large dataset
            createLargeDatasetForTesting();

            // When & Then - Query should complete within performance limits
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(get("/api/v1/travel-plans/public")
                    .param("page", "0")
                    .param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());

            long responseTime = System.currentTimeMillis() - startTime;
            
            assertThat(responseTime)
                    .as("Large dataset query should complete within 200ms")
                    .isLessThan(MAX_API_RESPONSE_TIME_MS);
        }
    }

    // ============================================================
    // ERROR HANDLING AND VALIDATION TESTS
    // ============================================================

    @Nested
    @DisplayName("Error Handling and Validation Tests")
    class ErrorHandlingAndValidationTests {

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should validate required fields in travel plan creation")
        void shouldValidateRequiredFieldsInTravelPlanCreation() throws Exception {
            // Given - Invalid request with missing required fields
            CreateTravelPlanRequest invalidRequest = CreateTravelPlanRequest.builder()
                    .title("") // Empty title
                    .destination(null) // Null destination
                    .startDate(null) // Null start date
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("Should handle travel plan not found gracefully")
        void shouldHandleTravelPlanNotFoundGracefully() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/{id}", "non-existent-id")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("TRAVEL_PLAN_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should handle unauthorized access gracefully")
        void shouldHandleUnauthorizedAccessGracefully() throws Exception {
            // When & Then - Request without authentication
            mockMvc.perform(get("/api/v1/travel-plans/{id}", TEST_TRAVEL_PLAN_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = TEST_USER_ID, roles = "USER")
        @DisplayName("Should validate date range logic")
        void shouldValidateDateRangeLogic() throws Exception {
            // Given - Invalid date range (end before start)
            CreateTravelPlanRequest invalidRequest = CreateTravelPlanRequest.builder()
                    .title("Invalid Date Range Plan")
                    .destination("Seoul")
                    .startDate(LocalDate.now().plusDays(10))
                    .endDate(LocalDate.now().plusDays(5)) // End before start
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.message").value("Start date must be before end date"));
        }
    }

    // ============================================================
    // SPATIAL AND LOCATION TESTS (PostGIS Integration)
    // ============================================================

    @Nested
    @DisplayName("Spatial and Location Tests")
    class SpatialAndLocationTests {

        @Test
        @DisplayName("Should search travel plans by geographic proximity")
        void shouldSearchTravelPlansByGeographicProximity() throws Exception {
            // Given - Create plans in different Korean cities
            createTravelPlansInDifferentCities();

            // When & Then - Search near Seoul
            mockMvc.perform(get("/api/v1/travel-plans/near")
                    .param("latitude", String.valueOf(SEOUL_LATITUDE))
                    .param("longitude", String.valueOf(SEOUL_LONGITUDE))
                    .param("radiusKm", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("Should validate coordinate ranges")
        void shouldValidateCoordinateRanges() throws Exception {
            // When & Then - Invalid coordinates
            mockMvc.perform(get("/api/v1/travel-plans/near")
                    .param("latitude", "200") // Invalid latitude
                    .param("longitude", "400") // Invalid longitude
                    .param("radiusKm", "10"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private void createMultipleTravelPlans() {
        for (int i = 0; i < 10; i++) {
            TravelPlan plan = testDataFactory.createTestTravelPlan();
            plan.setUser(testUser);
            plan.setTitle("Seoul Adventure " + i);
            plan.setDestination("Seoul");
            plan.setPublic(true);
            travelPlanRepository.save(plan);
        }
    }

    private void createLargeDatasetForTesting() {
        for (int i = 0; i < 200; i++) {
            TravelPlan plan = testDataFactory.createTestTravelPlan();
            plan.setUser(testUser);
            plan.setPublic(true);
            travelPlanRepository.save(plan);
        }
    }

    private void createTravelPlansInDifferentCities() {
        // Seoul plan
        TravelPlan seoulPlan = testDataFactory.createTestTravelPlan();
        seoulPlan.setUser(testUser);
        seoulPlan.setDestination("Seoul");
        seoulPlan.setPublic(true);
        travelPlanRepository.save(seoulPlan);

        // Busan plan  
        TravelPlan busanPlan = testDataFactory.createTestTravelPlan();
        busanPlan.setUser(testUser);
        busanPlan.setDestination("Busan");
        busanPlan.setPublic(true);
        travelPlanRepository.save(busanPlan);
    }
}