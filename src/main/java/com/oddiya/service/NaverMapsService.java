package com.oddiya.service;

import com.oddiya.dto.response.PlaceResponse;

import java.util.List;
import java.util.Map;

/**
 * Naver Maps API Service Interface
 * Handles location-based services and place searches for Korean locations
 */
public interface NaverMapsService {
    
    /**
     * Search places using Naver Maps API
     * 
     * @param query Search query
     * @param latitude Latitude for location-based search
     * @param longitude Longitude for location-based search
     * @param radius Search radius in meters
     * @return List of place responses
     */
    List<PlaceResponse> searchPlaces(String query, Double latitude, Double longitude, Integer radius);
    
    /**
     * Get place details by Naver Place ID
     * 
     * @param naverPlaceId Naver Place ID
     * @return Place details
     */
    PlaceResponse getPlaceDetails(String naverPlaceId);
    
    /**
     * Search places by category
     * 
     * @param category Place category (restaurant, cafe, tourist_attraction, etc.)
     * @param latitude Latitude
     * @param longitude Longitude  
     * @param radius Search radius in meters
     * @param limit Maximum results
     * @return List of places
     */
    List<PlaceResponse> searchByCategory(String category, Double latitude, Double longitude, Integer radius, Integer limit);
    
    /**
     * Get popular places in a specific area
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @param radius Search radius in meters
     * @param category Optional category filter
     * @return List of popular places
     */
    List<PlaceResponse> getPopularPlaces(Double latitude, Double longitude, Integer radius, String category);
    
    /**
     * Geocode address to coordinates
     * 
     * @param address Korean address
     * @return Coordinates map with lat/lng
     */
    Map<String, Double> geocodeAddress(String address);
    
    /**
     * Reverse geocode coordinates to address
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Korean address information
     */
    Map<String, Object> reverseGeocode(Double latitude, Double longitude);
    
    /**
     * Get directions between two points
     * 
     * @param startLat Start latitude
     * @param startLng Start longitude
     * @param endLat End latitude
     * @param endLng End longitude
     * @param option Route option (trafast, tracomfort, traoptimal)
     * @return Direction information including duration and distance
     */
    Map<String, Object> getDirections(Double startLat, Double startLng, Double endLat, Double endLng, String option);
    
    /**
     * Get nearby transportation options
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @param radius Search radius in meters
     * @return List of nearby stations/stops
     */
    List<Map<String, Object>> getNearbyTransportation(Double latitude, Double longitude, Integer radius);
    
    /**
     * Check service health and API limits
     * 
     * @return Service health status
     */
    Map<String, Object> getServiceHealth();
}