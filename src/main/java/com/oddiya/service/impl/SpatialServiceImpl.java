package com.oddiya.service.impl;

import com.oddiya.entity.Place;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.repository.SpatialQueryRepository;
import com.oddiya.service.SpatialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SpatialService for location-based operations.
 * Uses PostGIS spatial functions for optimal performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpatialServiceImpl implements SpatialService {
    
    private final PlaceRepository placeRepository;
    private final SpatialQueryRepository spatialQueryRepository;
    private final EntityManager entityManager;
    
    // Default values
    private static final int DEFAULT_RADIUS_METERS = 5000;
    private static final int DEFAULT_RECOMMENDATION_RADIUS = 2000;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;
    private static final int DEFAULT_MAX_DETOUR_METERS = 1000;
    
    @Override
    public Page<Map<String, Object>> searchPlacesWithinRadius(
            Double latitude, Double longitude, Integer radiusMeters, 
            String category, Double minRating, Pageable pageable) {
        
        log.debug("Searching places within {}m of ({}, {}), category: {}, minRating: {}", 
                  radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS, 
                  latitude, longitude, category, minRating);
        
        if (!validateKoreanCoordinates(latitude, longitude)) {
            log.warn("Invalid coordinates provided: ({}, {})", latitude, longitude);
            return Page.empty(pageable);
        }
        
        int searchRadius = radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS;
        
        Page<Object[]> results = spatialQueryRepository.findPlacesWithinRadius(
            latitude, longitude, searchRadius, category, minRating, pageable
        );
        
        List<Map<String, Object>> places = results.getContent().stream()
            .map(this::mapPlaceResult)
            .collect(Collectors.toList());
            
        return new PageImpl<>(places, pageable, results.getTotalElements());
    }
    
    @Override
    public List<Map<String, Object>> getPlaceRecommendations(
            List<String> existingPlaceIds, Integer radiusMeters, 
            List<String> excludeCategories, Integer limit) {
        
        if (existingPlaceIds == null || existingPlaceIds.isEmpty()) {
            log.warn("No existing place IDs provided for recommendations");
            return Collections.emptyList();
        }
        
        log.debug("Getting recommendations for {} places within {}m", 
                  existingPlaceIds.size(), 
                  radiusMeters != null ? radiusMeters : DEFAULT_RECOMMENDATION_RADIUS);
        
        String placeIdsStr = String.join(",", existingPlaceIds);
        String excludeCategoriesStr = excludeCategories != null ? 
            String.join(",", excludeCategories) : null;
        int searchRadius = radiusMeters != null ? radiusMeters : DEFAULT_RECOMMENDATION_RADIUS;
        int resultLimit = limit != null ? limit : DEFAULT_RECOMMENDATION_LIMIT;
        
        List<Object[]> results = spatialQueryRepository.findNearbyRecommendations(
            placeIdsStr, searchRadius, excludeCategoriesStr, resultLimit
        );
        
        return results.stream()
            .map(this::mapRecommendationResult)
            .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, Object> calculateRouteInformation(List<String> placeIds) {
        if (placeIds == null || placeIds.size() < 2) {
            log.warn("Insufficient places provided for route calculation: {}", 
                     placeIds != null ? placeIds.size() : 0);
            return Collections.emptyMap();
        }
        
        log.debug("Calculating route for {} places", placeIds.size());
        
        String placeIdsStr = String.join(",", placeIds);
        List<Object[]> results = spatialQueryRepository.calculatePlacesRouteDistance(placeIdsStr);
        
        if (results.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Object[] result = results.get(0);
        Map<String, Object> routeInfo = new HashMap<>();
        routeInfo.put("totalDistanceMeters", result[0]);
        routeInfo.put("maxSegmentDistanceMeters", result[1]);
        routeInfo.put("placeCoordinates", result[2]);
        routeInfo.put("numberOfPlaces", placeIds.size());
        
        return routeInfo;
    }
    
    @Override
    public Map<String, Object> getAreaStatistics(Double latitude, Double longitude, Integer radiusMeters) {
        if (!validateKoreanCoordinates(latitude, longitude)) {
            log.warn("Invalid coordinates for area statistics: ({}, {})", latitude, longitude);
            return Collections.emptyMap();
        }
        
        int searchRadius = radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS * 2; // Default 10km for statistics
        
        log.debug("Getting area statistics for {}m radius around ({}, {})", 
                  searchRadius, latitude, longitude);
        
        List<Object[]> results = spatialQueryRepository.getAreaPlaceStatistics(
            latitude, longitude, searchRadius
        );
        
        if (results.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Object[] result = results.get(0);
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalPlaces", result[0]);
        statistics.put("verifiedPlaces", result[1]);
        statistics.put("averageRating", result[2]);
        statistics.put("categoryBreakdown", result[3]);
        statistics.put("topRatedPlaces", result[4]);
        statistics.put("searchRadiusMeters", searchRadius);
        statistics.put("centerLatitude", latitude);
        statistics.put("centerLongitude", longitude);
        
        return statistics;
    }
    
    @Override
    public Boolean validateKoreanCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        try {
            return spatialQueryRepository.isValidKoreanCoordinates(latitude, longitude);
        } catch (Exception e) {
            log.error("Error validating coordinates ({}, {}): {}", latitude, longitude, e.getMessage());
            return false;
        }
    }
    
    @Override
    public Page<Place> findPopularPlacesByCategory(String category, Pageable pageable) {
        log.debug("Finding popular places by category: {}", category);
        return spatialQueryRepository.findPopularPlacesByCategory(category, pageable);
    }
    
    @Override
    public Page<Place> findTrendingPlaces(Pageable pageable) {
        log.debug("Finding trending places");
        return spatialQueryRepository.findTrendingPlaces(pageable);
    }
    
    @Override
    @Transactional
    public Integer updatePopularityScores() {
        log.info("Updating popularity scores for all places");
        try {
            Integer updatedCount = spatialQueryRepository.updatePlacePopularityScores();
            log.info("Updated popularity scores for {} places", updatedCount);
            return updatedCount;
        } catch (Exception e) {
            log.error("Error updating popularity scores: {}", e.getMessage());
            throw new RuntimeException("Failed to update popularity scores", e);
        }
    }
    
    @Override
    public Map<String, Object> findOptimalMeetingPoint(List<List<Double>> locations) {
        if (locations == null || locations.size() < 2) {
            log.warn("Insufficient locations provided for meeting point calculation");
            return Collections.emptyMap();
        }
        
        // Calculate centroid
        double avgLat = locations.stream().mapToDouble(loc -> loc.get(0)).average().orElse(0.0);
        double avgLng = locations.stream().mapToDouble(loc -> loc.get(1)).average().orElse(0.0);
        
        log.debug("Calculated meeting point centroid: ({}, {})", avgLat, avgLng);
        
        // Find places near the centroid
        Page<Map<String, Object>> nearbyPlaces = searchPlacesWithinRadius(
            avgLat, avgLng, 1000, null, 4.0, 
            org.springframework.data.domain.PageRequest.of(0, 10)
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("centerLatitude", avgLat);
        result.put("centerLongitude", avgLng);
        result.put("nearbyPlaces", nearbyPlaces.getContent());
        result.put("inputLocations", locations);
        
        return result;
    }
    
    @Override
    public List<Map<String, Object>> findPlacesAlongRoute(
            Double startLat, Double startLng, Double endLat, Double endLng, 
            String category, Integer maxDetourMeters) {
        
        if (!validateKoreanCoordinates(startLat, startLng) || 
            !validateKoreanCoordinates(endLat, endLng)) {
            log.warn("Invalid route coordinates provided");
            return Collections.emptyList();
        }
        
        int detourLimit = maxDetourMeters != null ? maxDetourMeters : DEFAULT_MAX_DETOUR_METERS;
        
        // Calculate route midpoint and search around it
        double midLat = (startLat + endLat) / 2;
        double midLng = (startLng + endLng) / 2;
        
        // Calculate approximate route length to determine search radius
        double routeDistance = calculateSimpleDistance(startLat, startLng, endLat, endLng);
        int searchRadius = (int) (routeDistance / 2) + detourLimit;
        
        log.debug("Searching for places along route from ({}, {}) to ({}, {}), radius: {}m", 
                  startLat, startLng, endLat, endLng, searchRadius);
        
        Page<Map<String, Object>> places = searchPlacesWithinRadius(
            midLat, midLng, searchRadius, category, null,
            org.springframework.data.domain.PageRequest.of(0, 50)
        );
        
        // Filter places that are actually along the route (simple heuristic)
        return places.getContent().stream()
            .filter(place -> {
                Double placeLat = (Double) place.get("latitude");
                Double placeLng = (Double) place.get("longitude");
                double distanceFromRoute = calculateDistanceFromRoute(
                    placeLat, placeLng, startLat, startLng, endLat, endLng
                );
                return distanceFromRoute <= detourLimit;
            })
            .collect(Collectors.toList());
    }
    
    // Helper methods
    private Map<String, Object> mapPlaceResult(Object[] result) {
        Map<String, Object> place = new HashMap<>();
        place.put("id", result[0]);
        place.put("name", result[1]);
        place.put("category", result[2]);
        place.put("address", result[3]);
        place.put("latitude", result[4]);
        place.put("longitude", result[5]);
        place.put("distanceMeters", result[6]);
        place.put("rating", result[7]);
        place.put("reviewCount", result[8]);
        place.put("popularityScore", result[9]);
        return place;
    }
    
    private Map<String, Object> mapRecommendationResult(Object[] result) {
        Map<String, Object> recommendation = mapPlaceResult(Arrays.copyOf(result, 10));
        recommendation.put("distanceFromNearest", result[10]);
        recommendation.put("avgDistanceToGroup", result[11]);
        return recommendation;
    }
    
    private double calculateSimpleDistance(double lat1, double lng1, double lat2, double lng2) {
        // Simplified distance calculation (Haversine approximation)
        final int earthRadius = 6371000; // Earth's radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
    
    private double calculateDistanceFromRoute(
            double pointLat, double pointLng, 
            double startLat, double startLng, 
            double endLat, double endLng) {
        // Simplified distance from point to line segment
        // This is a basic implementation; a more accurate version would use proper geometric calculations
        double distanceToStart = calculateSimpleDistance(pointLat, pointLng, startLat, startLng);
        double distanceToEnd = calculateSimpleDistance(pointLat, pointLng, endLat, endLng);
        return Math.min(distanceToStart, distanceToEnd);
    }
}