package com.oddiya.service;

import com.oddiya.dto.request.RecommendationRequest;
import com.oddiya.dto.response.RecommendationResponse;
import com.oddiya.dto.response.TravelPlanSuggestion;
import com.oddiya.service.impl.MockAIRecommendationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIRecommendationService Tests")
class AIRecommendationServiceTest {

    @InjectMocks
    private MockAIRecommendationServiceImpl aiRecommendationService;

    private RecommendationRequest recommendationRequest;

    @BeforeEach
    void setUp() {
        recommendationRequest = RecommendationRequest.builder()
                .destination("Seoul")
                .interests(Arrays.asList("culture", "food", "history"))
                .budget(2000)
                .duration(5)
                .travelStyle("cultural")
                .season("spring")
                .build();
    }

    @Nested
    @DisplayName("Get Recommendations Tests")
    class GetRecommendationsTests {

        @Test
        @DisplayName("Should successfully generate recommendations")
        void shouldSuccessfullyGenerateRecommendations() {
            // When
            RecommendationResponse response = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getDestination()).isEqualTo("Seoul");
            assertThat(response.getRecommendations()).isNotEmpty();
            assertThat(response.getPlaces()).isNotEmpty();
            assertThat(response.getRestaurants()).isNotEmpty();
            assertThat(response.getTips()).isNotEmpty();
            assertThat(response.getGeneratedAt()).isNotNull();
            assertThat(response.getGeneratedAt()).isBeforeOrEqualTo(new Date());
        }

        @Test
        @DisplayName("Should return expected number of recommendations")
        void shouldReturnExpectedNumberOfRecommendations() {
            // When
            RecommendationResponse response = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            assertThat(response.getRecommendations()).hasSize(5);
            assertThat(response.getPlaces()).hasSize(5);
            assertThat(response.getRestaurants()).hasSize(3);
            assertThat(response.getTips()).hasSize(4);
        }

        @Test
        @DisplayName("Should contain expected recommendation content")
        void shouldContainExpectedRecommendationContent() {
            // When
            RecommendationResponse response = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            assertThat(response.getRecommendations())
                    .contains("Visit the historic palace in the city center")
                    .contains("Try local street food at the night market");

            assertThat(response.getPlaces())
                    .anyMatch(place -> place.contains("Central Palace"))
                    .anyMatch(place -> place.contains("Night Market"));

            assertThat(response.getRestaurants())
                    .anyMatch(restaurant -> restaurant.contains("The Local Kitchen"));

            assertThat(response.getTips())
                    .anyMatch(tip -> tip.contains("Book accommodations"));
        }

