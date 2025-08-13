package com.oddiya.repository;

import com.oddiya.entity.CultureFacilityDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CultureFacilityDetailRepository extends JpaRepository<CultureFacilityDetail, String> {
    
    Optional<CultureFacilityDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
}