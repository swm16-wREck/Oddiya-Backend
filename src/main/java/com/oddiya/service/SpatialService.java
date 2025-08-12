package com.oddiya.service;

import com.oddiya.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Service interface for spatial operations and location-based queries.
 * Provides high-level spatial operations for the travel planning system.
 */
public interface SpatialService {
    
    /**
     * Search for places within a specified radius.
     * 
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusMeters Search radius in meters (default: 5000)
     * @param category Optional category filter
     * @param minRating Optional minimum rating filter
     * @param pageable Pagination parameters
     * @return Page of places with distance information
     */
    Page<Map<String, Object>> searchPlacesWithinRadius(
        Double latitude, 
        Double longitude, 
        Integer radiusMeters, 
        String category, 
        Double minRating, 
        Pageable pageable
    );
    
    /**
     * Get place recommendations based on existing places in a travel plan.
     * 
     * @param existingPlaceIds List of place IDs already in the plan
     * @param radiusMeters Search radius in meters (default: 2000)
     * @param excludeCategories Categories to exclude from recommendations
     * @param limit Maximum number of recommendations (default: 20)
     * @return List of recommended places with relevance scores
     */
    List<Map<String, Object>> getPlaceRecommendations(
        List<String> existingPlaceIds,
        Integer radiusMeters,
        List<String> excludeCategories,
        Integer limit
    );
    
    /**
     * Calculate route information for a sequence of places.
     * 
     * @param placeIds List of place IDs in route order
     * @return Route information including total distance, coordinates, and segments
     */
    Map<String, Object> calculateRouteInformation(List<String> placeIds);
    
    /**
     * Get area statistics for places in a specific region.
     * 
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusMeters Analysis radius in meters
     * @return Statistics including place counts, category breakdown, and top places
     */
    Map<String, Object> getAreaStatistics(Double latitude, Double longitude, Integer radiusMeters);
    
    /**
     * Validate if coordinates are within South Korean boundaries.
     * 
     * @param latitude Latitude to validate
     * @param longitude Longitude to validate
     * @return True if coordinates are valid for the Korean service area
     */
    Boolean validateKoreanCoordinates(Double latitude, Double longitude);
    
    /**
     * Find popular places by category within Korea.
     * 
     * @param category Place category
     * @param pageable Pagination parameters
     * @return Page of popular places in the category
     */
    Page<Place> findPopularPlacesByCategory(String category, Pageable pageable);
    
    /**
     * Find currently trending places based on activity and ratings.
     * 
     * @param pageable Pagination parameters
     * @return Page of trending places
     */
    Page<Place> findTrendingPlaces(Pageable pageable);
    
    /**
     * Update popularity scores for all places.
     * Should be called periodically (e.g., daily) to refresh rankings.
     * 
     * @return Number of places updated
     */
    Integer updatePopularityScores();
    
    /**
     * Find optimal meeting point for multiple locations.
     * 
     * @param locations List of coordinate pairs [latitude, longitude]
     * @return Optimal central point and nearby places
     */
    Map<String, Object> findOptimalMeetingPoint(List<List<Double>> locations);
    
    /**
     * Get places along a route between two points.
     * 
     * @param startLat Starting latitude
     * @param startLng Starting longitude
     * @param endLat Ending latitude
     * @param endLng Ending longitude
     * @param category Optional category filter
     * @param maxDetourMeters Maximum detour distance from direct route
     * @return List of places along the route
     */
    List<Map<String, Object>> findPlacesAlongRoute(
        Double startLat, Double startLng,
        Double endLat, Double endLng,
        String category,
        Integer maxDetourMeters
    );
}