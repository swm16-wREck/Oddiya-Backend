package com.oddiya.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {
    
    @NotBlank(message = "Place ID is required")
    private String placeId;
    
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;
    
    @NotBlank(message = "Content is required")
    @Size(min = 10, max = 2000, message = "Review content must be between 10 and 2000 characters")
    private String content;
    
    private List<String> images;
    
    private String visitDate;
    
    private String travelPlanId;
}