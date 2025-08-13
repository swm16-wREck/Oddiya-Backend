package com.oddiya.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * AI Travel Plan Generation Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AITravelPlanRequest {
    
    @NotBlank(message = "Destination is required")
    private String destination;
    
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    @NotBlank(message = "Budget range is required")
    private String budgetRange; // budget, mid-range, luxury
    
    @NotNull(message = "At least one interest is required")
    private List<String> interests; // food, culture, nature, shopping, nightlife, history, etc.
    
    private Integer numberOfPeople;
    
    private String accommodationType; // hotel, hostel, airbnb, guesthouse
    
    private String transportPreference; // public, walking, rental-car, mixed
    
    private String travelStyle; // relaxed, packed, adventure, cultural, foodie
    
    private List<String> dietaryRestrictions; // vegetarian, vegan, halal, kosher, gluten-free
    
    private List<String> accessibilityNeeds; // wheelchair, mobility, visual, hearing
    
    private String languagePreference; // ko, en, ja, zh
    
    private Boolean includeWeather; // include weather-based recommendations
    
    private Boolean includeBudgetBreakdown; // detailed budget analysis
    
    private Boolean includeLocalTips; // local customs and tips
    
    private Boolean optimizeTravel; // optimize travel routes and timing
}