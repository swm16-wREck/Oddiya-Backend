package com.oddiya.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateItineraryItemRequest {
    
    @NotNull(message = "Day number is required")
    private Integer dayNumber;
    
    @NotNull(message = "Order is required")
    private Integer order;
    
    @NotBlank(message = "Place ID is required")
    private String placeId;
    
    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;
    
    @NotNull(message = "End time is required")
    private LocalDateTime endTime;
    
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
    
    private String transportMode;
    
    private Integer transportDuration;
}