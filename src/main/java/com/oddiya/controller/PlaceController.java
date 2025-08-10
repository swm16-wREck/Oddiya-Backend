package com.oddiya.controller;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "Places", description = "Place management API")
public class PlaceController {
    
    private final PlaceService placeService;
    
    @PostMapping
    @Operation(summary = "Create place", description = "Create a new place")
    public ResponseEntity<ApiResponse<PlaceResponse>> createPlace(
            @Valid @RequestBody CreatePlaceRequest request) {
        PlaceResponse response = placeService.createPlace(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get place", description = "Get place by ID")
    public ResponseEntity<ApiResponse<PlaceResponse>> getPlace(@PathVariable String id) {
        PlaceResponse response = placeService.getPlace(id);
        placeService.incrementViewCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update place", description = "Update an existing place")
    public ResponseEntity<ApiResponse<PlaceResponse>> updatePlace(
            @PathVariable String id,
            @Valid @RequestBody CreatePlaceRequest request) {
        PlaceResponse response = placeService.updatePlace(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete place", description = "Delete a place")
    public ResponseEntity<ApiResponse<Void>> deletePlace(@PathVariable String id) {
        placeService.deletePlace(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search places", description = "Search places by query")
    public ResponseEntity<ApiResponse<PageResponse<PlaceResponse>>> searchPlaces(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<PlaceResponse> response = placeService.searchPlaces(query, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/nearby")
    @Operation(summary = "Get nearby places", description = "Get places near a location")
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1000") double radius) {
        List<PlaceResponse> response = placeService.getNearbyPlaces(latitude, longitude, radius);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/category/{category}")
    @Operation(summary = "Get places by category", description = "Get places by category")
    public ResponseEntity<ApiResponse<PageResponse<PlaceResponse>>> getPlacesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<PlaceResponse> response = placeService.getPlacesByCategory(category, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/popular")
    @Operation(summary = "Get popular places", description = "Get popular places")
    public ResponseEntity<ApiResponse<PageResponse<PlaceResponse>>> getPopularPlaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<PlaceResponse> response = placeService.getPopularPlaces(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}