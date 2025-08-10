package com.oddiya.repository;

import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TravelPlanRepository extends JpaRepository<TravelPlan, String> {
    
    Page<TravelPlan> findByUserIdAndIsDeletedFalse(String userId, Pageable pageable);
    
    Page<TravelPlan> findByUserIdAndStatusAndIsDeletedFalse(String userId, TravelPlanStatus status, Pageable pageable);
    
    @Query("SELECT tp FROM TravelPlan tp WHERE " +
           "(LOWER(tp.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(tp.destination) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(tp.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND tp.isPublic = true AND tp.isDeleted = false")
    Page<TravelPlan> searchPublicPlans(@Param("query") String query, Pageable pageable);
    
    @Query("SELECT tp FROM TravelPlan tp WHERE " +
           "tp.destination = :destination AND " +
           "tp.startDate >= :startDate AND " +
           "tp.endDate <= :endDate AND " +
           "tp.isPublic = true AND tp.isDeleted = false")
    List<TravelPlan> findSimilarPlans(
        @Param("destination") String destination,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT tp FROM TravelPlan tp WHERE " +
           "tp.user.id = :userId AND " +
           "((tp.startDate BETWEEN :startDate AND :endDate) OR " +
           "(tp.endDate BETWEEN :startDate AND :endDate)) AND " +
           "tp.isDeleted = false")
    List<TravelPlan> findOverlappingPlans(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    Optional<TravelPlan> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
    
    @Query("SELECT tp FROM TravelPlan tp WHERE tp.isPublic = true AND tp.isDeleted = false " +
           "ORDER BY tp.viewCount DESC")
    Page<TravelPlan> findPopularPlans(Pageable pageable);
    
    @Query("SELECT tp FROM TravelPlan tp JOIN tp.collaborators c WHERE c.id = :userId AND tp.isDeleted = false")
    Page<TravelPlan> findCollaboratingPlans(@Param("userId") String userId, Pageable pageable);
    
    Page<TravelPlan> findByUserId(String userId, Pageable pageable);
    Page<TravelPlan> findByIsPublicTrue(Pageable pageable);
    Page<TravelPlan> findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
            String title, String destination, Pageable pageable);
}