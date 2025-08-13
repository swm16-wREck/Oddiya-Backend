package com.oddiya.repository;

import com.oddiya.entity.AccommodationDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccommodationDetailRepository extends JpaRepository<AccommodationDetail, String> {
    
    Optional<AccommodationDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
}