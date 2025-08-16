package com.oddiya.repository;

import com.oddiya.entity.FestivalDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FestivalDetailRepository extends JpaRepository<FestivalDetail, String> {
    
    Optional<FestivalDetail> findByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
    
    void deleteByPlaceId(String placeId);
    
    // 진행중인 축제 조회
    @Query("SELECT f FROM FestivalDetail f WHERE :currentDate BETWEEN f.eventstartdate AND f.eventenddate")
    List<FestivalDetail> findOngoingFestivals(@Param("currentDate") LocalDate currentDate);
    
    // 예정된 축제 조회
    @Query("SELECT f FROM FestivalDetail f WHERE f.eventstartdate > :currentDate ORDER BY f.eventstartdate")
    List<FestivalDetail> findUpcomingFestivals(@Param("currentDate") LocalDate currentDate);
}