package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "itinerary_items", indexes = {
    @Index(name = "idx_itinerary_plan", columnList = "travel_plan_id"),
    @Index(name = "idx_itinerary_place", columnList = "place_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", nullable = false)
    private TravelPlan travelPlan;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;
    
    @Column(nullable = false)
    private Integer sequence;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    @Column(name = "place_name")
    private String placeName;
    
    @Column
    private String address;
    
    @Column
    private Double latitude;
    
    @Column
    private Double longitude;
    
    @Column(name = "estimated_cost", precision = 10, scale = 2)
    private BigDecimal estimatedCost;
    
    @Column(name = "actual_cost", precision = 10, scale = 2)
    private BigDecimal actualCost;
    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    @Column(name = "transport_mode")
    private String transportMode;
    
    @Column(name = "transport_duration_minutes")
    private Integer transportDurationMinutes;
    
    @Column(length = 1000)
    private String notes;
    
    @Column(name = "is_completed")
    @Builder.Default
    private boolean isCompleted = false;
}