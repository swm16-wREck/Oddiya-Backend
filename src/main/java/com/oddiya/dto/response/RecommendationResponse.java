package com.oddiya.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Travel recommendation response")
public class RecommendationResponse {
    
    @Schema(description = "Destination", example = "Seoul")
    private String destination;
    
    @Schema(description = "List of recommendations")
    private List<String> recommendations;
    
    @Schema(description = "Places to visit")
    private List<String> places;
    
    @Schema(description = "Recommended activities")
    private List<String> activities;
    
    @Schema(description = "Restaurant suggestions")
    private List<String> restaurants;
    
    @Schema(description = "Accommodation suggestions")
    private List<String> accommodations;
    
    @Schema(description = "Travel tips")
    private List<String> tips;
    
    @Schema(description = "Generated timestamp")
    private Date generatedAt;
}