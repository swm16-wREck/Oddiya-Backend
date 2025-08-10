package com.oddiya.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlaceRequest {
    
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    private String name;
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    @NotBlank(message = "Address is required")
    @Size(min = 1, max = 500, message = "Address must be between 1 and 500 characters")
    private String address;
    
    @NotNull(message = "Latitude is required")
    private Double latitude;
    
    @NotNull(message = "Longitude is required")
    private Double longitude;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    private String phoneNumber;
    
    private String website;
    
    private Map<String, String> openingHours;
    
    private Double priceRange;
    
    private List<String> images;
    
    private List<String> tags;
    
    private String googlePlaceId;
    
    private Map<String, Object> metadata;
}