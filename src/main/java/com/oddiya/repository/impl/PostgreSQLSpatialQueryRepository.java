package com.oddiya.repository.impl;

import com.oddiya.entity.Place;
import com.oddiya.repository.SpatialQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PostgreSQL/PostGIS implementation of SpatialQueryRepository.
 * Uses native PostGIS spatial functions for optimal performance.
 */
@Slf4j
@Repository
@Profile({"local", "aws", "postgresql"})
@RequiredArgsConstructor
public class PostgreSQLSpatialQueryRepository implements SpatialQueryRepository {
    
    private final EntityManager entityManager;
    
    @Override
    public Page<Object[]> findPlacesWithinRadius(
            Double centerLat, Double centerLng, Integer radiusMeters,
            String category, Double minRating, Pageable pageable) {
        
        String sql = """
            SELECT p.*, 
                   ROUND(ST_Distance(p.location, create_geography_point(:centerLat, :centerLng))::numeric)::INTEGER as distance_meters
            FROM places p 
            WHERE p.is_deleted = false 
              AND p.is_verified = true
              AND ST_DWithin(p.location, create_geography_point(:centerLat, :centerLng), :radiusMeters)
              AND (:category IS NULL OR p.category = :category)
              AND (:minRating IS NULL OR p.rating >= :minRating)
            ORDER BY ST_Distance(p.location, create_geography_point(:centerLat, :centerLng)),
                     p.popularity_score DESC,
                     p.rating DESC
            """;
        
        Query query = entityManager.createNativeQuery(sql)
            .setParameter("centerLat", centerLat)
            .setParameter("centerLng", centerLng)
            .setParameter("radiusMeters", radiusMeters)
            .setParameter("category", category)
            .setParameter("minRating", minRating)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize());
        
        List<Object[]> results = query.getResultList();
        
        // Get total count
        String countSql = """
            SELECT COUNT(*)
            FROM places p 
            WHERE p.is_deleted = false 
              AND p.is_verified = true
              AND ST_DWithin(p.location, create_geography_point(:centerLat, :centerLng), :radiusMeters)
              AND (:category IS NULL OR p.category = :category)
              AND (:minRating IS NULL OR p.rating >= :minRating)
            """;
        
        Query countQuery = entityManager.createNativeQuery(countSql)
            .setParameter("centerLat", centerLat)
            .setParameter("centerLng", centerLng)
            .setParameter("radiusMeters", radiusMeters)
            .setParameter("category", category)
            .setParameter("minRating", minRating);
        
        Long total = ((Number) countQuery.getSingleResult()).longValue();
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public List<Object[]> findNearbyRecommendations(
            String placeIds, Integer radiusMeters,
            String excludeCategories, Integer limit) {
        
        String sql = """
            SELECT p.*, 
                   ROUND(min_distance.min_dist)::INTEGER as distance_from_nearest,
                   ROUND(avg_distance.avg_dist)::INTEGER as avg_distance_to_group
            FROM places p
            CROSS JOIN LATERAL (
                SELECT MIN(ST_Distance(p.location, ref_p.location)) as min_dist
                FROM places ref_p
                WHERE ref_p.id = ANY(string_to_array(:placeIds, ','))
            ) min_distance
            CROSS JOIN LATERAL (
                SELECT AVG(ST_Distance(p.location, ref_p.location)) as avg_dist
                FROM places ref_p
                WHERE ref_p.id = ANY(string_to_array(:placeIds, ','))
            ) avg_distance
            WHERE p.is_deleted = false
              AND p.is_verified = true
              AND p.id != ALL(string_to_array(:placeIds, ','))
              AND min_distance.min_dist <= :radiusMeters
              AND (:excludeCategories IS NULL OR p.category != ALL(string_to_array(:excludeCategories, ',')))
            ORDER BY min_distance.min_dist,
                     p.popularity_score DESC,
                     p.rating DESC
            LIMIT :limit
            """;
        
        Query query = entityManager.createNativeQuery(sql)
            .setParameter("placeIds", placeIds)
            .setParameter("radiusMeters", radiusMeters)
            .setParameter("excludeCategories", excludeCategories)
            .setParameter("limit", limit);
        
        return query.getResultList();
    }
    
