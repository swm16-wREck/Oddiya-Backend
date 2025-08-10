package com.oddiya.repository;

import com.oddiya.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, String> {
    List<ItineraryItem> findByTravelPlanIdOrderByDayNumberAscSequenceAsc(String travelPlanId);
    void deleteByTravelPlanId(String travelPlanId);
}