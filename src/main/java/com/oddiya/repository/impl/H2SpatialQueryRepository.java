package com.oddiya.repository.impl;

import com.oddiya.entity.Place;
import com.oddiya.repository.SpatialQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * H2-compatible implementation of SpatialQueryRepository.
 * Provides basic functionality without PostGIS spatial features.
 * This is a simplified implementation for development/testing with H2 database.
 */
@Slf4j
@Repository
@Profile({"test", "docker", "h2"})
@RequiredArgsConstructor
public class H2SpatialQueryRepository implements SpatialQueryRepository {
    
    @Override
    public Page<Object[]> findPlacesWithinRadius(
            Double centerLat, Double centerLng, Integer radiusMeters,
            String category, Double minRating, Pageable pageable) {
        log.warn("H2 implementation: Spatial queries not supported, returning empty result");
        return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }
    
    @Override
    public List<Object[]> findNearbyRecommendations(
            String placeIds, Integer radiusMeters,
            String excludeCategories, Integer limit) {
        log.warn("H2 implementation: Spatial recommendations not supported, returning empty result");
        return new ArrayList<>();
    }
    
    @Override
    public List<Object[]> calculatePlacesRouteDistance(String placeIds) {
        log.warn("H2 implementation: Route distance calculation not supported, returning empty result");
        return new ArrayList<>();
    }
    
    @Override
    public List<Object[]> getAreaPlaceStatistics(
            Double centerLat, Double centerLng, Integer radiusMeters) {
        log.warn("H2 implementation: Area statistics not supported, returning empty result");
        return new ArrayList<>();
    }
    
    @Override
    public Boolean isValidKoreanCoordinates(Double latitude, Double longitude) {
        // Simple boundary check for South Korea
        return latitude >= 33.0 && latitude <= 38.6 && 
               longitude >= 125.0 && longitude <= 131.9;
    }
    
    @Override
    public Integer updatePlacePopularityScores() {
        log.warn("H2 implementation: Popularity score update not supported");
        return 0;
    }
    
    @Override
    public Page<Place> findPopularPlacesByCategory(String category, Pageable pageable) {
        log.warn("H2 implementation: Category search not fully supported, returning empty result");
        return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }
    
    @Override
    public Page<Place> findTrendingPlaces(Pageable pageable) {
        log.warn("H2 implementation: Trending places not supported, returning empty result");
        return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }
}