package com.oddiya.repository;

import com.oddiya.entity.PlaceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceReviewRepository extends JpaRepository<PlaceReview, String> {
    
    Page<PlaceReview> findByPlaceIdOrderByReviewTimeDesc(String placeId, Pageable pageable);
    
    List<PlaceReview> findByPlaceId(String placeId);
    
    @Query("SELECT AVG(r.rating) FROM PlaceReview r WHERE r.place.id = :placeId")
    Double calculateAverageRating(@Param("placeId") String placeId);
    
    @Query("SELECT COUNT(r) FROM PlaceReview r WHERE r.place.id = :placeId")
    Long countByPlaceId(@Param("placeId") String placeId);
    
    List<PlaceReview> findByPlaceIdAndRatingGreaterThanEqual(String placeId, Integer rating);
    
    void deleteByPlaceId(String placeId);
}