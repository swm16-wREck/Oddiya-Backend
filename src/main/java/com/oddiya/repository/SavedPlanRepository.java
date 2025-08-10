package com.oddiya.repository;

import com.oddiya.entity.SavedPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
    boolean existsByUserIdAndTravelPlanId(String userId, String travelPlanId);
    Optional<SavedPlan> findByUserIdAndTravelPlanId(String userId, String travelPlanId);
    Page<SavedPlan> findByUserId(String userId, Pageable pageable);
    void deleteByUserIdAndTravelPlanId(String userId, String travelPlanId);
}