        @Test
        @DisplayName("Should handle different destinations")
        void shouldHandleDifferentDestinations() {
            // Given
            recommendationRequest.setDestination("Tokyo");

            // When
            RecommendationResponse response = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            assertThat(response.getDestination()).isEqualTo("Tokyo");
            assertThat(response.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle null destination gracefully")
        void shouldHandleNullDestinationGracefully() {
            // Given
            recommendationRequest.setDestination(null);

            // When & Then
            assertThatCode(() -> aiRecommendationService.getRecommendations(recommendationRequest))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should generate timestamp close to current time")
        void shouldGenerateTimestampCloseToCurrentTime() {
            // Given
            long beforeCall = System.currentTimeMillis();

            // When
            RecommendationResponse response = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            long afterCall = System.currentTimeMillis();
            long generatedTime = response.getGeneratedAt().getTime();
            
            assertThat(generatedTime).isBetween(beforeCall, afterCall);
        }
    }

    @Nested
    @DisplayName("Generate Travel Plan Tests")
    class GenerateTravelPlanTests {

        @Test
        @DisplayName("Should successfully generate travel plan")
        void shouldSuccessfullyGenerateTravelPlan() {
            // Given
            String destination = "Seoul";
            int days = 3;
            List<String> preferences = Arrays.asList("culture", "food");

            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan(destination, days, preferences);

            // Then
            assertThat(suggestion).isNotNull();
            assertThat(suggestion.getTitle()).contains("3-Day Seoul Adventure");
            assertThat(suggestion.getDestination()).isEqualTo("Seoul");
            assertThat(suggestion.getDuration()).isEqualTo(3);
            assertThat(suggestion.getDescription()).contains("culture, food");
        }

        @Test
        @DisplayName("Should generate daily itinerary for all days")
        void shouldGenerateDailyItineraryForAllDays() {
            // Given
            int days = 5;

            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", days, Arrays.asList("culture"));

            // Then
            assertThat(suggestion.getDailyItinerary()).hasSize(5);
            
            for (int i = 0; i < days; i++) {
                TravelPlanSuggestion.DayPlan dayPlan = suggestion.getDailyItinerary().get(i);
                assertThat(dayPlan.getDay()).isEqualTo(i + 1);
                assertThat(dayPlan.getTitle()).contains("Day " + (i + 1));
                assertThat(dayPlan.getMorning()).isNotEmpty();
                assertThat(dayPlan.getAfternoon()).isNotEmpty();
                assertThat(dayPlan.getEvening()).isNotEmpty();
                assertThat(dayPlan.getMeals()).isNotEmpty();
                assertThat(dayPlan.getEstimatedCost()).isNotNull();
            }
        }

        @Test
        @DisplayName("Should include meals for each day")
        void shouldIncludeMealsForEachDay() {
            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", 2, Arrays.asList("food"));

            // Then
            suggestion.getDailyItinerary().forEach(dayPlan -> {
                assertThat(dayPlan.getMeals()).containsKeys("Breakfast", "Lunch", "Dinner");
                assertThat(dayPlan.getMeals().get("Breakfast")).contains("Local Cafe");
                assertThat(dayPlan.getMeals().get("Lunch")).contains("Street Market");
                assertThat(dayPlan.getMeals().get("Dinner")).contains("Restaurant");
            });
        }

        @Test
        @DisplayName("Should calculate estimated cost based on duration")
        void shouldCalculateEstimatedCostBasedOnDuration() {
            // Given
            int days = 4;

            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", days, Arrays.asList("culture"));

            // Then
            assertThat(suggestion.getEstimatedCost()).isEqualTo("$800-1200");
        }

        @Test
        @DisplayName("Should handle single day travel plan")
        void shouldHandleSingleDayTravelPlan() {
            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", 1, Arrays.asList("culture"));

            // Then
            assertThat(suggestion.getDuration()).isEqualTo(1);
            assertThat(suggestion.getDailyItinerary()).hasSize(1);
            assertThat(suggestion.getEstimatedCost()).isEqualTo("$200-300");
        }

        @Test
        @DisplayName("Should handle zero or negative days gracefully")
        void shouldHandleZeroOrNegativeDaysGracefully() {
            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", 0, Arrays.asList("culture"));

            // Then
            assertThat(suggestion).isNotNull();
            assertThat(suggestion.getDailyItinerary()).isEmpty();
            assertThat(suggestion.getEstimatedCost()).isEqualTo("$0-0");
        }

        @Test
        @DisplayName("Should include general travel tips")
        void shouldIncludeGeneralTravelTips() {
            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", 3, Arrays.asList("culture"));

            // Then
            assertThat(suggestion.getTips()).contains(
                    "Book hotels in advance",
                    "Learn basic local phrases",
                    "Keep copies of important documents"
            );
        }

        @Test
        @DisplayName("Should handle empty preferences list")
        void shouldHandleEmptyPreferencesList() {
            // When
            TravelPlanSuggestion suggestion = aiRecommendationService.generateTravelPlan("Seoul", 2, Arrays.asList());

            // Then
            assertThat(suggestion).isNotNull();
            assertThat(suggestion.getDescription()).contains("");
        }
    }

    @Nested
    @DisplayName("Recommend Places Tests")
    class RecommendPlacesTests {

        @Test
        @DisplayName("Should recommend requested number of places")
        void shouldRecommendRequestedNumberOfPlaces() {
            // Given
            String destination = "Seoul";
            String category = "restaurant";
            int count = 5;

            // When
            List<String> places = aiRecommendationService.recommendPlaces(destination, category, count);

            // Then
            assertThat(places).hasSize(5);
            places.forEach(place -> {
                assertThat(place).contains("restaurant");
                assertThat(place).contains("Seoul");
            });
        }

        @Test
        @DisplayName("Should handle different categories")
        void shouldHandleDifferentCategories() {
            // Given
            String[] categories = {"museum", "park", "shopping", "temple"};

            for (String category : categories) {
                // When
                List<String> places = aiRecommendationService.recommendPlaces("Seoul", category, 3);

                // Then
                assertThat(places).hasSize(3);
                places.forEach(place -> assertThat(place).contains(category));
            }
        }

        @Test
        @DisplayName("Should handle zero count")
        void shouldHandleZeroCount() {
            // When
            List<String> places = aiRecommendationService.recommendPlaces("Seoul", "restaurant", 0);

            // Then
            assertThat(places).isEmpty();
        }

        @Test
        @DisplayName("Should handle large count")
        void shouldHandleLargeCount() {
            // When
            List<String> places = aiRecommendationService.recommendPlaces("Seoul", "attraction", 100);

            // Then
            assertThat(places).hasSize(100);
        }

        @Test
        @DisplayName("Should include destination in place descriptions")
        void shouldIncludeDestinationInPlaceDescriptions() {
            // When
            List<String> places = aiRecommendationService.recommendPlaces("Tokyo", "restaurant", 3);

            // Then
            places.forEach(place -> assertThat(place).contains("Tokyo"));
        }
    }

    @Nested
    @DisplayName("Generate Itinerary Tests")
    class GenerateItineraryTests {

        @Test
        @DisplayName("Should generate itinerary with all components")
        void shouldGenerateItineraryWithAllComponents() {
            // Given
            String destination = "Seoul";
            int days = 3;
            String budget = "$2000";
            List<String> interests = Arrays.asList("culture", "food");

            // When
            String itinerary = aiRecommendationService.generateItinerary(destination, days, budget, interests);

            // Then
            assertThat(itinerary).isNotNull().isNotEmpty();
            assertThat(itinerary).contains("3-Day Itinerary for Seoul");
            assertThat(itinerary).contains("Budget: $2000");
            assertThat(itinerary).contains("Interests: culture, food");
        }

        @Test
        @DisplayName("Should include daily breakdown")
        void shouldIncludeDailyBreakdown() {
            // When
            String itinerary = aiRecommendationService.generateItinerary("Seoul", 4, "$1500", Arrays.asList("culture"));

            // Then
            for (int i = 1; i <= 4; i++) {
                assertThat(itinerary).contains("Day " + i + ":");
                assertThat(itinerary).contains("Morning:");
                assertThat(itinerary).contains("Afternoon:");
                assertThat(itinerary).contains("Evening:");
                assertThat(itinerary).contains("Estimated cost:");
            }
        }

        @Test
        @DisplayName("Should include transportation and tips")
        void shouldIncludeTransportationAndTips() {
            // When
            String itinerary = aiRecommendationService.generateItinerary("Seoul", 2, "$1000", Arrays.asList("sightseeing"));

            // Then
            assertThat(itinerary).contains("Transportation:");
            assertThat(itinerary).contains("Tips:");
        }

        @Test
        @DisplayName("Should handle single day itinerary")
        void shouldHandleSingleDayItinerary() {
            // When
            String itinerary = aiRecommendationService.generateItinerary("Seoul", 1, "$500", Arrays.asList("culture"));

            // Then
            assertThat(itinerary).contains("1-Day Itinerary");
            assertThat(itinerary).contains("Day 1:");
        }

        @Test
        @DisplayName("Should handle empty interests list")
        void shouldHandleEmptyInterestsList() {
            // When
            String itinerary = aiRecommendationService.generateItinerary("Seoul", 2, "$1000", Arrays.asList());

            // Then
            assertThat(itinerary).isNotNull();
            assertThat(itinerary).contains("Interests:");
        }

        @Test
        @DisplayName("Should format as readable text")
        void shouldFormatAsReadableText() {
            // When
            String itinerary = aiRecommendationService.generateItinerary("Seoul", 2, "$1000", Arrays.asList("culture"));

            // Then
            assertThat(itinerary).contains("\n"); // Contains line breaks
            assertThat(itinerary).contains("  "); // Contains indentation
        }
    }

    @Nested
    @DisplayName("Suggest Destinations Tests")
    class SuggestDestinationsTests {

        @Test
        @DisplayName("Should suggest destinations based on parameters")
        void shouldSuggestDestinationsBasedOnParameters() {
            // Given
            List<String> preferences = Arrays.asList("culture", "food");
            String season = "spring";
            int budget = 3000;

            // When
            List<String> destinations = aiRecommendationService.suggestDestinations(preferences, season, budget);

            // Then
            assertThat(destinations).isNotEmpty();
            assertThat(destinations).hasSize(5);
            destinations.forEach(destination -> {
                assertThat(destination).contains(season);
                assertThat(destination).contains("mid-range");
            });
        }

        @Test
        @DisplayName("Should categorize budget levels correctly")
        void shouldCategorizeBudgetLevelsCorrectly() {
            // Test budget budget
            List<String> budgetDestinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "summer", 1500);
            budgetDestinations.forEach(dest -> assertThat(dest).contains("budget"));

            // Test mid-range budget
            List<String> midRangeDestinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "summer", 3500);
            midRangeDestinations.forEach(dest -> assertThat(dest).contains("mid-range"));

            // Test luxury budget
            List<String> luxuryDestinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "summer", 8000);
            luxuryDestinations.forEach(dest -> assertThat(dest).contains("luxury"));
        }

