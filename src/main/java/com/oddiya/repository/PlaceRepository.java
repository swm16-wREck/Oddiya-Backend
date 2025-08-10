package com.oddiya.repository;

import com.oddiya.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceRepository extends JpaRepository<Place, String> {
    
    Optional<Place> findByNaverPlaceId(String naverPlaceId);
    
    boolean existsByNaverPlaceId(String naverPlaceId);
    
    @Query("SELECT p FROM Place p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.address) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND p.isDeleted = false")
    Page<Place> searchPlaces(@Param("query") String query, Pageable pageable);
    
    @Query(value = "SELECT * FROM places p WHERE " +
           "ST_DWithin(ST_MakePoint(p.longitude, p.latitude)::geography, " +
           "ST_MakePoint(:lng, :lat)::geography, :radius) " +
           "AND p.is_deleted = false " +
           "ORDER BY ST_Distance(ST_MakePoint(p.longitude, p.latitude)::geography, " +
           "ST_MakePoint(:lng, :lat)::geography)",
           nativeQuery = true)
    List<Place> findNearbyPlaces(
        @Param("lat") Double latitude,
        @Param("lng") Double longitude,
        @Param("radius") Integer radiusInMeters
    );
    
    Page<Place> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);
    
    @Query("SELECT p FROM Place p WHERE p.category IN :categories AND p.isDeleted = false")
    Page<Place> findByCategoriesIn(@Param("categories") List<String> categories, Pageable pageable);
    
    @Query("SELECT p FROM Place p WHERE p.isVerified = true AND p.isDeleted = false " +
           "ORDER BY p.popularityScore DESC")
    List<Place> findTopPopularPlaces(Pageable pageable);
    
    @Query("SELECT p FROM Place p JOIN p.tags t WHERE t IN :tags AND p.isDeleted = false")
    Page<Place> findByTags(@Param("tags") List<String> tags, Pageable pageable);
    
    @Query("SELECT p FROM Place p WHERE p.rating >= :minRating AND p.isDeleted = false " +
           "ORDER BY p.rating DESC")
    Page<Place> findByMinimumRating(@Param("minRating") Double minRating, Pageable pageable);
    
    Page<Place> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
            String name, String address, Pageable pageable);
    Page<Place> findByCategory(String category, Pageable pageable);
    Page<Place> findAllByOrderByPopularityScoreDesc(Pageable pageable);
}