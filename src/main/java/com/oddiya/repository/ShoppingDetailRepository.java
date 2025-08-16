package com.oddiya.repository;

import com.oddiya.entity.ShoppingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShoppingDetailRepository extends JpaRepository<ShoppingDetail, String> {
    
    Optional<ShoppingDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
}