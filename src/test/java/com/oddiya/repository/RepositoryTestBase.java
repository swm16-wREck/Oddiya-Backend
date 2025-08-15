package com.oddiya.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.oddiya.entity.User;
import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

/**
 * Base class for repository tests providing common test data and utilities.
 * Uses @DataJpaTest for lightweight JPA testing with PostgreSQL TestContainers.
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.format_sql=false"
})
public abstract class RepositoryTestBase {

    @Autowired
    protected TestEntityManager entityManager;

    protected User testUser1;
    protected User testUser2;
    protected User testUser3;
    protected Place testPlace1;
    protected Place testPlace2;
    protected Place testPlace3;
    protected TravelPlan testTravelPlan1;
    protected TravelPlan testTravelPlan2;

    @BeforeEach
    void setUpBaseTestData() {
        // Create test users
        testUser1 = User.builder()
            .email("test1@oddiya.com")
            .username("testuser1")
            .nickname("Test User 1")
            .provider("google")
            .providerId("google-123")
            .bio("Test bio for user 1")
            .isActive(true)
            .isDeleted(false)
            .preferences(Map.of(
                "language", "ko",
                "theme", "light"
            ))
            .travelPreferences(Map.of(
                "budget", "medium",
                "style", "adventure"
            ))
            .build();

        testUser2 = User.builder()
            .email("test2@oddiya.com")
            .username("testuser2")
            .nickname("Test User 2")
            .provider("apple")
            .providerId("apple-456")
            .bio("Test bio for user 2")
            .isActive(true)
            .isDeleted(false)
            .preferences(Map.of(
                "language", "en",
                "theme", "dark"
            ))
            .build();

        testUser3 = User.builder()
            .email("test3@oddiya.com")
            .username("testuser3")
            .nickname("Test User 3")
            .provider("google")
            .providerId("google-789")
            .bio("Inactive user for testing")
            .isActive(false)
            .isDeleted(false)
            .build();

        // Create test places
        testPlace1 = Place.builder()
            .naverPlaceId("naver-place-1")
            .name("Seoul Tower")
            .category("tourist_attraction")
            .description("Famous landmark in Seoul")
            .address("105 Namsangongwon-gil, Yongsan-gu, Seoul")
            .roadAddress("105 Namsangongwon-gil, Yongsan-gu, Seoul")
            .latitude(37.5512)
            .longitude(126.9882)
            .phoneNumber("02-3455-9277")
            .website("http://www.seoultower.co.kr")
            .rating(4.5)
            .reviewCount(1000)
            .bookmarkCount(500)
            .isVerified(true)
            .popularityScore(95.0)
            .isDeleted(false)
            .tags(List.of("landmark", "view", "romantic"))
            .build();

        testPlace2 = Place.builder()
            .naverPlaceId("naver-place-2")
            .name("Busan Beach")
            .category("beach")
            .description("Beautiful beach in Busan")
            .address("Haeundae-gu, Busan")
            .roadAddress("Haeundae Beach-ro, Haeundae-gu, Busan")
            .latitude(35.1595)
            .longitude(129.1604)
            .rating(4.2)
            .reviewCount(800)
            .bookmarkCount(300)
            .isVerified(true)
            .popularityScore(85.0)
            .isDeleted(false)
            .tags(List.of("beach", "swimming", "summer"))
            .build();

        testPlace3 = Place.builder()
            .naverPlaceId("naver-place-3")
            .name("Deleted Place")
            .category("restaurant")
            .description("This place is deleted")
            .address("Somewhere")
            .latitude(37.5665)
            .longitude(126.9780)
            .rating(3.0)
            .reviewCount(10)
            .bookmarkCount(5)
            .isVerified(false)
            .popularityScore(20.0)
            .isDeleted(true)
            .build();

        // Persist entities to get IDs
        testUser1 = entityManager.persistAndFlush(testUser1);
        testUser2 = entityManager.persistAndFlush(testUser2);
        testUser3 = entityManager.persistAndFlush(testUser3);
        testPlace1 = entityManager.persistAndFlush(testPlace1);
        testPlace2 = entityManager.persistAndFlush(testPlace2);
        testPlace3 = entityManager.persistAndFlush(testPlace3);

        // Create test travel plans
        testTravelPlan1 = TravelPlan.builder()
            .user(testUser1)
            .title("Seoul Adventure")
            .description("Exploring Seoul landmarks")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(10))
            .endDate(LocalDate.now().plusDays(15))
            .numberOfPeople(2)
            .budget(new BigDecimal("1000000"))
            .status(TravelPlanStatus.DRAFT)
            .isPublic(true)
            .isAiGenerated(false)
            .isDeleted(false)
            .viewCount(50L)
            .likeCount(10L)
            .shareCount(5L)
            .saveCount(8L)
            .preferences(Map.of(
                "transportation", "public",
                "accommodation", "hotel"
            ))
            .tags(List.of("seoul", "adventure", "culture"))
            .build();

        testTravelPlan2 = TravelPlan.builder()
            .user(testUser2)
            .title("Private Plan")
            .description("A private travel plan")
            .destination("Busan")
            .startDate(LocalDate.now().plusDays(20))
            .endDate(LocalDate.now().plusDays(25))
            .numberOfPeople(1)
            .budget(new BigDecimal("500000"))
            .status(TravelPlanStatus.CONFIRMED)
            .isPublic(false)
            .isAiGenerated(true)
            .isDeleted(false)
            .viewCount(100L)
            .likeCount(25L)
            .shareCount(12L)
            .saveCount(15L)
            .build();

        testTravelPlan1 = entityManager.persistAndFlush(testTravelPlan1);
        testTravelPlan2 = entityManager.persistAndFlush(testTravelPlan2);

        // Clear the persistence context
        entityManager.clear();
    }

    /**
     * Helper method to create and persist a test user with custom values
     */
    protected User createTestUser(String email, String nickname, String provider, String providerId, boolean isActive) {
        User user = User.builder()
            .email(email)
            .nickname(nickname)
            .provider(provider)
            .providerId(providerId)
            .isActive(isActive)
            .isDeleted(false)
            .build();
        return entityManager.persistAndFlush(user);
    }

    /**
     * Helper method to create and persist a test place with custom values
     */
    protected Place createTestPlace(String naverPlaceId, String name, String category, 
                                  Double latitude, Double longitude, boolean isDeleted) {
        Place place = Place.builder()
            .naverPlaceId(naverPlaceId)
            .name(name)
            .category(category)
            .address("Test Address")
            .latitude(latitude)
            .longitude(longitude)
            .rating(4.0)
            .reviewCount(100)
            .bookmarkCount(50)
            .isVerified(true)
            .popularityScore(75.0)
            .isDeleted(isDeleted)
            .build();
        return entityManager.persistAndFlush(place);
    }

    /**
     * Helper method to create and persist a test travel plan with custom values
     */
    protected TravelPlan createTestTravelPlan(User user, String title, String destination,
                                            LocalDate startDate, LocalDate endDate,
                                            TravelPlanStatus status, boolean isPublic, boolean isDeleted) {
        TravelPlan plan = TravelPlan.builder()
            .user(user)
            .title(title)
            .destination(destination)
            .startDate(startDate)
            .endDate(endDate)
            .status(status)
            .isPublic(isPublic)
            .isDeleted(isDeleted)
            .numberOfPeople(2)
            .budget(new BigDecimal("1000000"))
            .viewCount(10L)
            .likeCount(5L)
            .shareCount(2L)
            .saveCount(3L)
            .build();
        return entityManager.persistAndFlush(plan);
    }
}