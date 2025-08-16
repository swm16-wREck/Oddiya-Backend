package com.oddiya.repository;

import com.oddiya.entity.PlacePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlacePhotoRepository extends JpaRepository<PlacePhoto, String> {
    
    List<PlacePhoto> findByPlaceIdOrderByIsPrimaryDescCreatedAtDesc(String placeId);
    
    List<PlacePhoto> findByPlaceId(String placeId);
    
    Optional<PlacePhoto> findByPlaceIdAndIsPrimaryTrue(String placeId);
    
    @Query("SELECT p FROM PlacePhoto p WHERE p.place.id = :placeId AND p.photoSource = :source")
    List<PlacePhoto> findByPlaceIdAndPhotoSource(@Param("placeId") String placeId, @Param("source") String source);
    
    void deleteByPlaceId(String placeId);
    
    long countByPlaceId(String placeId);
}