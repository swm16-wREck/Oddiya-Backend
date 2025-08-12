package com.oddiya.controller;

import com.oddiya.dto.request.AITravelPlanRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;
import com.oddiya.service.AITravelPlanningService;
import com.oddiya.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Travel Planning Controller
 * Handles AI-powered travel plan generation using AWS Bedrock Claude 3 Sonnet
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Travel Planning", description = "AI-powered travel planning endpoints")
public class AITravelPlanController {

    private final AITravelPlanningService aiTravelPlanningService;
    private final JwtService jwtService;

    @PostMapping("/travel-plans/generate")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Generate AI travel plan", description = "Generate a comprehensive travel plan using AI")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Travel plan generated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    public ResponseEntity<ApiResponse<TravelPlanResponse>> generateTravelPlan(
            @Valid @RequestBody AITravelPlanRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("AI travel plan generation request for destination: {}", request.getDestination());
        
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            TravelPlanResponse travelPlan = aiTravelPlanningService.generateTravelPlan(userId, request);
            
            ApiResponse<TravelPlanResponse> response = ApiResponse.<TravelPlanResponse>builder()
                    .success(true)
                    .message("AI travel plan generated successfully")
                    .data(travelPlan)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating AI travel plan: {}", e.getMessage(), e);
            
            ApiResponse<TravelPlanResponse> errorResponse = ApiResponse.<TravelPlanResponse>builder()
                    .success(false)
                    .message("Failed to generate travel plan: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/travel-plans/suggestions")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get travel suggestions", description = "Get AI-powered travel plan suggestions")
    public ResponseEntity<ApiResponse<List<TravelPlanSuggestion>>> getTravelSuggestions(
            @Parameter(description = "Destination") @RequestParam String destination,
            @Parameter(description = "Budget range") @RequestParam(required = false) String budgetRange,
            @Parameter(description = "Travel interests") @RequestParam(required = false) List<String> interests,
            HttpServletRequest httpRequest) {
        
        log.info("Getting travel suggestions for destination: {}", destination);
        
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            
            Map<String, Object> preferences = Map.of(
                    "budgetRange", budgetRange != null ? budgetRange : "mid-range",
                    "interests", interests != null ? interests : List.of("culture", "food")
            );
            
            List<TravelPlanSuggestion> suggestions = aiTravelPlanningService.getTravelSuggestions(userId, destination, preferences);
            
            ApiResponse<List<TravelPlanSuggestion>> response = ApiResponse.<List<TravelPlanSuggestion>>builder()
                    .success(true)
                    .message("Travel suggestions retrieved successfully")
                    .data(suggestions)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting travel suggestions: {}", e.getMessage(), e);
            
            ApiResponse<List<TravelPlanSuggestion>> errorResponse = ApiResponse.<List<TravelPlanSuggestion>>builder()
                    .success(false)
                    .message("Failed to get travel suggestions: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @PostMapping("/travel-plans/{planId}/enhance")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Enhance travel plan", description = "Enhance existing travel plan with AI recommendations")
    public ResponseEntity<ApiResponse<TravelPlanResponse>> enhanceTravelPlan(
            @Parameter(description = "Travel plan ID") @PathVariable String planId,
            HttpServletRequest httpRequest) {
        
        log.info("Enhancing travel plan: {}", planId);
        
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            TravelPlanResponse enhancedPlan = aiTravelPlanningService.enhanceTravelPlan(userId, planId);
            
            ApiResponse<TravelPlanResponse> response = ApiResponse.<TravelPlanResponse>builder()
                    .success(true)
                    .message("Travel plan enhanced successfully")
                    .data(enhancedPlan)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error enhancing travel plan {}: {}", planId, e.getMessage(), e);
            
            ApiResponse<TravelPlanResponse> errorResponse = ApiResponse.<TravelPlanResponse>builder()
                    .success(false)
                    .message("Failed to enhance travel plan: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/places/recommendations")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get place recommendations", description = "Get AI-powered place recommendations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPlaceRecommendations(
            @Parameter(description = "Destination") @RequestParam String destination,
            @Parameter(description = "Place category") @RequestParam String category,
            @Parameter(description = "Budget range") @RequestParam(required = false) String budgetRange,
            @Parameter(description = "Interests") @RequestParam(required = false) List<String> interests,
            HttpServletRequest httpRequest) {
        
        log.info("Getting place recommendations for {} in {}", category, destination);
        
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            
            Map<String, Object> preferences = Map.of(
                    "budgetRange", budgetRange != null ? budgetRange : "mid-range",
                    "interests", interests != null ? interests : List.of(),
                    "category", category
            );
            
            List<Map<String, Object>> recommendations = aiTravelPlanningService.getPlaceRecommendations(
                    userId, destination, category, preferences);
            
            ApiResponse<List<Map<String, Object>>> response = ApiResponse.<List<Map<String, Object>>>builder()
                    .success(true)
                    .message("Place recommendations retrieved successfully")
                    .data(recommendations)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting place recommendations: {}", e.getMessage(), e);
            
            ApiResponse<List<Map<String, Object>>> errorResponse = ApiResponse.<List<Map<String, Object>>>builder()
                    .success(false)
                    .message("Failed to get place recommendations: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @PostMapping("/itinerary/optimize")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Optimize itinerary", description = "Optimize itinerary with AI for better travel routes and timing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> optimizeItinerary(
            @Parameter(description = "Selected places") @RequestBody List<Map<String, Object>> places,
            @Parameter(description = "Start date") @RequestParam String startDate,
            @Parameter(description = "End date") @RequestParam String endDate,
            HttpServletRequest httpRequest) {
        
        log.info("Optimizing itinerary with {} places from {} to {}", places.size(), startDate, endDate);
        
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            Map<String, Object> optimizedItinerary = aiTravelPlanningService.optimizeItinerary(
                    userId, places, startDate, endDate);
            
            ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("Itinerary optimized successfully")
                    .data(optimizedItinerary)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error optimizing itinerary: {}", e.getMessage(), e);
            
            ApiResponse<Map<String, Object>> errorResponse = ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to optimize itinerary: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/insights/travel")
    @Operation(summary = "Get travel insights", description = "Get AI-powered travel insights and tips")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTravelInsights(
            @Parameter(description = "Destination") @RequestParam String destination,
            @Parameter(description = "Start date") @RequestParam String startDate,
            @Parameter(description = "End date") @RequestParam String endDate,
            @Parameter(description = "Travel style") @RequestParam(required = false) String travelStyle) {
        
        log.info("Getting travel insights for {} from {} to {}", destination, startDate, endDate);
        
        try {
            Map<String, String> travelDates = Map.of(
                    "startDate", startDate,
                    "endDate", endDate
            );
            
            Map<String, Object> insights = aiTravelPlanningService.getTravelInsights(
                    destination, travelDates, travelStyle != null ? travelStyle : "balanced");
            
            ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("Travel insights retrieved successfully")
                    .data(insights)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting travel insights: {}", e.getMessage(), e);
            
            ApiResponse<Map<String, Object>> errorResponse = ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to get travel insights: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/health")
    @Operation(summary = "AI service health", description = "Check AI service health status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getServiceHealth() {
        
        try {
            Map<String, Object> health = aiTravelPlanningService.getServiceHealth();
            
            ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("AI service health check completed")
                    .data(health)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking AI service health: {}", e.getMessage(), e);
            
            ApiResponse<Map<String, Object>> errorResponse = ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("AI service health check failed")
                    .data(Map.of("status", "unhealthy", "error", e.getMessage()))
                    .build();
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }
}