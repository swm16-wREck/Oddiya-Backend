package com.oddiya.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTravelPlanRequest {
    
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    @Size(min = 1, max = 200, message = "Destination must be between 1 and 200 characters")
    private String destination;
    
    @Future(message = "Start date must be in the future")
    private LocalDate startDate;
    
    @Future(message = "End date must be in the future")
    private LocalDate endDate;
    
    private Boolean isPublic;
    
    private String imageUrl;
    
    private List<String> tags;
    
    private String status;
}