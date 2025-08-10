package com.oddiya.controller;

import com.oddiya.dto.request.RecommendationRequest;
import com.oddiya.dto.response.RecommendationResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;
import com.oddiya.service.AIRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Recommendations", description = "AI-powered travel recommendations using LangChain")
public class AIRecommendationController {
    
    private final AIRecommendationService aiRecommendationService;
    
    @PostMapping("/recommendations")
    @Operation(summary = "Get AI recommendations", description = "Generate travel recommendations based on preferences")
    public ResponseEntity<RecommendationResponse> getRecommendations(@Valid @RequestBody RecommendationRequest request) {
        log.info("Generating AI recommendations for: {}", request.getDestination());
        
        RecommendationResponse response = aiRecommendationService.getRecommendations(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/plan")
    @Operation(summary = "Generate travel plan", description = "Generate a detailed AI-powered travel plan")
    public ResponseEntity<TravelPlanSuggestion> generateTravelPlan(
            @RequestParam String destination,
            @RequestParam Integer days,
            @RequestParam(required = false) List<String> preferences) {
        
        log.info("Generating travel plan for {} ({} days)", destination, days);
        
        List<String> prefs = preferences != null ? preferences : List.of("culture", "food", "sightseeing");
        TravelPlanSuggestion plan = aiRecommendationService.generateTravelPlan(destination, days, prefs);
        
        return ResponseEntity.ok(plan);
    }
    
    @GetMapping("/places")
    @Operation(summary = "Recommend places", description = "Get AI recommendations for places to visit")
    public ResponseEntity<List<String>> recommendPlaces(
            @RequestParam String destination,
            @RequestParam(defaultValue = "tourist attractions") String category,
            @RequestParam(defaultValue = "5") Integer count) {
        
        log.info("Recommending {} {} in {}", count, category, destination);
        
        List<String> places = aiRecommendationService.recommendPlaces(destination, category, count);
        return ResponseEntity.ok(places);
    }
    
    @PostMapping("/itinerary")
    @Operation(summary = "Generate itinerary", description = "Generate a detailed travel itinerary")
    public ResponseEntity<Map<String, String>> generateItinerary(
            @RequestParam String destination,
            @RequestParam Integer days,
            @RequestParam String budget,
            @RequestParam List<String> interests) {
        
        log.info("Generating itinerary for {} with budget: {}", destination, budget);
        
        String itinerary = aiRecommendationService.generateItinerary(destination, days, budget, interests);
        
        return ResponseEntity.ok(Map.of(
                "destination", destination,
                "days", String.valueOf(days),
                "budget", budget,
                "itinerary", itinerary
        ));
    }
    
    @GetMapping("/destinations")
    @Operation(summary = "Suggest destinations", description = "Get AI-powered destination suggestions")
    public ResponseEntity<List<String>> suggestDestinations(
            @RequestParam List<String> preferences,
            @RequestParam String season,
            @RequestParam Integer budget) {
        
        log.info("Suggesting destinations for {} season with budget: ${}", season, budget);
        
        List<String> destinations = aiRecommendationService.suggestDestinations(preferences, season, budget);
        return ResponseEntity.ok(destinations);
    }
}