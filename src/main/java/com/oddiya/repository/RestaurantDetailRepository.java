package com.oddiya.repository;

import com.oddiya.entity.RestaurantDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantDetailRepository extends JpaRepository<RestaurantDetail, String> {
    
    Optional<RestaurantDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
}