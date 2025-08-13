package com.oddiya.service.impl;

import com.oddiya.dto.request.RecommendationRequest;
import com.oddiya.dto.response.RecommendationResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;
import com.oddiya.service.AIRecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@Primary
public class MockAIRecommendationServiceImpl implements AIRecommendationService {
    
    @Override
    public RecommendationResponse getRecommendations(RecommendationRequest request) {
        log.info("Generating mock recommendations for: {}", request.getDestination());
        
        List<String> recommendations = Arrays.asList(
            "Visit the historic palace in the city center",
            "Try local street food at the night market",
            "Take a guided walking tour of the old town",
            "Visit the famous museum of modern art",
            "Enjoy a sunset view from the observation deck"
        );
        
        List<String> places = Arrays.asList(
            "Central Palace - Historic landmark from 15th century",
            "Night Market - Best local food experience",
            "Old Town Square - Cultural heritage site",
            "Modern Art Museum - Contemporary exhibitions",
            "Sky Tower - 360-degree city views"
        );
        
        List<String> restaurants = Arrays.asList(
            "The Local Kitchen - Traditional cuisine",
            "Street Food Alley - Authentic local dishes",
            "Rooftop Restaurant - Fine dining with views"
        );
        
        List<String> tips = Arrays.asList(
            "Book accommodations in advance for better rates",
            "Use public transportation to save money",
            "Try local food markets for authentic experiences",
            "Visit major attractions early morning to avoid crowds"
        );
        
        return RecommendationResponse.builder()
                .destination(request.getDestination())
                .recommendations(recommendations)
                .places(places)
                .restaurants(restaurants)
                .tips(tips)
                .generatedAt(new Date())
                .build();
    }
    
    @Override
    public TravelPlanSuggestion generateTravelPlan(String destination, int days, List<String> preferences) {
        log.info("Generating mock travel plan for {} ({} days)", destination, days);
        
        List<TravelPlanSuggestion.DayPlan> dailyItinerary = new ArrayList<>();
        
        for (int i = 1; i <= days; i++) {
            Map<String, String> meals = new HashMap<>();
            meals.put("Breakfast", "Local Cafe - Traditional breakfast");
            meals.put("Lunch", "Street Market - Local specialties");
            meals.put("Dinner", "Restaurant - Regional cuisine");
            
            TravelPlanSuggestion.DayPlan dayPlan = TravelPlanSuggestion.DayPlan.builder()
                    .day(i)
                    .title("Day " + i + " - Exploring " + destination)
                    .morning(Arrays.asList("Visit historic site", "Walking tour"))
                    .afternoon(Arrays.asList("Museum visit", "Shopping"))
                    .evening(Arrays.asList("Sunset viewpoint", "Local entertainment"))
                    .meals(meals)
                    .estimatedCost("$150-200")
                    .build();
            
            dailyItinerary.add(dayPlan);
        }
        
        return TravelPlanSuggestion.builder()
                .title(days + "-Day " + destination + " Adventure")
                .description("Mock AI-generated travel plan based on your preferences: " + String.join(", ", preferences))
                .destination(destination)
                .duration(days + " days")
                .dailyItinerary(dailyItinerary)
                .estimatedCost("$" + (days * 200) + "-" + (days * 300))
                .tips(Arrays.asList(
                    "Book hotels in advance",
                    "Learn basic local phrases",
                    "Keep copies of important documents"
                ))
                .generatedAt(new Date())
                .build();
    }
    
    @Override
    public List<String> recommendPlaces(String destination, String category, int count) {
        log.info("Recommending mock {} {} places in {}", count, category, destination);
        
        List<String> places = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            places.add(String.format("%s Place %d - Amazing %s experience in %s", 
                    category, i, category.toLowerCase(), destination));
        }
        
        return places;
    }
    
    @Override
    public String generateItinerary(String destination, int days, String budget, List<String> interests) {
        log.info("Generating mock itinerary for {} with budget: {}", destination, budget);
        
        StringBuilder itinerary = new StringBuilder();
        itinerary.append("Mock " + days + "-Day Itinerary for " + destination + "\n");
        itinerary.append("Budget: " + budget + "\n");
        itinerary.append("Interests: " + String.join(", ", interests) + "\n\n");
        
        for (int i = 1; i <= days; i++) {
            itinerary.append("Day " + i + ":\n");
            itinerary.append("  Morning: Explore local attractions\n");
            itinerary.append("  Afternoon: Cultural experiences\n");
            itinerary.append("  Evening: Local cuisine and entertainment\n");
            itinerary.append("  Estimated cost: $150-200\n\n");
        }
        
        itinerary.append("Transportation: Use local public transport\n");
        itinerary.append("Tips: Book in advance, learn local customs\n");
        
        return itinerary.toString();
    }
    
    @Override
    public List<String> suggestDestinations(List<String> preferences, String season, int budget) {
        log.info("Suggesting mock destinations for season: {} with budget: {}", season, budget);
        
        String budgetLevel = budget < 2000 ? "budget" : budget < 5000 ? "mid-range" : "luxury";
        
        return Arrays.asList(
            "Tokyo, Japan - Perfect for " + season + " travel with " + budgetLevel + " options",
            "Seoul, South Korea - Great cultural experiences matching your preferences",
            "Bangkok, Thailand - Amazing food and temples for " + season + " season",
            "Singapore - Modern city with diverse attractions",
            "Bali, Indonesia - Beautiful beaches and cultural sites"
        );
    }
}