package com.oddiya.repository;

import com.oddiya.entity.SportsDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SportsDetailRepository extends JpaRepository<SportsDetail, String> {
    
    Optional<SportsDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
}