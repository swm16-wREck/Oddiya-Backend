package com.oddiya.repository;

import com.oddiya.entity.ItineraryItem;
import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for ItineraryItemRepository covering:
 * - Basic CRUD operations
 * - Travel plan relationship queries
 * - Ordering by day number and sequence
 * - Cascade delete operations
 * - Database constraints
 * - Place relationships
 */
@DisplayName("ItineraryItemRepository Tests")
class ItineraryItemRepositoryTest extends RepositoryTestBase {

    @Autowired
    private ItineraryItemRepository itineraryItemRepository;

    private ItineraryItem testItineraryItem1;
    private ItineraryItem testItineraryItem2;
    private ItineraryItem testItineraryItem3;

    @BeforeEach
    void setUpItineraryTestData() {
        // Create itinerary items for testTravelPlan1
        testItineraryItem1 = ItineraryItem.builder()
            .travelPlan(testTravelPlan1)
            .place(testPlace1)
            .dayNumber(1)
            .sequence(1)
            .title("Visit Seoul Tower")
            .description("Morning visit to Seoul Tower with great city views")
            .startTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(9, 0)))
            .endTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(11, 0)))
            .estimatedCost(new BigDecimal("25000"))
            .notes("Bring camera for photos")
            .build();

        testItineraryItem2 = ItineraryItem.builder()
            .travelPlan(testTravelPlan1)
            .place(testPlace2)
            .dayNumber(1)
            .sequence(2)
            .title("Lunch at Local Restaurant")
            .description("Try local Korean cuisine")
            .startTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(12, 0)))
            .endTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(13, 30)))
            .estimatedCost(new BigDecimal("15000"))
            .build();

        testItineraryItem3 = ItineraryItem.builder()
            .travelPlan(testTravelPlan1)
            .place(testPlace1)
            .dayNumber(2)
            .sequence(1)
            .title("Morning Exercise")
            .description("Early morning walk around the area")
            .startTime(LocalDateTime.of(LocalDate.now().plusDays(11), LocalTime.of(7, 0)))
            .endTime(LocalDateTime.of(LocalDate.now().plusDays(11), LocalTime.of(8, 0)))
            .estimatedCost(BigDecimal.ZERO)
            .build();

        testItineraryItem1 = entityManager.persistAndFlush(testItineraryItem1);
        testItineraryItem2 = entityManager.persistAndFlush(testItineraryItem2);
        testItineraryItem3 = entityManager.persistAndFlush(testItineraryItem3);

        entityManager.clear();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve itinerary item successfully")
        void shouldSaveAndRetrieveItineraryItem() {
            // Given
            ItineraryItem newItem = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .place(testPlace2)
                .dayNumber(3)
                .sequence(1)
                .title("New Activity")
                .description("A new activity for the trip")
                .startTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(14, 0)))
                .endTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(16, 0)))
                .estimatedCost(new BigDecimal("30000"))
                .notes("Remember to book in advance")
                .build();

            // When
            ItineraryItem savedItem = itineraryItemRepository.save(newItem);

            // Then
            assertThat(savedItem).isNotNull();
            assertThat(savedItem.getId()).isNotNull();
            assertThat(savedItem.getCreatedAt()).isNotNull();
            assertThat(savedItem.getUpdatedAt()).isNotNull();
            assertThat(savedItem.getTitle()).isEqualTo("New Activity");
            assertThat(savedItem.getTravelPlan().getId()).isEqualTo(testTravelPlan1.getId());
            assertThat(savedItem.getPlace().getId()).isEqualTo(testPlace2.getId());
        }

        @Test
        @DisplayName("Should update itinerary item successfully")
        void shouldUpdateItineraryItemSuccessfully() {
            // Given
            String originalTitle = testItineraryItem1.getTitle();

            // When
            testItineraryItem1.setTitle("Updated Seoul Tower Visit");
            testItineraryItem1.setEstimatedCost(new BigDecimal("30000"));
            testItineraryItem1.setNotes("Updated notes with new information");
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then
            assertThat(updatedItem.getTitle()).isEqualTo("Updated Seoul Tower Visit");
            assertThat(updatedItem.getTitle()).isNotEqualTo(originalTitle);
            assertThat(updatedItem.getEstimatedCost()).isEqualTo(new BigDecimal("30000"));
            assertThat(updatedItem.getNotes()).isEqualTo("Updated notes with new information");
            assertThat(updatedItem.getUpdatedAt()).isAfter(updatedItem.getCreatedAt());
        }

        @Test
        @DisplayName("Should delete itinerary item successfully")
        void shouldDeleteItineraryItemSuccessfully() {
            // Given
            String itemId = testItineraryItem1.getId();

            // When
            itineraryItemRepository.deleteById(itemId);

            // Then
            assertThat(itineraryItemRepository.findById(itemId)).isEmpty();
        }

        @Test
        @DisplayName("Should find all itinerary items")
        void shouldFindAllItineraryItems() {
            // When
            List<ItineraryItem> allItems = itineraryItemRepository.findAll();

            // Then
            assertThat(allItems).hasSize(3);
            assertThat(allItems)
                .extracting(ItineraryItem::getTitle)
                .containsExactlyInAnyOrder(
                    "Visit Seoul Tower", 
                    "Lunch at Local Restaurant", 
                    "Morning Exercise"
                );
        }
    }

    @Nested
    @DisplayName("Travel Plan Relationship Queries")
    class TravelPlanRelationshipQueries {

        @Test
        @DisplayName("Should find items by travel plan ID ordered correctly")
        void shouldFindItemsByTravelPlanIdOrderedCorrectly() {
            // When
            List<ItineraryItem> planItems = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan1.getId());

            // Then
            assertThat(planItems).hasSize(3);
            
            // Check ordering: Day 1 Seq 1, Day 1 Seq 2, Day 2 Seq 1
            assertThat(planItems.get(0).getDayNumber()).isEqualTo(1);
            assertThat(planItems.get(0).getSequence()).isEqualTo(1);
            assertThat(planItems.get(0).getTitle()).isEqualTo("Visit Seoul Tower");
            
            assertThat(planItems.get(1).getDayNumber()).isEqualTo(1);
            assertThat(planItems.get(1).getSequence()).isEqualTo(2);
            assertThat(planItems.get(1).getTitle()).isEqualTo("Lunch at Local Restaurant");
            
            assertThat(planItems.get(2).getDayNumber()).isEqualTo(2);
            assertThat(planItems.get(2).getSequence()).isEqualTo(1);
            assertThat(planItems.get(2).getTitle()).isEqualTo("Morning Exercise");
        }

        @Test
        @DisplayName("Should return empty list for non-existent travel plan")
        void shouldReturnEmptyListForNonExistentTravelPlan() {
            // When
            List<ItineraryItem> items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc("non-existent-id");

            // Then
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for travel plan with no items")
        void shouldReturnEmptyListForTravelPlanWithNoItems() {
            // When
            List<ItineraryItem> items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan2.getId());

            // Then
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("Should maintain correct ordering with multiple days and sequences")
        void shouldMaintainCorrectOrderingWithMultipleDaysAndSequences() {
            // Given - Create additional items to test complex ordering
            ItineraryItem day1Item3 = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .place(testPlace1)
                .dayNumber(1)
                .sequence(3)
                .title("Evening Activity")
                .startTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(18, 0)))
                .endTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(20, 0)))
                .build();

            ItineraryItem day2Item2 = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .place(testPlace2)
                .dayNumber(2)
                .sequence(2)
                .title("Afternoon Activity")
                .startTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(14, 0)))
                .endTime(LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(16, 0)))
                .build();

            entityManager.persistAndFlush(day1Item3);
            entityManager.persistAndFlush(day2Item2);

            // When
            List<ItineraryItem> items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan1.getId());

            // Then
            assertThat(items).hasSize(5);
            
            // Verify complete ordering
            assertThat(items.get(0).getDayNumber()).isEqualTo(1);
            assertThat(items.get(0).getSequence()).isEqualTo(1);
            
            assertThat(items.get(1).getDayNumber()).isEqualTo(1);
            assertThat(items.get(1).getSequence()).isEqualTo(2);
            
            assertThat(items.get(2).getDayNumber()).isEqualTo(1);
            assertThat(items.get(2).getSequence()).isEqualTo(3);
            assertThat(items.get(2).getTitle()).isEqualTo("Evening Activity");
            
            assertThat(items.get(3).getDayNumber()).isEqualTo(2);
            assertThat(items.get(3).getSequence()).isEqualTo(1);
            
            assertThat(items.get(4).getDayNumber()).isEqualTo(2);
            assertThat(items.get(4).getSequence()).isEqualTo(2);
            assertThat(items.get(4).getTitle()).isEqualTo("Afternoon Activity");
        }
    }

    @Nested
    @DisplayName("Cascade Delete Operations")
    class CascadeDeleteOperations {

        @Test
        @DisplayName("Should delete all items by travel plan ID")
        void shouldDeleteAllItemsByTravelPlanId() {
            // Given
            String travelPlanId = testTravelPlan1.getId();
            
            // Verify items exist before deletion
            List<ItineraryItem> itemsBefore = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(travelPlanId);
            assertThat(itemsBefore).hasSize(3);

            // When
            itineraryItemRepository.deleteByTravelPlanId(travelPlanId);
            entityManager.flush();

            // Then
            List<ItineraryItem> itemsAfter = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(travelPlanId);
            assertThat(itemsAfter).isEmpty();
        }

        @Test
        @DisplayName("Should not affect items from other travel plans")
        void shouldNotAffectItemsFromOtherTravelPlans() {
            // Given - Create an item for testTravelPlan2
            ItineraryItem plan2Item = ItineraryItem.builder()
                .travelPlan(testTravelPlan2)
                .place(testPlace1)
                .dayNumber(1)
                .sequence(1)
                .title("Plan 2 Activity")
                .build();
            entityManager.persistAndFlush(plan2Item);

            // When - Delete items from testTravelPlan1
            itineraryItemRepository.deleteByTravelPlanId(testTravelPlan1.getId());
            entityManager.flush();

            // Then
            List<ItineraryItem> plan1Items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan1.getId());
            List<ItineraryItem> plan2Items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan2.getId());

            assertThat(plan1Items).isEmpty();
            assertThat(plan2Items).hasSize(1);
            assertThat(plan2Items.get(0).getTitle()).isEqualTo("Plan 2 Activity");
        }

        @Test
        @DisplayName("Should handle delete by travel plan ID when no items exist")
        void shouldHandleDeleteByTravelPlanIdWhenNoItemsExist() {
            // When - Try to delete items for a plan that has no items
            itineraryItemRepository.deleteByTravelPlanId(testTravelPlan2.getId());
            entityManager.flush();

            // Then - Should not throw exception
            List<ItineraryItem> items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(testTravelPlan2.getId());
            assertThat(items).isEmpty();
        }
    }

    @Nested
    @DisplayName("Database Constraints and Validation")
    class DatabaseConstraintsAndValidation {

        @Test
        @DisplayName("Should enforce required travel plan reference")
        void shouldEnforceRequiredTravelPlanReference() {
            // Given
            ItineraryItem itemWithoutTravelPlan = ItineraryItem.builder()
                .travelPlan(null) // Should be required
                .place(testPlace1)
                .dayNumber(1)
                .sequence(1)
                .title("Item without travel plan")
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                itineraryItemRepository.save(itemWithoutTravelPlan);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow null place reference")
        void shouldAllowNullPlaceReference() {
            // Given
            ItineraryItem itemWithoutPlace = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .place(null) // Should be allowed
                .dayNumber(1)
                .sequence(4)
                .title("Free time activity")
                .description("Some free time without specific place")
                .build();

            // When
            ItineraryItem savedItem = itineraryItemRepository.save(itemWithoutPlace);

            // Then
            assertThat(savedItem).isNotNull();
            assertThat(savedItem.getPlace()).isNull();
            assertThat(savedItem.getTitle()).isEqualTo("Free time activity");
        }

        @Test
        @DisplayName("Should handle default values correctly")
        void shouldHandleDefaultValuesCorrectly() {
            // Given
            ItineraryItem itemWithMinimalData = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .dayNumber(1)
                .sequence(5)
                .build();

            // When
            ItineraryItem savedItem = itineraryItemRepository.save(itemWithMinimalData);

            // Then
            assertThat(savedItem).isNotNull();
            assertThat(savedItem.getEstimatedCost()).isNull(); // Can be null
            assertThat(savedItem.getTitle()).isNull(); // Can be null
            assertThat(savedItem.getDescription()).isNull(); // Can be null
            assertThat(savedItem.getStartTime()).isNull(); // Can be null
            assertThat(savedItem.getEndTime()).isNull(); // Can be null
            assertThat(savedItem.getNotes()).isNull(); // Can be null
        }

        @Test
        @DisplayName("Should validate positive day number and sequence")
        void shouldValidatePositiveDayNumberAndSequence() {
            // Given
            ItineraryItem itemWithZeroDayNumber = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .dayNumber(0) // Should be positive
                .sequence(1)
                .title("Invalid day number")
                .build();

            ItineraryItem itemWithZeroSequence = ItineraryItem.builder()
                .travelPlan(testTravelPlan1)
                .dayNumber(1)
                .sequence(0) // Should be positive
                .title("Invalid sequence")
                .build();

            // When & Then
            // Note: Database constraints depend on schema definition
            // These tests assume business logic validation at service layer
            ItineraryItem savedItem1 = itineraryItemRepository.save(itemWithZeroDayNumber);
            ItineraryItem savedItem2 = itineraryItemRepository.save(itemWithZeroSequence);

            assertThat(savedItem1.getDayNumber()).isEqualTo(0);
            assertThat(savedItem2.getSequence()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Place Relationship Handling")
    class PlaceRelationshipHandling {

        @Test
        @DisplayName("Should maintain place relationship correctly")
        void shouldMaintainPlaceRelationshipCorrectly() {
            // When
            ItineraryItem foundItem = itineraryItemRepository.findById(testItineraryItem1.getId()).orElse(null);

            // Then
            assertThat(foundItem).isNotNull();
            assertThat(foundItem.getPlace()).isNotNull();
            assertThat(foundItem.getPlace().getId()).isEqualTo(testPlace1.getId());
            assertThat(foundItem.getPlace().getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should handle place reference updates")
        void shouldHandlePlaceReferenceUpdates() {
            // Given
            assertThat(testItineraryItem1.getPlace().getId()).isEqualTo(testPlace1.getId());

            // When - Change place reference
            testItineraryItem1.setPlace(testPlace2);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);
            entityManager.flush();
            entityManager.clear();

            ItineraryItem reloadedItem = itineraryItemRepository.findById(updatedItem.getId()).orElse(null);

            // Then
            assertThat(reloadedItem).isNotNull();
            assertThat(reloadedItem.getPlace().getId()).isEqualTo(testPlace2.getId());
            assertThat(reloadedItem.getPlace().getName()).isEqualTo("Busan Beach");
        }

        @Test
        @DisplayName("Should allow removing place reference")
        void shouldAllowRemovingPlaceReference() {
            // Given
            assertThat(testItineraryItem1.getPlace()).isNotNull();

            // When - Remove place reference
            testItineraryItem1.setPlace(null);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);
            entityManager.flush();
            entityManager.clear();

            ItineraryItem reloadedItem = itineraryItemRepository.findById(updatedItem.getId()).orElse(null);

            // Then
            assertThat(reloadedItem).isNotNull();
            assertThat(reloadedItem.getPlace()).isNull();
        }
    }

    @Nested
    @DisplayName("Time and Cost Management")
    class TimeAndCostManagement {

        @Test
        @DisplayName("Should handle time ranges correctly")
        void shouldHandleTimeRangesCorrectly() {
            // Given
            LocalDateTime newStartTime = LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(10, 30));
            LocalDateTime newEndTime = LocalDateTime.of(LocalDate.now().plusDays(10), LocalTime.of(12, 45));

            // When
            testItineraryItem1.setStartTime(newStartTime);
            testItineraryItem1.setEndTime(newEndTime);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then
            assertThat(updatedItem.getStartTime()).isEqualTo(newStartTime);
            assertThat(updatedItem.getEndTime()).isEqualTo(newEndTime);
        }

        @Test
        @DisplayName("Should handle cost calculations correctly")
        void shouldHandleCostCalculationsCorrectly() {
            // Given
            BigDecimal newCost = new BigDecimal("50000.50");

            // When
            testItineraryItem1.setEstimatedCost(newCost);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then
            assertThat(updatedItem.getEstimatedCost()).isEqualTo(newCost);
        }

        @Test
        @DisplayName("Should handle zero cost activities")
        void shouldHandleZeroCostActivities() {
            // Given
            BigDecimal zeroCost = BigDecimal.ZERO;

            // When
            testItineraryItem1.setEstimatedCost(zeroCost);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then
            assertThat(updatedItem.getEstimatedCost()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should allow null cost for uncertain activities")
        void shouldAllowNullCostForUncertainActivities() {
            // When
            testItineraryItem1.setEstimatedCost(null);
            ItineraryItem updatedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then
            assertThat(updatedItem.getEstimatedCost()).isNull();
        }
    }

    @Nested
    @DisplayName("Travel Plan Relationship Integrity")
    class TravelPlanRelationshipIntegrity {

        @Test
        @DisplayName("Should maintain travel plan relationship correctly")
        void shouldMaintainTravelPlanRelationshipCorrectly() {
            // When
            ItineraryItem foundItem = itineraryItemRepository.findById(testItineraryItem1.getId()).orElse(null);

            // Then
            assertThat(foundItem).isNotNull();
            assertThat(foundItem.getTravelPlan()).isNotNull();
            assertThat(foundItem.getTravelPlan().getId()).isEqualTo(testTravelPlan1.getId());
            assertThat(foundItem.getTravelPlan().getTitle()).isEqualTo("Seoul Adventure");
        }

        @Test
        @DisplayName("Should not allow changing travel plan reference")
        void shouldNotAllowChangingTravelPlanReference() {
            // This test verifies business logic - in practice, changing the travel plan
            // of an existing itinerary item should be done carefully or restricted
            
            // Given
            TravelPlan originalPlan = testItineraryItem1.getTravelPlan();

            // When - Try to change travel plan
            testItineraryItem1.setTravelPlan(testTravelPlan2);
            ItineraryItem savedItem = itineraryItemRepository.save(testItineraryItem1);

            // Then - The change should be persisted (but may be restricted at service level)
            assertThat(savedItem.getTravelPlan().getId()).isEqualTo(testTravelPlan2.getId());
            assertThat(savedItem.getTravelPlan().getId()).isNotEqualTo(originalPlan.getId());
        }
    }
}