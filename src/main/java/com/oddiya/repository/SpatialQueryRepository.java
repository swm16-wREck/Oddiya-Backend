package com.oddiya.repository;

import com.oddiya.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for spatial queries on places using PostGIS functions.
 * Provides optimized spatial operations for location-based search and recommendations.
 * Note: This interface is implemented differently based on the database:
 * - PostgreSQL: Uses native PostGIS functions (production)
 * - H2: Uses simplified mock implementation (development/testing)
 */
public interface SpatialQueryRepository {
    
    /**
     * Find places within a specified radius using PostGIS spatial functions.
     * 
     * @param centerLat Center latitude
     * @param centerLng Center longitude 
     * @param radiusMeters Radius in meters (default 5000m)
     * @param category Optional category filter
     * @param minRating Optional minimum rating filter
     * @param pageable Pagination parameters
     * @return Page of places within radius ordered by distance
     */
    @Query(nativeQuery = true, value = """
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
        """)
    Page<Object[]> findPlacesWithinRadius(
        @Param("centerLat") Double centerLat,
        @Param("centerLng") Double centerLng,
        @Param("radiusMeters") Integer radiusMeters,
        @Param("category") String category,
        @Param("minRating") Double minRating,
        Pageable pageable
    );
    
    /**
     * Find nearby places for recommendations based on existing places.
     * 
     * @param placeIds Array of existing place IDs
     * @param radiusMeters Radius in meters for search
     * @param excludeCategories Categories to exclude from recommendations
     * @param limit Maximum number of results
     * @return List of recommended places
     */
    @Query(nativeQuery = true, value = """
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
        """)
    List<Object[]> findNearbyRecommendations(
        @Param("placeIds") String placeIds,
        @Param("radiusMeters") Integer radiusMeters,
        @Param("excludeCategories") String excludeCategories,
        @Param("limit") Integer limit
    );
    
    /**
     * Calculate route statistics for a sequence of places.
     * 
     * @param placeIds Comma-separated list of place IDs in route order
     * @return Route statistics including total distance and coordinates
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM calculate_places_route_distance(string_to_array(:placeIds, ','))
        """)
    List<Object[]> calculatePlacesRouteDistance(@Param("placeIds") String placeIds);
    
    /**
     * Get area statistics for places within a radius.
     * 
     * @param centerLat Center latitude
     * @param centerLng Center longitude
     * @param radiusMeters Radius in meters
     * @return Area statistics including counts and breakdowns
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM get_area_place_statistics(:centerLat, :centerLng, :radiusMeters)
        """)
    List<Object[]> getAreaPlaceStatistics(
        @Param("centerLat") Double centerLat,
        @Param("centerLng") Double centerLng,
        @Param("radiusMeters") Integer radiusMeters
    );
    
    /**
     * Validate if coordinates are within Korean boundaries.
     * 
     * @param latitude Latitude to validate
     * @param longitude Longitude to validate
     * @return True if coordinates are within South Korean boundaries
     */
    @Query(nativeQuery = true, value = """
        SELECT is_valid_korean_coordinates(:latitude, :longitude)
        """)
    Boolean isValidKoreanCoordinates(
        @Param("latitude") Double latitude,
        @Param("longitude") Double longitude
    );
    
    /**
     * Update popularity scores for all places.
     * Should be called periodically to refresh popularity rankings.
     * 
     * @return Number of places updated
     */
    @Query(nativeQuery = true, value = "SELECT update_place_popularity_scores()")
    Integer updatePlacePopularityScores();
    
    /**
     * Find places by category within Korean boundaries, ordered by popularity.
     * 
     * @param category Place category
     * @param pageable Pagination parameters
     * @return Page of popular places in the specified category
     */
    @Query(value = """
        SELECT p FROM Place p 
        WHERE p.category = :category 
          AND p.isDeleted = false 
          AND p.isVerified = true
          AND p.latitude >= 33.0 AND p.latitude <= 38.6
          AND p.longitude >= 125.0 AND p.longitude <= 131.9
        ORDER BY p.popularityScore DESC, p.rating DESC
        """)
    Page<Place> findPopularPlacesByCategory(@Param("category") String category, Pageable pageable);
    
    /**
     * Find trending places based on recent activity and high ratings.
     * 
     * @param pageable Pagination parameters
     * @return Page of trending places
     */
    @Query(value = """
        SELECT p FROM Place p 
        WHERE p.isDeleted = false 
          AND p.isVerified = true
          AND p.rating >= 4.0
          AND p.reviewCount >= 10
        ORDER BY (p.popularityScore * 0.7 + p.viewCount * 0.3) DESC
        """)
    Page<Place> findTrendingPlaces(Pageable pageable);
}