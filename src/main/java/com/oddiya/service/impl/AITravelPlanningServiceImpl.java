package com.oddiya.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.request.AITravelPlanRequest;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;
import com.oddiya.entity.User;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.AITravelPlanningService;
import com.oddiya.service.TravelPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI Travel Planning Service Implementation
 * Integrates with AWS Bedrock Claude 3 Sonnet for intelligent travel plan generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AITravelPlanningServiceImpl implements AITravelPlanningService {

    private final BedrockRuntimeClient bedrockClient;
    private final UserRepository userRepository;
    private final TravelPlanService travelPlanService;
    private final ObjectMapper objectMapper;

    @Value("${app.aws.bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String modelId;

    @Value("${app.aws.bedrock.max-tokens:4000}")
    private int maxTokens;

    @Value("${app.aws.bedrock.temperature:0.7}")
    private double temperature;

    @Override
    public TravelPlanResponse generateTravelPlan(String userId, AITravelPlanRequest request) {
        log.info("Generating AI travel plan for user {} to destination {}", userId, request.getDestination());
        
        try {
            // Get user for personalization
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            // Build AI prompt with user preferences and request details
            String prompt = buildTravelPlanPrompt(user, request);

            // Call AWS Bedrock
            String aiResponse = invokeBedrockModel(prompt);

            // Parse AI response and create travel plan
            Map<String, Object> planData = parseAIResponse(aiResponse);

            // Convert to travel plan and save
            return createTravelPlanFromAI(userId, request, planData);

        } catch (Exception e) {
            log.error("Error generating AI travel plan for user {}: {}", userId, e.getMessage(), e);
            throw new BadRequestException("Failed to generate travel plan: " + e.getMessage());
        }
    }

    @Override
    public List<TravelPlanSuggestion> getTravelSuggestions(String userId, String destination, Map<String, Object> preferences) {
        log.info("Getting travel suggestions for user {} to destination {}", userId, destination);
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            String prompt = buildSuggestionPrompt(user, destination, preferences);
            String aiResponse = invokeBedrockModel(prompt);

            return parseSuggestionsResponse(aiResponse);

        } catch (Exception e) {
            log.error("Error getting travel suggestions for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public TravelPlanResponse enhanceTravelPlan(String userId, String planId) {
        log.info("Enhancing travel plan {} for user {}", planId, userId);
        
        try {
            // Get existing travel plan
            TravelPlanResponse existingPlan = travelPlanService.getTravelPlan(planId);
            
            // Get user for personalization
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            // Build enhancement prompt
            String prompt = buildEnhancementPrompt(user, existingPlan);
            String aiResponse = invokeBedrockModel(prompt);

            // Parse and apply enhancements
            Map<String, Object> enhancements = parseAIResponse(aiResponse);
            
            // Apply enhancements to existing plan
            return applyEnhancements(planId, enhancements);

        } catch (Exception e) {
            log.error("Error enhancing travel plan {} for user {}: {}", planId, userId, e.getMessage(), e);
            throw new BadRequestException("Failed to enhance travel plan: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getPlaceRecommendations(String userId, String destination, String category, Map<String, Object> preferences) {
        log.info("Getting place recommendations for user {} in {} for category {}", userId, destination, category);
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            String prompt = buildPlaceRecommendationPrompt(user, destination, category, preferences);
            String aiResponse = invokeBedrockModel(prompt);

            return parsePlaceRecommendations(aiResponse);

        } catch (Exception e) {
            log.error("Error getting place recommendations for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> optimizeItinerary(String userId, List<Map<String, Object>> places, String startDate, String endDate) {
        log.info("Optimizing itinerary for user {} with {} places", userId, places.size());
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            String prompt = buildItineraryOptimizationPrompt(user, places, startDate, endDate);
            String aiResponse = invokeBedrockModel(prompt);

            return parseItineraryOptimization(aiResponse);

        } catch (Exception e) {
            log.error("Error optimizing itinerary for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getTravelInsights(String destination, Map<String, String> travelDates, String travelStyle) {
        log.info("Getting travel insights for destination {} with style {}", destination, travelStyle);
        
        try {
            String prompt = buildTravelInsightsPrompt(destination, travelDates, travelStyle);
            String aiResponse = invokeBedrockModel(prompt);

            return parseTravelInsights(aiResponse);

        } catch (Exception e) {
            log.error("Error getting travel insights for destination {}: {}", destination, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getServiceHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test a simple AI call
            String testPrompt = "Respond with 'OK' to confirm the service is working.";
            String response = invokeBedrockModel(testPrompt);
            
            health.put("status", "healthy");
            health.put("modelId", modelId);
            health.put("responseReceived", response != null && !response.trim().isEmpty());
            health.put("lastChecked", new Date());
            
        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("lastChecked", new Date());
            log.error("AI service health check failed: {}", e.getMessage());
        }
        
        return health;
    }

    private String invokeBedrockModel(String prompt) {
        try {
            // Build request payload for Claude 3 Sonnet
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            requestBody.put("messages", messages);

            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromUtf8String(jsonPayload))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();
            
            // Parse Claude response
            JsonNode responseJson = objectMapper.readTree(responseBody);
            return responseJson.path("content").get(0).path("text").asText();
            
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON for Bedrock request: {}", e.getMessage());
            throw new BadRequestException("Error processing AI request");
        } catch (Exception e) {
            log.error("Error invoking Bedrock model: {}", e.getMessage());
            throw new BadRequestException("AI service is currently unavailable");
        }
    }

    private String buildTravelPlanPrompt(User user, AITravelPlanRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert travel planner. Create a detailed travel plan with the following requirements:\n\n");
        prompt.append("Destination: ").append(request.getDestination()).append("\n");
        prompt.append("Travel Dates: ").append(request.getStartDate()).append(" to ").append(request.getEndDate()).append("\n");
        prompt.append("Number of Days: ").append(calculateDays(request.getStartDate(), request.getEndDate())).append("\n");
        prompt.append("Budget Range: ").append(request.getBudgetRange()).append("\n");
        prompt.append("Travel Style: ").append(String.join(", ", request.getInterests())).append("\n\n");

        // Add user preferences if available
        if (user.getTravelPreferences() != null && !user.getTravelPreferences().isEmpty()) {
            prompt.append("User Preferences:\n");
            user.getTravelPreferences().forEach((key, value) -> 
                prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }

        prompt.append("Please provide a comprehensive travel plan in JSON format with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"title\": \"Trip title\",\n");
        prompt.append("  \"description\": \"Brief description\",\n");
        prompt.append("  \"estimatedBudget\": \"Total estimated budget\",\n");
        prompt.append("  \"days\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"dayNumber\": 1,\n");
        prompt.append("      \"date\": \"YYYY-MM-DD\",\n");
        prompt.append("      \"places\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"name\": \"Place name\",\n");
        prompt.append("          \"category\": \"restaurant/attraction/hotel/etc\",\n");
        prompt.append("          \"description\": \"Why visit this place\",\n");
        prompt.append("          \"estimatedTime\": \"Duration in minutes\",\n");
        prompt.append("          \"estimatedCost\": \"Cost in KRW\",\n");
        prompt.append("          \"startTime\": \"HH:MM\",\n");
        prompt.append("          \"endTime\": \"HH:MM\",\n");
        prompt.append("          \"notes\": \"Additional notes\"\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"tips\": [\"Travel tips and recommendations\"]\n");
        prompt.append("}\n\n");
        prompt.append("Focus on popular and highly-rated places in ").append(request.getDestination())
               .append(". Include local cuisine, must-see attractions, and cultural experiences.");

        return prompt.toString();
    }

    private String buildSuggestionPrompt(User user, String destination, Map<String, Object> preferences) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Provide 5 travel plan suggestions for ").append(destination).append(" in JSON format:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"title\": \"Trip title\",\n");
        prompt.append("    \"description\": \"Brief description\",\n");
        prompt.append("    \"duration\": \"Number of days\",\n");
        prompt.append("    \"budgetRange\": \"budget/mid-range/luxury\",\n");
        prompt.append("    \"highlights\": [\"Key highlights\"]\n");
        prompt.append("  }\n");
        prompt.append("]\n");

        return prompt.toString();
    }

    private String buildEnhancementPrompt(User user, TravelPlanResponse existingPlan) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Enhance this existing travel plan with additional recommendations:\n\n");
        prompt.append("Current Plan: ").append(existingPlan.getTitle()).append("\n");
        prompt.append("Destination: ").append(existingPlan.getDestination()).append("\n");
        prompt.append("Dates: ").append(existingPlan.getStartDate()).append(" to ").append(existingPlan.getEndDate()).append("\n\n");
        
        prompt.append("Provide enhancement suggestions in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"additionalPlaces\": [],\n");
        prompt.append("  \"improvements\": [],\n");
        prompt.append("  \"localTips\": []\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private String buildPlaceRecommendationPrompt(User user, String destination, String category, Map<String, Object> preferences) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Recommend top ").append(category).append(" places in ").append(destination).append(" in JSON format:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"name\": \"Place name\",\n");
        prompt.append("    \"description\": \"Why recommend this place\",\n");
        prompt.append("    \"category\": \"").append(category).append("\",\n");
        prompt.append("    \"estimatedCost\": \"Cost range\",\n");
        prompt.append("    \"rating\": \"Expected rating out of 5\"\n");
        prompt.append("  }\n");
        prompt.append("]\n");

        return prompt.toString();
    }

    private String buildItineraryOptimizationPrompt(User user, List<Map<String, Object>> places, String startDate, String endDate) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Optimize the itinerary for these places from ").append(startDate).append(" to ").append(endDate).append(":\n\n");
        places.forEach(place -> {
            prompt.append("- ").append(place.get("name")).append(" (").append(place.get("category")).append(")\n");
        });
        
        prompt.append("\nProvide optimized itinerary in JSON format with day-by-day schedule.\n");

        return prompt.toString();
    }

    private String buildTravelInsightsPrompt(String destination, Map<String, String> travelDates, String travelStyle) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Provide travel insights for ").append(destination).append(" in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"bestTimeToVisit\": \"Seasonal information\",\n");
        prompt.append("  \"weather\": \"Weather information\",\n");
        prompt.append("  \"culturalTips\": [],\n");
        prompt.append("  \"transportation\": \"Transportation options\",\n");
        prompt.append("  \"budgetTips\": []\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private long calculateDays(LocalDate startDate, LocalDate endDate) {
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private Map<String, Object> parseAIResponse(String aiResponse) {
        try {
            // Extract JSON from AI response
            String jsonPart = extractJsonFromResponse(aiResponse);
            return objectMapper.readValue(jsonPart, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error parsing AI response: {}", e.getMessage());
            throw new BadRequestException("Failed to parse AI response");
        }
    }

    private String extractJsonFromResponse(String response) {
        // Find JSON content between braces
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        throw new BadRequestException("No valid JSON found in AI response");
    }

    private List<TravelPlanSuggestion> parseSuggestionsResponse(String aiResponse) {
        try {
            String jsonPart = extractJsonFromResponse(aiResponse);
            List<Map<String, Object>> suggestions = objectMapper.readValue(jsonPart, List.class);
            
            return suggestions.stream()
                    .map(this::mapToTravelPlanSuggestion)
                    .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);
                    
        } catch (Exception e) {
            log.error("Error parsing suggestions response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parsePlaceRecommendations(String aiResponse) {
        try {
            String jsonPart = extractJsonFromResponse(aiResponse);
            return objectMapper.readValue(jsonPart, List.class);
        } catch (Exception e) {
            log.error("Error parsing place recommendations: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseItineraryOptimization(String aiResponse) {
        try {
            String jsonPart = extractJsonFromResponse(aiResponse);
            return objectMapper.readValue(jsonPart, Map.class);
        } catch (Exception e) {
            log.error("Error parsing itinerary optimization: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseTravelInsights(String aiResponse) {
        try {
            String jsonPart = extractJsonFromResponse(aiResponse);
            return objectMapper.readValue(jsonPart, Map.class);
        } catch (Exception e) {
            log.error("Error parsing travel insights: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private TravelPlanSuggestion mapToTravelPlanSuggestion(Map<String, Object> suggestionData) {
        return TravelPlanSuggestion.builder()
                .title((String) suggestionData.get("title"))
                .description((String) suggestionData.get("description"))
                .duration((String) suggestionData.get("duration"))
                .budgetRange((String) suggestionData.get("budgetRange"))
                .highlights((List<String>) suggestionData.get("highlights"))
                .build();
    }

    private TravelPlanResponse createTravelPlanFromAI(String userId, AITravelPlanRequest request, Map<String, Object> planData) {
        // This is a simplified implementation
        // In a real implementation, you would create the full TravelPlan entity and save it
        // For now, return a basic response
        
        return TravelPlanResponse.builder()
                .title((String) planData.get("title"))
                .description((String) planData.get("description"))
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .aiGenerated(true)
                .userId(userId)
                .build();
    }

    private TravelPlanResponse applyEnhancements(String planId, Map<String, Object> enhancements) {
        // This is a simplified implementation
        // In a real implementation, you would update the existing travel plan with enhancements
        TravelPlanResponse existingPlan = travelPlanService.getTravelPlan(planId);
        
        // Apply enhancements to the plan
        // For now, just return the existing plan
        return existingPlan;
    }
}