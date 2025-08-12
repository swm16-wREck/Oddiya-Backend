package com.oddiya.dto.request;

import jakarta.validation.constraints.*;
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
public class PlaceRequest {
    
    @NotBlank(message = "Place name is required")
    @Size(min = 1, max = 255, message = "Place name must be between 1 and 255 characters")
    private String name;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    private String description;
    
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;
    
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;
    
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
    
    private String phoneNumber;
    
    private String website;
    
    private Map<String, String> openingHours;
    
    private List<String> tags;
    
    private List<String> images;
    
    private Double rating;
    
    private Integer priceLevel;
    
    private Map<String, Object> metadata;
}