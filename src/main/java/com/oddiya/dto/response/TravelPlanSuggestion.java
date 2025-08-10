package com.oddiya.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI-generated travel plan suggestion")
public class TravelPlanSuggestion {
    
    @Schema(description = "Plan title", example = "5-Day Seoul Adventure")
    private String title;
    
    @Schema(description = "Plan description")
    private String description;
    
    @Schema(description = "Destination", example = "Seoul")
    private String destination;
    
    @Schema(description = "Duration in days", example = "5")
    private Integer duration;
    
    @Schema(description = "Daily itinerary")
    private List<DayPlan> dailyItinerary;
    
    @Schema(description = "Full itinerary text")
    private String itinerary;
    
    @Schema(description = "Estimated total cost", example = "$2000")
    private String estimatedCost;
    
    @Schema(description = "Travel tips")
    private List<String> tips;
    
    @Schema(description = "Recommended places")
    private List<String> recommendedPlaces;
    
    @Schema(description = "Generated timestamp")
    private Date generatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Daily plan")
    public static class DayPlan {
        
        @Schema(description = "Day number", example = "1")
        private Integer day;
        
        @Schema(description = "Day title", example = "Exploring Gangnam")
        private String title;
        
        @Schema(description = "Morning activities")
        private List<String> morning;
        
        @Schema(description = "Afternoon activities")
        private List<String> afternoon;
        
        @Schema(description = "Evening activities")
        private List<String> evening;
        
        @Schema(description = "Meal recommendations")
        private Map<String, String> meals;
        
        @Schema(description = "Estimated daily cost", example = "$400")
        private String estimatedCost;
    }
}