package com.oddiya.contract;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.service.PlaceService;
import org.mockito.BDDMockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;

/**
 * Base class for Place contract tests
 */
public class PlaceContractTestBase extends ContractTestBase {

    @MockBean
    private PlaceService placeService;

    public void setupPlaceMocks() {
        // Sample place response
        PlaceResponse placeResponse = PlaceResponse.builder()
                .id("place123")
                .name("Gyeongbokgung Palace")
                .description("A historic palace in Seoul, South Korea")
                .category("attraction")
                .address("161 Sajik-ro, Jongno-gu, Seoul, South Korea")
                .latitude(37.5788)
                .longitude(126.9770)
                .rating(4.5)
                .viewCount(5000L)
                .imageUrls(List.of("https://example.com/gyeongbok1.jpg"))
                .tags(List.of("palace", "history", "culture"))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        // Mock create place
        BDDMockito.given(placeService.createPlace(any(CreatePlaceRequest.class)))
                .willReturn(placeResponse);

        // Mock get place
        BDDMockito.given(placeService.getPlace(anyString()))
                .willReturn(placeResponse);

        // Mock update place
        BDDMockito.given(placeService.updatePlace(anyString(), any(CreatePlaceRequest.class)))
                .willReturn(placeResponse);

        // Mock delete place - void method
        BDDMockito.willDoNothing().given(placeService).deletePlace(anyString());

        // Mock search places
        PageResponse<PlaceResponse> pageResponse = PageResponse.<PlaceResponse>builder()
                .content(List.of(placeResponse))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        BDDMockito.given(placeService.searchPlaces(anyString(), any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock get nearby places
        BDDMockito.given(placeService.getNearbyPlaces(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(placeResponse));

        // Mock get places by category
        BDDMockito.given(placeService.getPlacesByCategory(anyString(), any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock get popular places
        BDDMockito.given(placeService.getPopularPlaces(any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock view count increment - void method
        BDDMockito.willDoNothing().given(placeService).incrementViewCount(anyString());
    }
}