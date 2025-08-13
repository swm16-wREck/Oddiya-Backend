package com.oddiya.service;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Enhanced Place Service Interface
 * Integrates with PostGIS spatial queries and Naver Maps API for location services
 */
public interface PlaceService {
    
    /**
     * Create a new place
     */
    PlaceResponse createPlace(CreatePlaceRequest request);
    
    /**
     * Get place by ID
     */
    PlaceResponse getPlace(String id);
    
    /**
     * Update existing place
     */
    PlaceResponse updatePlace(String id, CreatePlaceRequest request);
    
    /**
     * Delete place
     */
    void deletePlace(String id);
    
    /**
     * Search places with text query
     */
    PageResponse<PlaceResponse> searchPlaces(String query, Pageable pageable);
    
    /**
     * Get nearby places using PostGIS spatial query
     * 
     * @param latitude Latitude
     * @param longitude Longitude  
     * @param radiusKm Radius in kilometers
     * @return List of nearby places
     */
    List<PlaceResponse> getNearbyPlaces(double latitude, double longitude, double radiusKm);
    
    /**
     * Advanced spatial search with filters
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @param radiusKm Radius in kilometers
     * @param category Category filter
     * @param minRating Minimum rating filter
     * @param limit Maximum results
     * @return Filtered nearby places
     */
    List<PlaceResponse> searchNearbyPlaces(double latitude, double longitude, double radiusKm, 
                                          String category, Double minRating, Integer limit);
    
    /**
     * Get places by category with pagination
     */
    PageResponse<PlaceResponse> getPlacesByCategory(String category, Pageable pageable);
    
    /**
     * Get popular places sorted by popularity score
     */
    PageResponse<PlaceResponse> getPopularPlaces(Pageable pageable);
    
    /**
     * Get popular places in specific area
     */
    List<PlaceResponse> getPopularPlacesInArea(double latitude, double longitude, double radiusKm, Integer limit);
    
    /**
     * Search places using external APIs (Naver Maps)
     * 
     * @param query Search query
     * @param latitude Optional center latitude
     * @param longitude Optional center longitude
     * @param radiusKm Optional search radius in km
     * @return Places from external APIs
     */
    List<PlaceResponse> searchExternalPlaces(String query, Double latitude, Double longitude, Double radiusKm);
    
    /**
     * Sync place with external data sources
     */
    PlaceResponse syncPlaceWithExternalData(String placeId);
    
    /**
     * Get place recommendations based on user preferences
     * 
     * @param latitude User's location latitude
     * @param longitude User's location longitude
     * @param preferences User preferences
     * @param limit Maximum recommendations
     * @return Recommended places
     */
    List<PlaceResponse> getPlaceRecommendations(double latitude, double longitude, 
                                               Map<String, Object> preferences, Integer limit);
    
    /**
     * Calculate distance between two places
     */
    double calculateDistance(String placeId1, String placeId2);
    
    /**
     * Get places within travel distance/time
     */
    List<PlaceResponse> getPlacesWithinTravelDistance(double latitude, double longitude, 
                                                     int maxTravelTimeMinutes, String transportMode);
    
    /**
     * Increment place view count
     */
    void incrementViewCount(String id);
    
    /**
     * Update place popularity score
     */
    void updatePopularityScore(String placeId);
    
    /**
     * Bulk import places from external sources
     */
    List<PlaceResponse> importPlacesFromExternal(String area, List<String> categories);
    
    /**
     * Get place statistics
     */
    Map<String, Object> getPlaceStatistics(String placeId);
    
    /**
     * Verify place data with external sources
     */
    boolean verifyPlaceData(String placeId);
}