package com.oddiya.service;

import com.oddiya.dto.request.RecommendationRequest;
import com.oddiya.dto.response.RecommendationResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;

import java.util.List;

public interface AIRecommendationService {
    
    /**
     * Generate travel recommendations based on user preferences
     */
    RecommendationResponse getRecommendations(RecommendationRequest request);
    
    /**
     * Generate a detailed travel plan using AI
     */
    TravelPlanSuggestion generateTravelPlan(String destination, int days, List<String> preferences);
    
    /**
     * Get personalized place recommendations
     */
    List<String> recommendPlaces(String destination, String category, int count);
    
    /**
     * Generate a travel itinerary
     */
    String generateItinerary(String destination, int days, String budget, List<String> interests);
    
    /**
     * Analyze user preferences and suggest destinations
     */
    List<String> suggestDestinations(List<String> preferences, String season, int budget);
}