        @Test
        @DisplayName("Should include expected destinations")
        void shouldIncludeExpectedDestinations() {
            // When
            List<String> destinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "winter", 2500);

            // Then
            assertThat(destinations)
                    .anyMatch(dest -> dest.contains("Tokyo, Japan"))
                    .anyMatch(dest -> dest.contains("Seoul, South Korea"))
                    .anyMatch(dest -> dest.contains("Bangkok, Thailand"))
                    .anyMatch(dest -> dest.contains("Singapore"))
                    .anyMatch(dest -> dest.contains("Bali, Indonesia"));
        }

        @Test
        @DisplayName("Should handle different seasons")
        void shouldHandleDifferentSeasons() {
            // Given
            String[] seasons = {"spring", "summer", "autumn", "winter"};

            for (String season : seasons) {
                // When
                List<String> destinations = aiRecommendationService.suggestDestinations(
                        Arrays.asList("culture"), season, 2500);

                // Then
                destinations.forEach(dest -> assertThat(dest).contains(season));
            }
        }

        @Test
        @DisplayName("Should handle empty preferences")
        void shouldHandleEmptyPreferences() {
            // When
            List<String> destinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList(), "spring", 2500);

            // Then
            assertThat(destinations).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle null preferences")
        void shouldHandleNullPreferences() {
            // When & Then
            assertThatCode(() -> aiRecommendationService.suggestDestinations(null, "spring", 2500))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle edge budget values")
        void shouldHandleEdgeBudgetValues() {
            // Test exactly at boundary
            List<String> exactBudgetDestinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "spring", 2000);
            exactBudgetDestinations.forEach(dest -> assertThat(dest).contains("budget"));

            List<String> exactMidRangeDestinations = aiRecommendationService.suggestDestinations(
                    Arrays.asList("culture"), "spring", 5000);
            exactMidRangeDestinations.forEach(dest -> assertThat(dest).contains("mid-range"));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should maintain consistent service behavior")
        void shouldMaintainConsistentServiceBehavior() {
            // Given
            RecommendationRequest request = RecommendationRequest.builder()
                    .destination("Tokyo")
                    .interests(Arrays.asList("culture", "technology"))
                    .budget(4000)
                    .duration(7)
                    .build();

            // When
            RecommendationResponse recommendations = aiRecommendationService.getRecommendations(request);
            TravelPlanSuggestion travelPlan = aiRecommendationService.generateTravelPlan("Tokyo", 7, Arrays.asList("culture", "technology"));
            List<String> places = aiRecommendationService.recommendPlaces("Tokyo", "museum", 5);
            String itinerary = aiRecommendationService.generateItinerary("Tokyo", 7, "$4000", Arrays.asList("culture", "technology"));
            List<String> destinations = aiRecommendationService.suggestDestinations(Arrays.asList("culture", "technology"), "spring", 4000);

            // Then
            assertThat(recommendations).isNotNull();
            assertThat(travelPlan).isNotNull();
            assertThat(places).hasSize(5);
            assertThat(itinerary).isNotNull();
            assertThat(destinations).hasSize(5);

            // All should reference the same destination when applicable
            assertThat(recommendations.getDestination()).isEqualTo("Tokyo");
            assertThat(travelPlan.getDestination()).isEqualTo("Tokyo");
            places.forEach(place -> assertThat(place).contains("Tokyo"));
            assertThat(itinerary).contains("Tokyo");
        }

        @Test
        @DisplayName("Should handle concurrent requests")
        void shouldHandleConcurrentRequests() {
            // When - Multiple concurrent calls
            RecommendationResponse response1 = aiRecommendationService.getRecommendations(recommendationRequest);
            RecommendationResponse response2 = aiRecommendationService.getRecommendations(recommendationRequest);

            // Then
            assertThat(response1).isNotNull();
            assertThat(response2).isNotNull();
            assertThat(response1.getDestination()).isEqualTo(response2.getDestination());
            assertThat(response1.getRecommendations()).hasSize(response2.getRecommendations().size());
        }
    }
}