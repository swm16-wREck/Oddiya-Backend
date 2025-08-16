package com.oddiya.repository;

import com.oddiya.entity.PlaceOpeningHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceOpeningHoursRepository extends JpaRepository<PlaceOpeningHours, String> {
    
    List<PlaceOpeningHours> findByPlaceIdOrderByDayOfWeek(String placeId);
    
    Optional<PlaceOpeningHours> findByPlaceIdAndDayOfWeek(String placeId, Integer dayOfWeek);
    
    @Query("SELECT h FROM PlaceOpeningHours h WHERE h.place.id = :placeId AND h.dayOfWeek = :day")
    Optional<PlaceOpeningHours> findTodayHours(@Param("placeId") String placeId, @Param("day") Integer day);
    
    void deleteByPlaceId(String placeId);
    
    boolean existsByPlaceId(String placeId);
}