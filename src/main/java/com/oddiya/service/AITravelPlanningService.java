package com.oddiya.service;

import com.oddiya.dto.request.AITravelPlanRequest;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;

import java.util.List;
import java.util.Map;

/**
 * AI Travel Planning Service Interface
 * Integrates with AWS Bedrock Claude 3 Sonnet for intelligent travel plan generation
 */
public interface AITravelPlanningService {
    
    /**
     * Generate a comprehensive AI travel plan using AWS Bedrock
     * 
     * @param userId User ID for personalization
     * @param request Travel plan generation request
     * @return Generated travel plan response
     */
    TravelPlanResponse generateTravelPlan(String userId, AITravelPlanRequest request);
    
    /**
     * Get AI-powered travel suggestions based on user preferences
     * 
     * @param userId User ID for personalization
     * @param destination Target destination
     * @param preferences User travel preferences
     * @return List of travel plan suggestions
     */
    List<TravelPlanSuggestion> getTravelSuggestions(String userId, String destination, Map<String, Object> preferences);
    
    /**
     * Enhance existing travel plan with AI recommendations
     * 
     * @param userId User ID
     * @param planId Travel plan ID
     * @return Enhanced travel plan response
     */
    TravelPlanResponse enhanceTravelPlan(String userId, String planId);
    
    /**
     * Generate personalized place recommendations using AI
     * 
     * @param userId User ID for personalization
     * @param destination Destination city/region
     * @param category Place category (restaurant, attraction, etc.)
     * @param preferences User preferences
     * @return List of place recommendations
     */
    List<Map<String, Object>> getPlaceRecommendations(String userId, String destination, String category, Map<String, Object> preferences);
    
    /**
     * Generate optimal itinerary ordering using AI
     * 
     * @param userId User ID
     * @param places List of selected places
     * @param startDate Trip start date
     * @param endDate Trip end date
     * @return Optimized itinerary with timing and transportation
     */
    Map<String, Object> optimizeItinerary(String userId, List<Map<String, Object>> places, String startDate, String endDate);
    
    /**
     * Get AI-powered travel tips and insights
     * 
     * @param destination Destination
     * @param travelDates Travel dates
     * @param travelStyle Travel style (budget, luxury, adventure, etc.)
     * @return Travel tips and insights
     */
    Map<String, Object> getTravelInsights(String destination, Map<String, String> travelDates, String travelStyle);
    
    /**
     * Check AI service health and model availability
     * 
     * @return Health status
     */
    Map<String, Object> getServiceHealth();
}