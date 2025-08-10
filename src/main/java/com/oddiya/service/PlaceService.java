package com.oddiya.service;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PlaceService {
    PlaceResponse createPlace(CreatePlaceRequest request);
    PlaceResponse getPlace(String id);
    PlaceResponse updatePlace(String id, CreatePlaceRequest request);
    void deletePlace(String id);
    PageResponse<PlaceResponse> searchPlaces(String query, Pageable pageable);
    List<PlaceResponse> getNearbyPlaces(double latitude, double longitude, double radius);
    PageResponse<PlaceResponse> getPlacesByCategory(String category, Pageable pageable);
    PageResponse<PlaceResponse> getPopularPlaces(Pageable pageable);
    void incrementViewCount(String id);
}