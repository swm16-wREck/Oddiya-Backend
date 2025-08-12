package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.exception.ResourceNotFoundException;
import com.oddiya.service.PlaceService;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlaceController.class)
@Import(TestSecurityConfig.class)
@DisplayName("PlaceController Tests")
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceService placeService;

    @Autowired
    private ObjectMapper objectMapper;

    private PlaceResponse createSamplePlaceResponse() {
        return PlaceResponse.builder()
                .id("place-123")
                .name("Tokyo Tower")
                .description("Famous tower in Tokyo")
                .category("ATTRACTION")
                .address("Tokyo, Japan")
                .latitude(35.6586)
                .longitude(139.7454)
                .phoneNumber("+81-3-3433-5111")
                .website("https://www.tokyotower.co.jp")
                .openingHours("9:00-23:00")
                .priceRange("$$")
                .rating(4.5)
                .viewCount(1000)
                .imageUrls(Arrays.asList("https://example.com/image1.jpg", "https://example.com/image2.jpg"))
                .tags(Arrays.asList("tower", "viewpoint", "landmark"))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/places")
    class CreatePlaceTests {

        @Test
        @DisplayName("Should successfully create place with valid data")
        void createPlaceSuccess() throws Exception {
            // Given
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .name("Tokyo Tower")
                    .description("Famous tower in Tokyo")
                    .category("ATTRACTION")
                    .address("Tokyo, Japan")
                    .latitude(35.6586)
                    .longitude(139.7454)
                    .phoneNumber("+81-3-3433-5111")
                    .website("https://www.tokyotower.co.jp")
                    .openingHours("9:00-23:00")
                    .priceRange("$$")
                    .imageUrls(Arrays.asList("https://example.com/image1.jpg"))
                    .tags(Arrays.asList("tower", "viewpoint"))
                    .build();

            PlaceResponse response = createSamplePlaceResponse();
            given(placeService.createPlace(any(CreatePlaceRequest.class))).willReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("place-123")))
                    .andExpect(jsonPath("$.data.name", is("Tokyo Tower")))
                    .andExpect(jsonPath("$.data.description", is("Famous tower in Tokyo")))
                    .andExpect(jsonPath("$.data.category", is("ATTRACTION")))
                    .andExpect(jsonPath("$.data.latitude", is(35.6586)))
                    .andExpect(jsonPath("$.data.longitude", is(139.7454)))
                    .andExpect(jsonPath("$.data.rating", is(4.5)))
                    .andExpect(jsonPath("$.data.imageUrls", hasSize(2)))
                    .andExpect(jsonPath("$.data.tags", hasSize(3)));
        }

        @Test
        @DisplayName("Should return 400 when required fields are missing")
        void createPlaceFailsWithMissingFields() throws Exception {
            // Given - request without required name
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .description("Famous tower in Tokyo")
                    .category("ATTRACTION")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("Should return 400 when coordinates are invalid")
        void createPlaceFailsWithInvalidCoordinates() throws Exception {
            // Given - request with invalid latitude/longitude
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .name("Tokyo Tower")
                    .description("Famous tower in Tokyo")
                    .category("ATTRACTION")
                    .latitude(200.0) // Invalid latitude (should be -90 to 90)
                    .longitude(300.0) // Invalid longitude (should be -180 to 180)
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("Should handle malformed JSON")
        void createPlaceFailsWithMalformedJson() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid-json"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/{id}")
    class GetPlaceTests {

        @Test
        @DisplayName("Should successfully get place by ID")
        void getPlaceSuccess() throws Exception {
            // Given
            PlaceResponse response = createSamplePlaceResponse();
            given(placeService.getPlace("place-123")).willReturn(response);
            willDoNothing().given(placeService).incrementViewCount("place-123");

            // When & Then
            mockMvc.perform(get("/api/v1/places/place-123"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("place-123")))
                    .andExpect(jsonPath("$.data.name", is("Tokyo Tower")))
                    .andExpect(jsonPath("$.data.viewCount", is(1000)));
        }

        @Test
        @DisplayName("Should return 404 when place not found")
        void getPlaceNotFound() throws Exception {
            // Given
            given(placeService.getPlace("non-existent"))
                    .willThrow(new ResourceNotFoundException("Place not found"));

            // When & Then
            mockMvc.perform(get("/api/v1/places/non-existent"))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")))
                    .andExpect(jsonPath("$.error.message", is("Place not found")));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/places/{id}")
    class UpdatePlaceTests {

        @Test
        @DisplayName("Should successfully update place")
        void updatePlaceSuccess() throws Exception {
            // Given
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .name("Updated Tokyo Tower")
                    .description("Updated description")
                    .category("ATTRACTION")
                    .build();

            PlaceResponse response = createSamplePlaceResponse();
            response.setName("Updated Tokyo Tower");
            response.setDescription("Updated description");

            given(placeService.updatePlace(eq("place-123"), any(CreatePlaceRequest.class))).willReturn(response);

            // When & Then
            mockMvc.perform(put("/api/v1/places/place-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.name", is("Updated Tokyo Tower")))
                    .andExpect(jsonPath("$.data.description", is("Updated description")));
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent place")
        void updatePlaceNotFound() throws Exception {
            // Given
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .name("Updated Place")
                    .category("ATTRACTION")
                    .build();

            given(placeService.updatePlace(eq("non-existent"), any(CreatePlaceRequest.class)))
                    .willThrow(new ResourceNotFoundException("Place not found"));

            // When & Then
            mockMvc.perform(put("/api/v1/places/non-existent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/places/{id}")
    class DeletePlaceTests {

        @Test
        @DisplayName("Should successfully delete place")
        void deletePlaceSuccess() throws Exception {
            // Given
            willDoNothing().given(placeService).deletePlace("place-123");

            // When & Then
            mockMvc.perform(delete("/api/v1/places/place-123"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent place")
        void deletePlaceNotFound() throws Exception {
            // Given
            willThrow(new ResourceNotFoundException("Place not found"))
                    .given(placeService).deletePlace("non-existent");

            // When & Then
            mockMvc.perform(delete("/api/v1/places/non-existent"))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/search")
    class SearchPlacesTests {

        @Test
        @DisplayName("Should successfully search places by query")
        void searchPlacesSuccess() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Arrays.asList(createSamplePlaceResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.searchPlaces(eq("Tokyo"), any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/search")
                    .param("query", "Tokyo"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].name", is("Tokyo Tower")))
                    .andExpect(jsonPath("$.data.pageNumber", is(0)))
                    .andExpect(jsonPath("$.data.pageSize", is(20)))
                    .andExpect(jsonPath("$.data.totalElements", is(1)));
        }

        @Test
        @DisplayName("Should return empty results when no matches found")
        void searchPlacesNoResults() throws Exception {
            // Given
            PageResponse<PlaceResponse> emptyResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.searchPlaces(eq("NonExistent"), any(Pageable.class))).willReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/search")
                    .param("query", "NonExistent"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 400 when query parameter is missing")
        void searchPlacesFailsWithoutQuery() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/places/search"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle custom pagination parameters")
        void searchPlacesWithCustomPagination() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(1)
                    .pageSize(10)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(false)
                    .last(true)
                    .build();

            given(placeService.searchPlaces(eq("Tokyo"), any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/search")
                    .param("query", "Tokyo")
                    .param("page", "1")
                    .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageNumber", is(1)))
                    .andExpect(jsonPath("$.data.pageSize", is(10)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/nearby")
    class GetNearbyPlacesTests {

        @Test
        @DisplayName("Should successfully get nearby places")
        void getNearbyPlacesSuccess() throws Exception {
            // Given
            List<PlaceResponse> nearbyPlaces = Arrays.asList(createSamplePlaceResponse());
            given(placeService.getNearbyPlaces(35.6586, 139.7454, 1000.0)).willReturn(nearbyPlaces);

            // When & Then
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "35.6586")
                    .param("longitude", "139.7454")
                    .param("radius", "1000.0"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].name", is("Tokyo Tower")));
        }

        @Test
        @DisplayName("Should use default radius when not provided")
        void getNearbyPlacesWithDefaultRadius() throws Exception {
            // Given
            List<PlaceResponse> nearbyPlaces = Arrays.asList(createSamplePlaceResponse());
            given(placeService.getNearbyPlaces(35.6586, 139.7454, 1000.0)).willReturn(nearbyPlaces);

            // When & Then
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "35.6586")
                    .param("longitude", "139.7454"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should return 400 when coordinates are missing")
        void getNearbyPlacesFailsWithMissingCoordinates() throws Exception {
            // When & Then - missing longitude
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "35.6586"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return empty list when no nearby places")
        void getNearbyPlacesNoResults() throws Exception {
            // Given
            given(placeService.getNearbyPlaces(0.0, 0.0, 1000.0)).willReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "0.0")
                    .param("longitude", "0.0")
                    .param("radius", "1000.0"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Should handle invalid coordinate values")
        void getNearbyPlacesInvalidCoordinates() throws Exception {
            // When & Then - invalid latitude
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "invalid")
                    .param("longitude", "139.7454"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/category/{category}")
    class GetPlacesByCategoryTests {

        @Test
        @DisplayName("Should successfully get places by category")
        void getPlacesByCategorySuccess() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Arrays.asList(createSamplePlaceResponse()))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.getPlacesByCategory(eq("ATTRACTION"), any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/category/ATTRACTION"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].category", is("ATTRACTION")));
        }

        @Test
        @DisplayName("Should return empty results for non-existent category")
        void getPlacesByCategoryNoResults() throws Exception {
            // Given
            PageResponse<PlaceResponse> emptyResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.getPlacesByCategory(eq("NONEXISTENT"), any(Pageable.class))).willReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/category/NONEXISTENT"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }

        @Test
        @DisplayName("Should handle pagination for category results")
        void getPlacesByCategoryWithPagination() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(1)
                    .pageSize(5)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(false)
                    .last(true)
                    .build();

            given(placeService.getPlacesByCategory(eq("RESTAURANT"), any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/category/RESTAURANT")
                    .param("page", "1")
                    .param("size", "5"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageNumber", is(1)))
                    .andExpect(jsonPath("$.data.pageSize", is(5)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/places/popular")
    class GetPopularPlacesTests {

        @Test
        @DisplayName("Should successfully get popular places")
        void getPopularPlacesSuccess() throws Exception {
            // Given
            PlaceResponse popularPlace = createSamplePlaceResponse();
            popularPlace.setViewCount(5000); // High view count = popular

            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Arrays.asList(popularPlace))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.getPopularPlaces(any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/popular"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].viewCount", is(5000)));
        }

        @Test
        @DisplayName("Should handle pagination for popular places")
        void getPopularPlacesWithPagination() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(2)
                    .pageSize(10)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(false)
                    .last(true)
                    .build();

            given(placeService.getPopularPlaces(any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/popular")
                    .param("page", "2")
                    .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageNumber", is(2)))
                    .andExpect(jsonPath("$.data.pageSize", is(10)));
        }

        @Test
        @DisplayName("Should return empty results when no popular places")
        void getPopularPlacesNoResults() throws Exception {
            // Given
            PageResponse<PlaceResponse> emptyResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.getPopularPlaces(any(Pageable.class))).willReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/popular"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Content Type and Header Validation Tests")
    class ContentTypeAndHeaderTests {

        @Test
        @DisplayName("Should accept only application/json for POST requests")
        void shouldRejectNonJsonContentTypeForPost() throws Exception {
            // Given
            CreatePlaceRequest request = CreatePlaceRequest.builder()
                    .name("Tokyo Tower")
                    .category("ATTRACTION")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/places")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Should return application/json content type in all responses")
        void shouldReturnJsonContentType() throws Exception {
            // Given
            PlaceResponse response = createSamplePlaceResponse();
            given(placeService.getPlace("place-123")).willReturn(response);
            willDoNothing().given(placeService).incrementViewCount("place-123");

            // When & Then
            mockMvc.perform(get("/api/v1/places/place-123"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Should handle CORS preflight requests")
        void shouldHandleCorsPreflight() throws Exception {
            // When & Then
            mockMvc.perform(options("/api/v1/places")
                    .header("Origin", "https://example.com")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Edge Case and Error Handling Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle special characters in search query")
        void searchPlacesWithSpecialCharacters() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.searchPlaces(anyString(), any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/search")
                    .param("query", "東京タワー!@#$%"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should handle extreme coordinate values")
        void getNearbyPlacesWithExtremeCoordinates() throws Exception {
            // Given
            given(placeService.getNearbyPlaces(-90.0, -180.0, 1.0)).willReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "-90.0")
                    .param("longitude", "-180.0")
                    .param("radius", "1.0"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should handle very large radius values")
        void getNearbyPlacesWithLargeRadius() throws Exception {
            // Given
            given(placeService.getNearbyPlaces(0.0, 0.0, 999999.0)).willReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", "0.0")
                    .param("longitude", "0.0")
                    .param("radius", "999999.0"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle negative pagination values gracefully")
        void handleNegativePaginationValues() throws Exception {
            // Given
            PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();

            given(placeService.getPopularPlaces(any(Pageable.class))).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/places/popular")
                    .param("page", "-1")
                    .param("size", "-5"))
                    .andDo(print())
                    .andExpect(status().isOk()); // Spring handles negative values gracefully
        }
    }
}