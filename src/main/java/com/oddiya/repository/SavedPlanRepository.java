package com.oddiya.repository;

import com.oddiya.entity.SavedPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SavedPlan s WHERE s.user.id = :userId AND s.travelPlan.id = :travelPlanId")
    boolean existsByUserIdAndTravelPlanId(@Param("userId") String userId, @Param("travelPlanId") String travelPlanId);
    
    @Query("SELECT s FROM SavedPlan s WHERE s.user.id = :userId AND s.travelPlan.id = :travelPlanId")
    Optional<SavedPlan> findByUserIdAndTravelPlanId(@Param("userId") String userId, @Param("travelPlanId") String travelPlanId);
    
    @Query("SELECT s FROM SavedPlan s WHERE s.user.id = :userId")
    Page<SavedPlan> findByUserId(@Param("userId") String userId, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM SavedPlan s WHERE s.user.id = :userId AND s.travelPlan.id = :travelPlanId")
    void deleteByUserIdAndTravelPlanId(@Param("userId") String userId, @Param("travelPlanId") String travelPlanId);
}