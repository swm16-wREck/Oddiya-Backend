package com.oddiya.contract;

import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.entity.TravelPlanStatus;
import com.oddiya.service.TravelPlanService;
import org.mockito.BDDMockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;

/**
 * Base class for Travel Plan contract tests
 */
public class TravelPlanContractTestBase extends ContractTestBase {

    @MockBean
    private TravelPlanService travelPlanService;

    public void setupTravelPlanMocks() {
        // Sample travel plan response
        TravelPlanResponse travelPlanResponse = TravelPlanResponse.builder()
                .id("tp123")
                .title("Seoul 3-Day Adventure")
                .description("Explore the best of Seoul in 3 days")
                .userId("user123")
                .userName("John Doe")
                .status(TravelPlanStatus.PUBLISHED)
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(33))
                .isPublic(true)
                .viewCount(150L)
                .likeCount(25L)
                .tags(List.of("seoul", "culture", "food"))
                .createdAt(LocalDateTime.now().minusDays(7))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        // Mock create travel plan
        BDDMockito.given(travelPlanService.createTravelPlan(anyString(), any(CreateTravelPlanRequest.class)))
                .willReturn(travelPlanResponse);

        // Mock get travel plan
        BDDMockito.given(travelPlanService.getTravelPlan(anyString()))
                .willReturn(travelPlanResponse);

        // Mock update travel plan
        BDDMockito.given(travelPlanService.updateTravelPlan(anyString(), anyString(), any(UpdateTravelPlanRequest.class)))
                .willReturn(travelPlanResponse);

        // Mock delete travel plan - void method
        BDDMockito.willDoNothing().given(travelPlanService).deleteTravelPlan(anyString(), anyString());

        // Mock get user travel plans
        PageResponse<TravelPlanResponse> pageResponse = PageResponse.<TravelPlanResponse>builder()
                .content(List.of(travelPlanResponse))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        BDDMockito.given(travelPlanService.getUserTravelPlans(anyString(), any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock get public travel plans
        BDDMockito.given(travelPlanService.getPublicTravelPlans(any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock search travel plans
        BDDMockito.given(travelPlanService.searchTravelPlans(anyString(), any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock copy travel plan
        BDDMockito.given(travelPlanService.copyTravelPlan(anyString(), anyString()))
                .willReturn(travelPlanResponse);

        // Mock collaborator operations - void methods
        BDDMockito.willDoNothing().given(travelPlanService).addCollaborator(anyString(), anyString(), anyString());
        BDDMockito.willDoNothing().given(travelPlanService).removeCollaborator(anyString(), anyString(), anyString());

        // Mock save/unsave operations - void methods
        BDDMockito.willDoNothing().given(travelPlanService).saveTravelPlan(anyString(), anyString());
        BDDMockito.willDoNothing().given(travelPlanService).unsaveTravelPlan(anyString(), anyString());

        // Mock get saved travel plans
        BDDMockito.given(travelPlanService.getSavedTravelPlans(anyString(), any(Pageable.class)))
                .willReturn(pageResponse);

        // Mock view count increment - void method
        BDDMockito.willDoNothing().given(travelPlanService).incrementViewCount(anyString());
    }
}