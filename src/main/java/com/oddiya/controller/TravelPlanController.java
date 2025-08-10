package com.oddiya.controller;

import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.service.TravelPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/travel-plans")
@RequiredArgsConstructor
@Tag(name = "Travel Plans", description = "Travel plan management API")
public class TravelPlanController {
    
    private final TravelPlanService travelPlanService;
    
    @PostMapping
    @Operation(summary = "Create travel plan", description = "Create a new travel plan")
    public ResponseEntity<ApiResponse<TravelPlanResponse>> createTravelPlan(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateTravelPlanRequest request) {
        TravelPlanResponse response = travelPlanService.createTravelPlan(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get travel plan", description = "Get travel plan by ID")
    public ResponseEntity<ApiResponse<TravelPlanResponse>> getTravelPlan(@PathVariable String id) {
        TravelPlanResponse response = travelPlanService.getTravelPlan(id);
        travelPlanService.incrementViewCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update travel plan", description = "Update an existing travel plan")
    public ResponseEntity<ApiResponse<TravelPlanResponse>> updateTravelPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateTravelPlanRequest request) {
        TravelPlanResponse response = travelPlanService.updateTravelPlan(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete travel plan", description = "Delete a travel plan")
    public ResponseEntity<ApiResponse<Void>> deleteTravelPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        travelPlanService.deleteTravelPlan(userId, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user travel plans", description = "Get all travel plans for a user")
    public ResponseEntity<ApiResponse<PageResponse<TravelPlanResponse>>> getUserTravelPlans(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<TravelPlanResponse> response = travelPlanService.getUserTravelPlans(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/public")
    @Operation(summary = "Get public travel plans", description = "Get all public travel plans")
    public ResponseEntity<ApiResponse<PageResponse<TravelPlanResponse>>> getPublicTravelPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "viewCount") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<TravelPlanResponse> response = travelPlanService.getPublicTravelPlans(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search travel plans", description = "Search travel plans by query")
    public ResponseEntity<ApiResponse<PageResponse<TravelPlanResponse>>> searchTravelPlans(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<TravelPlanResponse> response = travelPlanService.searchTravelPlans(query, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/{id}/copy")
    @Operation(summary = "Copy travel plan", description = "Create a copy of an existing travel plan")
    public ResponseEntity<ApiResponse<TravelPlanResponse>> copyTravelPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        TravelPlanResponse response = travelPlanService.copyTravelPlan(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
    
    @PostMapping("/{id}/collaborators/{collaboratorId}")
    @Operation(summary = "Add collaborator", description = "Add a collaborator to travel plan")
    public ResponseEntity<ApiResponse<Void>> addCollaborator(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @PathVariable String collaboratorId) {
        travelPlanService.addCollaborator(userId, id, collaboratorId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    @Operation(summary = "Remove collaborator", description = "Remove a collaborator from travel plan")
    public ResponseEntity<ApiResponse<Void>> removeCollaborator(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @PathVariable String collaboratorId) {
        travelPlanService.removeCollaborator(userId, id, collaboratorId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @PostMapping("/{id}/save")
    @Operation(summary = "Save travel plan", description = "Save a travel plan to user's saved list")
    public ResponseEntity<ApiResponse<Void>> saveTravelPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        travelPlanService.saveTravelPlan(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @DeleteMapping("/{id}/save")
    @Operation(summary = "Unsave travel plan", description = "Remove travel plan from user's saved list")
    public ResponseEntity<ApiResponse<Void>> unsaveTravelPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        travelPlanService.unsaveTravelPlan(userId, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @GetMapping("/saved")
    @Operation(summary = "Get saved travel plans", description = "Get user's saved travel plans")
    public ResponseEntity<ApiResponse<PageResponse<TravelPlanResponse>>> getSavedTravelPlans(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<TravelPlanResponse> response = travelPlanService.getSavedTravelPlans(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}