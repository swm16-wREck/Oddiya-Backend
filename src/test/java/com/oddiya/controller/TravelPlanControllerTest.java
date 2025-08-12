package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.exception.ResourceNotFoundException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.security.WithMockJwtUser;
import com.oddiya.service.TravelPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TravelPlanController.class)
@Import(TestSecurityConfig.class)
@DisplayName("TravelPlanController Tests")
class TravelPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TravelPlanService travelPlanService;

    @Autowired
    private ObjectMapper objectMapper;

    private TravelPlanResponse createSampleTravelPlanResponse() {
        return TravelPlanResponse.builder()
                .id("plan-123")
                .title("Tokyo Adventure")
                .description("Amazing trip to Tokyo")
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(37))
                .isPublic(true)
                .viewCount(0)
                .userId("user-123")
                .collaborators(Collections.emptyList())
                .itineraryItems(Collections.emptyList())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/travel-plans")
    class CreateTravelPlanTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully create travel plan with valid data")
        void createTravelPlanSuccess() throws Exception {
            // Given
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                    .title("Tokyo Adventure")
                    .description("Amazing trip to Tokyo")
                    .startDate(LocalDateTime.now().plusDays(30))
                    .endDate(LocalDateTime.now().plusDays(37))
                    .isPublic(true)
                    .build();

            TravelPlanResponse response = createSampleTravelPlanResponse();
            given(travelPlanService.createTravelPlan(eq("user-123"), any(CreateTravelPlanRequest.class)))
                    .willReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("plan-123")))
                    .andExpect(jsonPath("$.data.title", is("Tokyo Adventure")))
                    .andExpect(jsonPath("$.data.description", is("Amazing trip to Tokyo")))
                    .andExpect(jsonPath("$.data.isPublic", is(true)))
                    .andExpect(jsonPath("$.data.userId", is("user-123")));
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void createTravelPlanFailsWithoutAuthentication() throws Exception {
            // Given
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                    .title("Tokyo Adventure")
                    .description("Amazing trip to Tokyo")
                    .startDate(LocalDateTime.now().plusDays(30))
                    .endDate(LocalDateTime.now().plusDays(37))
                    .isPublic(true)
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should return 400 when required fields are missing")
        void createTravelPlanFailsWithMissingFields() throws Exception {
            // Given - request without required title
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                    .description("Amazing trip to Tokyo")
                    .startDate(LocalDateTime.now().plusDays(30))
                    .endDate(LocalDateTime.now().plusDays(37))
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/travel-plans/{id}")
    class GetTravelPlanTests {

        @Test
        @DisplayName("Should successfully get travel plan by ID")
        void getTravelPlanSuccess() throws Exception {
            // Given
            TravelPlanResponse response = createSampleTravelPlanResponse();
            given(travelPlanService.getTravelPlan("plan-123")).willReturn(response);
            willDoNothing().given(travelPlanService).incrementViewCount("plan-123");

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/plan-123"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("plan-123")))
                    .andExpect(jsonPath("$.data.title", is("Tokyo Adventure")));
        }

        @Test
        @DisplayName("Should return 404 when travel plan not found")
        void getTravelPlanNotFound() throws Exception {
            // Given
            given(travelPlanService.getTravelPlan("non-existent"))
                    .willThrow(new ResourceNotFoundException("Travel plan not found"));

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/non-existent"))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/travel-plans/{id}")
    class UpdateTravelPlanTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully update travel plan")
        void updateTravelPlanSuccess() throws Exception {
            // Given
            UpdateTravelPlanRequest request = UpdateTravelPlanRequest.builder()
                    .title("Updated Tokyo Adventure")
                    .description("Updated amazing trip to Tokyo")
                    .build();

            TravelPlanResponse response = createSampleTravelPlanResponse();
            response.setTitle("Updated Tokyo Adventure");
            response.setDescription("Updated amazing trip to Tokyo");

            given(travelPlanService.updateTravelPlan(eq("user-123"), eq("plan-123"), any(UpdateTravelPlanRequest.class)))
                    .willReturn(response);

            // When & Then
            mockMvc.perform(put("/api/v1/travel-plans/plan-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.title", is("Updated Tokyo Adventure")))
                    .andExpect(jsonPath("$.data.description", is("Updated amazing trip to Tokyo")));
        }

        @Test
        @WithMockJwtUser("user-456")
        @DisplayName("Should return 401 when user is not the owner")
        void updateTravelPlanFailsWithUnauthorizedUser() throws Exception {
            // Given
            UpdateTravelPlanRequest request = UpdateTravelPlanRequest.builder()
                    .title("Updated Tokyo Adventure")
                    .build();

            given(travelPlanService.updateTravelPlan(eq("user-456"), eq("plan-123"), any(UpdateTravelPlanRequest.class)))
                    .willThrow(new UnauthorizedException("Not authorized to update this travel plan"));

            // When & Then
            mockMvc.perform(put("/api/v1/travel-plans/plan-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/travel-plans/{id}")
    class DeleteTravelPlanTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully delete travel plan")
        void deleteTravelPlanSuccess() throws Exception {
            // Given
            willDoNothing().given(travelPlanService).deleteTravelPlan("user-123", "plan-123");

            // When & Then
            mockMvc.perform(delete("/api/v1/travel-plans/plan-123"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockJwtUser("user-456")
        @DisplayName("Should return 401 when user is not the owner")
        void deleteTravelPlanFailsWithUnauthorizedUser() throws Exception {
            // Given
            willThrow(new UnauthorizedException("Not authorized to delete this travel plan"))
                    .given(travelPlanService).deleteTravelPlan("user-456", "plan-123");

            // When & Then
            mockMvc.perform(delete("/api/v1/travel-plans/plan-123"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/travel-plans/user/{userId}")
    class GetUserTravelPlansTests {

        @Test
        @DisplayName("Should successfully get user travel plans with pagination")
        void getUserTravelPlansSuccess() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Arrays.asList(createSampleTravelPlanResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getUserTravelPlans(eq("user-123"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/user/user-123")
                    .param("page", "0")
                    .param("size", "20")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESC"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.pageNumber", is(0)))
                    .andExpect(jsonPath("$.data.pageSize", is(20)))
                    .andExpect(jsonPath("$.data.totalElements", is(1)))
                    .andExpect(jsonPath("$.data.first", is(true)))
                    .andExpect(jsonPath("$.data.last", is(true)));
        }

        @Test
        @DisplayName("Should use default pagination parameters when not provided")
        void getUserTravelPlansWithDefaults() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getUserTravelPlans(eq("user-123"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/user/user-123"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.pageNumber", is(0)))
                    .andExpect(jsonPath("$.data.pageSize", is(20)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/travel-plans/public")
    class GetPublicTravelPlansTests {

        @Test
        @DisplayName("Should successfully get public travel plans")
        void getPublicTravelPlansSuccess() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Arrays.asList(createSampleTravelPlanResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getPublicTravelPlans(any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/public"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/travel-plans/search")
    class SearchTravelPlansTests {

        @Test
        @DisplayName("Should successfully search travel plans")
        void searchTravelPlansSuccess() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Arrays.asList(createSampleTravelPlanResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.searchTravelPlans(eq("Tokyo"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", "Tokyo"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].title", is("Tokyo Adventure")));
        }

        @Test
        @DisplayName("Should return empty results when no matches found")
        void searchTravelPlansNoResults() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.searchTravelPlans(eq("NonExistent"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", "NonExistent"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 400 when query parameter is missing")
        void searchTravelPlansFailsWithoutQuery() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/search"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/travel-plans/{id}/copy")
    class CopyTravelPlanTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully copy travel plan")
        void copyTravelPlanSuccess() throws Exception {
            // Given
            TravelPlanResponse copiedPlan = createSampleTravelPlanResponse();
            copiedPlan.setId("copied-plan-123");
            copiedPlan.setTitle("Tokyo Adventure (Copy)");

            given(travelPlanService.copyTravelPlan("user-123", "plan-123"))
                    .willReturn(copiedPlan);

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans/plan-123/copy"))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("copied-plan-123")))
                    .andExpect(jsonPath("$.data.title", is("Tokyo Adventure (Copy)")));
        }
    }

    @Nested
    @DisplayName("Collaborator Management Tests")
    class CollaboratorTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully add collaborator")
        void addCollaboratorSuccess() throws Exception {
            // Given
            willDoNothing().given(travelPlanService)
                    .addCollaborator("user-123", "plan-123", "collaborator-456");

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans/plan-123/collaborators/collaborator-456"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully remove collaborator")
        void removeCollaboratorSuccess() throws Exception {
            // Given
            willDoNothing().given(travelPlanService)
                    .removeCollaborator("user-123", "plan-123", "collaborator-456");

            // When & Then
            mockMvc.perform(delete("/api/v1/travel-plans/plan-123/collaborators/collaborator-456"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Save/Unsave Travel Plan Tests")
    class SaveUnsaveTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully save travel plan")
        void saveTravelPlanSuccess() throws Exception {
            // Given
            willDoNothing().given(travelPlanService).saveTravelPlan("user-123", "plan-123");

            // When & Then
            mockMvc.perform(post("/api/v1/travel-plans/plan-123/save"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully unsave travel plan")
        void unsaveTravelPlanSuccess() throws Exception {
            // Given
            willDoNothing().given(travelPlanService).unsaveTravelPlan("user-123", "plan-123");

            // When & Then
            mockMvc.perform(delete("/api/v1/travel-plans/plan-123/save"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should get saved travel plans")
        void getSavedTravelPlansSuccess() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Arrays.asList(createSampleTravelPlanResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getSavedTravelPlans(eq("user-123"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/travel-plans/saved"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Request Parameter Validation Tests")
    class ParameterValidationTests {

        @Test
        @DisplayName("Should handle invalid pagination parameters gracefully")
        void handleInvalidPaginationParameters() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getPublicTravelPlans(any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then - negative page should be handled gracefully
            mockMvc.perform(get("/api/v1/travel-plans/public")
                    .param("page", "-1")
                    .param("size", "0"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should validate sort direction parameter")
        void validateSortDirectionParameter() throws Exception {
            // Given
            PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(travelPlanService.getUserTravelPlans(eq("user-123"), any(Pageable.class)))
                    .willReturn(pageResponse);

            // When & Then - invalid sort direction should be handled
            mockMvc.perform(get("/api/v1/travel-plans/user/user-123")
                    .param("sortDirection", "INVALID"))
                    .andDo(print())
                    .andExpect(status().isOk()); // Spring handles invalid enum gracefully
        }
    }
}