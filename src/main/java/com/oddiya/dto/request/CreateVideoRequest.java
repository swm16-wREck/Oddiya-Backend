package com.oddiya.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVideoRequest {
    
    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    @NotBlank(message = "Video URL is required")
    private String videoUrl;
    
    private String thumbnailUrl;
    
    private Integer duration;
    
    private String placeId;
    
    private String travelPlanId;
    
    private List<String> tags;
    
    @Builder.Default
    private Boolean isPublic = true;
}