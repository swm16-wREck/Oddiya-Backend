package com.oddiya.service;

import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import org.springframework.data.domain.Pageable;

public interface TravelPlanService {
    TravelPlanResponse createTravelPlan(String userId, CreateTravelPlanRequest request);
    TravelPlanResponse getTravelPlan(String id);
    TravelPlanResponse updateTravelPlan(String userId, String id, UpdateTravelPlanRequest request);
    void deleteTravelPlan(String userId, String id);
    PageResponse<TravelPlanResponse> getUserTravelPlans(String userId, Pageable pageable);
    PageResponse<TravelPlanResponse> getPublicTravelPlans(Pageable pageable);
    PageResponse<TravelPlanResponse> searchTravelPlans(String query, Pageable pageable);
    TravelPlanResponse copyTravelPlan(String userId, String id);
    void addCollaborator(String userId, String planId, String collaboratorId);
    void removeCollaborator(String userId, String planId, String collaboratorId);
    void incrementViewCount(String id);
    void saveTravelPlan(String userId, String id);
    void unsaveTravelPlan(String userId, String id);
    PageResponse<TravelPlanResponse> getSavedTravelPlans(String userId, Pageable pageable);
}