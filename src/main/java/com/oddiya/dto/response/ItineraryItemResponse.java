package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryItemResponse {
    private String id;
    private Integer dayNumber;
    private Integer order;
    private PlaceResponse place;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String notes;
    private String transportMode;
    private Integer transportDuration;
    
    // Alias method for compatibility with tests
    public Integer getSequence() {
        return this.order;
    }
}