    @Override
    public List<Object[]> calculatePlacesRouteDistance(String placeIds) {
        String sql = "SELECT * FROM calculate_places_route_distance(string_to_array(:placeIds, ','))";
        
        Query query = entityManager.createNativeQuery(sql)
            .setParameter("placeIds", placeIds);
        
        return query.getResultList();
    }
    
    @Override
    public List<Object[]> getAreaPlaceStatistics(
            Double centerLat, Double centerLng, Integer radiusMeters) {
        
        String sql = "SELECT * FROM get_area_place_statistics(:centerLat, :centerLng, :radiusMeters)";
        
        Query query = entityManager.createNativeQuery(sql)
            .setParameter("centerLat", centerLat)
            .setParameter("centerLng", centerLng)
            .setParameter("radiusMeters", radiusMeters);
        
        return query.getResultList();
    }
    
    @Override
    public Boolean isValidKoreanCoordinates(Double latitude, Double longitude) {
        String sql = "SELECT is_valid_korean_coordinates(:latitude, :longitude)";
        
        Query query = entityManager.createNativeQuery(sql)
            .setParameter("latitude", latitude)
            .setParameter("longitude", longitude);
        
        return (Boolean) query.getSingleResult();
    }
    
    @Override
    public Integer updatePlacePopularityScores() {
        String sql = "SELECT update_place_popularity_scores()";
        
        Query query = entityManager.createNativeQuery(sql);
        
        return ((Number) query.getSingleResult()).intValue();
    }
    
    @Override
    public Page<Place> findPopularPlacesByCategory(String category, Pageable pageable) {
        String jpql = """
            SELECT p FROM Place p 
            WHERE p.category = :category 
              AND p.isDeleted = false 
              AND p.isVerified = true
              AND p.latitude >= 33.0 AND p.latitude <= 38.6
              AND p.longitude >= 125.0 AND p.longitude <= 131.9
            ORDER BY p.popularityScore DESC, p.rating DESC
            """;
        
        Query query = entityManager.createQuery(jpql, Place.class)
            .setParameter("category", category)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize());
        
        List<Place> results = query.getResultList();
        
        // Get total count
        String countJpql = """
            SELECT COUNT(p) FROM Place p 
            WHERE p.category = :category 
              AND p.isDeleted = false 
              AND p.isVerified = true
              AND p.latitude >= 33.0 AND p.latitude <= 38.6
              AND p.longitude >= 125.0 AND p.longitude <= 131.9
            """;
        
        Query countQuery = entityManager.createQuery(countJpql)
            .setParameter("category", category);
        
        Long total = (Long) countQuery.getSingleResult();
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public Page<Place> findTrendingPlaces(Pageable pageable) {
        String jpql = """
            SELECT p FROM Place p 
            WHERE p.isDeleted = false 
              AND p.isVerified = true
              AND p.rating >= 4.0
              AND p.reviewCount >= 10
            ORDER BY (p.popularityScore * 0.7 + p.viewCount * 0.3) DESC
            """;
        
        Query query = entityManager.createQuery(jpql, Place.class)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize());
        
        List<Place> results = query.getResultList();
        
        // Get total count
        String countJpql = """
            SELECT COUNT(p) FROM Place p 
            WHERE p.isDeleted = false 
              AND p.isVerified = true
              AND p.rating >= 4.0
              AND p.reviewCount >= 10
            """;
        
        Query countQuery = entityManager.createQuery(countJpql);
        Long total = (Long) countQuery.getSingleResult();
        
        return new PageImpl<>(results, pageable, total);
    }
}