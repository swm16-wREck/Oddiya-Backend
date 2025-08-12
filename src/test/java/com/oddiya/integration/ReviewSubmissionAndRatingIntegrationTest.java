package com.oddiya.integration;

import com.oddiya.dto.request.CreateReviewRequest;
import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.dto.response.ReviewResponse;
import com.oddiya.entity.Place;
import com.oddiya.entity.Review;
import com.oddiya.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for complete review submission and rating calculation workflow.
 * Tests review creation, rating updates, and place rating recalculation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReviewSubmissionAndRatingIntegrationTest extends OddiyaIntegrationTestBase {

    private String accessToken;
    private String userId;
    private User testReviewer;

    @BeforeEach
    void authenticateUser() {
        // Create and authenticate a test reviewer user
        testReviewer = User.builder()
            .email("reviewer@oddiya.com")
            .username("reviewer")
            .nickname("Test Reviewer")
            .provider("google")
            .providerId("google-reviewer-123")
            .bio("Test reviewer for integration testing")
            .isActive(true)
            .isEmailVerified(true)
            .isDeleted(false)
            .build();
        testReviewer = userRepository.save(testReviewer);

        LoginRequest loginRequest = LoginRequest.builder()
            .provider("google")
            .providerId("google-reviewer-123")
            .email("reviewer@oddiya.com")
            .nickname("Test Reviewer")
            .build();

        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = response.getBody().getData();
        accessToken = authResponse.getAccessToken();
        userId = authResponse.getUser().getId();
    }

    @Test
    @Order(1)
    @DisplayName("Should submit review successfully and update place rating")
    void shouldSubmitReviewSuccessfullyAndUpdatePlaceRating() {
        // Given: Place with initial rating
        Place targetPlace = testPlace1;
        Double initialRating = targetPlace.getRating();
        Integer initialReviewCount = targetPlace.getReviewCount();

        CreateReviewRequest reviewRequest = createReviewRequest(
            targetPlace.getId().toString(),
            5,
            "Absolutely amazing place! The view from Seoul Tower is breathtaking, especially during sunset. " +
            "The staff was friendly and helpful. Highly recommend visiting during clear weather for the best experience."
        );

        HttpEntity<CreateReviewRequest> request = new HttpEntity<>(reviewRequest, createAuthHeaders());

        // When: Submitting review
        ResponseEntity<ApiResponse<ReviewResponse>> response = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Then: Should create review successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ApiResponse<ReviewResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        ReviewResponse reviewResponse = body.getData();
        assertThat(reviewResponse).isNotNull();
        assertThat(reviewResponse.getId()).isNotBlank();
        assertThat(reviewResponse.getRating()).isEqualTo(5);
        assertThat(reviewResponse.getContent()).isEqualTo(reviewRequest.getContent());
        assertThat(reviewResponse.getUser().getId()).isEqualTo(userId);
        assertThat(reviewResponse.getPlace().getId()).isEqualTo(reviewRequest.getPlaceId());

        // Verify review is saved in database
        List<Review> reviews = testReviewer.getReviews();
        Optional<Review> savedReview = reviews.stream()
            .filter(r -> r.getPlace().getId().equals(targetPlace.getId()))
            .findFirst();

        assertThat(savedReview).isPresent();
        assertThat(savedReview.get().getRating()).isEqualTo(5);
        assertThat(savedReview.get().getContent()).isEqualTo(reviewRequest.getContent());

        // Verify place rating is updated
        Place updatedPlace = placeRepository.findById(targetPlace.getId()).orElse(null);
        assertThat(updatedPlace).isNotNull();
        assertThat(updatedPlace.getReviewCount()).isEqualTo(initialReviewCount + 1);
        
        // Calculate expected rating: (initial_rating * initial_count + new_rating) / (initial_count + 1)
        Double expectedRating = (initialRating * initialReviewCount + 5.0) / (initialReviewCount + 1);
        assertThat(updatedPlace.getRating()).isCloseTo(expectedRating, within(0.01));
    }

    @Test
    @Order(2)
    @DisplayName("Should submit multiple reviews and calculate average rating correctly")
    void shouldSubmitMultipleReviewsAndCalculateAverageRatingCorrectly() throws InterruptedException {
        // Given: Multiple users and reviews for the same place
        Place targetPlace = testPlace1;
        
        // Create additional test users
        User reviewer2 = User.builder()
            .email("reviewer2@oddiya.com")
            .username("reviewer2")
            .nickname("Test Reviewer 2")
            .provider("google")
            .providerId("google-reviewer-456")
            .isActive(true)
            .isDeleted(false)
            .build();
        reviewer2 = userRepository.save(reviewer2);

        User reviewer3 = User.builder()
            .email("reviewer3@oddiya.com")
            .username("reviewer3")
            .nickname("Test Reviewer 3")
            .provider("google")
            .providerId("google-reviewer-789")
            .isActive(true)
            .isDeleted(false)
            .build();
        reviewer3 = userRepository.save(reviewer3);

        // Get initial place statistics
        Place initialPlace = placeRepository.findById(targetPlace.getId()).orElse(null);
        assertThat(initialPlace).isNotNull();
        Double initialRating = initialPlace.getRating();
        Integer initialReviewCount = initialPlace.getReviewCount();

        // Submit first review (rating: 5)
        CreateReviewRequest review1 = createReviewRequest(
            targetPlace.getId().toString(),
            5,
            "Excellent place with amazing views and great service."
        );
        HttpEntity<CreateReviewRequest> request1 = new HttpEntity<>(review1, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> response1 = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request1,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait for potential async processing
        waitForAsyncOperations();

        // Authenticate as second reviewer and submit review (rating: 3)
        String token2 = authenticateUserAndGetToken(reviewer2);
        CreateReviewRequest review2 = createReviewRequest(
            targetPlace.getId().toString(),
            3,
            "Average experience. The place was okay but nothing special."
        );
        
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.APPLICATION_JSON);
        headers2.setBearerAuth(token2);
        HttpEntity<CreateReviewRequest> request2 = new HttpEntity<>(review2, headers2);

        ResponseEntity<ApiResponse<ReviewResponse>> response2 = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request2,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        waitForAsyncOperations();

        // Authenticate as third reviewer and submit review (rating: 4)
        String token3 = authenticateUserAndGetToken(reviewer3);
        CreateReviewRequest review3 = createReviewRequest(
            targetPlace.getId().toString(),
            4,
            "Good place with nice atmosphere. Would visit again."
        );
        
        HttpHeaders headers3 = new HttpHeaders();
        headers3.setContentType(MediaType.APPLICATION_JSON);
        headers3.setBearerAuth(token3);
        HttpEntity<CreateReviewRequest> request3 = new HttpEntity<>(review3, headers3);

        ResponseEntity<ApiResponse<ReviewResponse>> response3 = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request3,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        waitForAsyncOperations();

        // Then: Verify final rating calculation
        Place finalPlace = placeRepository.findById(targetPlace.getId()).orElse(null);
        assertThat(finalPlace).isNotNull();
        assertThat(finalPlace.getReviewCount()).isEqualTo(initialReviewCount + 3);
        
        // Expected rating calculation: (initial_rating * initial_count + 5 + 3 + 4) / (initial_count + 3)
        Double expectedRating = (initialRating * initialReviewCount + 5.0 + 3.0 + 4.0) / (initialReviewCount + 3);
        assertThat(finalPlace.getRating()).isCloseTo(expectedRating, within(0.01));

        // Verify all reviews are properly associated with the place
        HttpEntity<Void> getPlaceRequest = new HttpEntity<>(createAuthHeaders());
        ResponseEntity<ApiResponse<PlaceResponse>> getPlaceResponse = restTemplate.exchange(
            baseUrl + "/places/" + targetPlace.getId(),
            HttpMethod.GET,
            getPlaceRequest,
            new ParameterizedTypeReference<ApiResponse<PlaceResponse>>() {}
        );

        assertThat(getPlaceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceResponse placeResponse = getPlaceResponse.getBody().getData();
        assertThat(placeResponse.getReviewCount()).isEqualTo(initialReviewCount + 3);
        assertThat(placeResponse.getRating()).isCloseTo(expectedRating, within(0.01));
    }

    @Test
    @Order(3)
    @DisplayName("Should retrieve reviews for place with proper pagination")
    void shouldRetrieveReviewsForPlaceWithProperPagination() {
        // Given: Place with multiple reviews
        Place targetPlace = testPlace1;

        // Submit multiple reviews first
        for (int i = 1; i <= 3; i++) {
            CreateReviewRequest reviewRequest = createReviewRequest(
                targetPlace.getId().toString(),
                4 + (i % 2), // Alternate between 4 and 5 star ratings
                "Review content number " + i + " - This is a detailed review for testing pagination."
            );

            HttpEntity<CreateReviewRequest> request = new HttpEntity<>(reviewRequest, createAuthHeaders());

            ResponseEntity<ApiResponse<ReviewResponse>> response = restTemplate.exchange(
                baseUrl + "/reviews",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // When: Retrieving reviews for place
        HttpEntity<Void> getRequest = new HttpEntity<>(createAuthHeaders());
        
        ResponseEntity<ApiResponse<List<ReviewResponse>>> response = restTemplate.exchange(
            baseUrl + "/places/" + targetPlace.getId() + "/reviews?page=0&size=10",
            HttpMethod.GET,
            getRequest,
            new ParameterizedTypeReference<ApiResponse<List<ReviewResponse>>>() {}
        );

        // Then: Should return reviews successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<List<ReviewResponse>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        List<ReviewResponse> reviews = body.getData();
        assertThat(reviews).isNotEmpty();
        assertThat(reviews.size()).isGreaterThanOrEqualTo(3);

        // Verify review content
        ReviewResponse firstReview = reviews.get(0);
        assertThat(firstReview.getPlace().getId()).isEqualTo(targetPlace.getId().toString());
        assertThat(firstReview.getRating()).isBetween(1, 5);
        assertThat(firstReview.getContent()).isNotBlank();
        assertThat(firstReview.getUser()).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("Should handle duplicate review submission from same user")
    void shouldHandleDuplicateReviewSubmissionFromSameUser() {
        // Given: User has already submitted a review for a place
        Place targetPlace = testPlace2;

        CreateReviewRequest firstReview = createReviewRequest(
            targetPlace.getId().toString(),
            4,
            "My first review for this place. Really enjoyed the experience."
        );

        HttpEntity<CreateReviewRequest> firstRequest = new HttpEntity<>(firstReview, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> firstResponse = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            firstRequest,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // When: Submitting another review for the same place from same user
        CreateReviewRequest duplicateReview = createReviewRequest(
            targetPlace.getId().toString(),
            5,
            "This is my second review attempt for the same place."
        );

        HttpEntity<CreateReviewRequest> duplicateRequest = new HttpEntity<>(duplicateReview, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> duplicateResponse = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            duplicateRequest,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Then: Should either update existing review or reject duplicate
        if (duplicateResponse.getStatusCode() == HttpStatus.OK || duplicateResponse.getStatusCode() == HttpStatus.CREATED) {
            // System allows review updates
            ReviewResponse updatedReview = duplicateResponse.getBody().getData();
            assertThat(updatedReview.getRating()).isEqualTo(5);
            assertThat(updatedReview.getContent()).isEqualTo(duplicateReview.getContent());
        } else {
            // System rejects duplicate reviews
            assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        // Verify only one review exists from this user for this place
        long reviewCount = testReviewer.getReviews().stream()
            .filter(r -> r.getPlace().getId().equals(targetPlace.getId()))
            .count();
        assertThat(reviewCount).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should validate review content and rating constraints")
    void shouldValidateReviewContentAndRatingConstraints() {
        // Given: Invalid review requests
        Place targetPlace = testPlace1;

        // Test invalid rating (above 5)
        CreateReviewRequest invalidRatingReview = createReviewRequest(
            targetPlace.getId().toString(),
            6, // Invalid rating
            "This review has an invalid rating that should be rejected."
        );

        HttpEntity<CreateReviewRequest> invalidRatingRequest = new HttpEntity<>(invalidRatingReview, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> invalidRatingResponse = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            invalidRatingRequest,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Should reject invalid rating
        assertThat(invalidRatingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test invalid rating (below 1)
        CreateReviewRequest lowRatingReview = createReviewRequest(
            targetPlace.getId().toString(),
            0, // Invalid rating
            "This review has an invalid low rating."
        );

        HttpEntity<CreateReviewRequest> lowRatingRequest = new HttpEntity<>(lowRatingReview, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> lowRatingResponse = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            lowRatingRequest,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Should reject invalid low rating
        assertThat(lowRatingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test review with too short content
        CreateReviewRequest shortContentReview = CreateReviewRequest.builder()
            .placeId(targetPlace.getId().toString())
            .rating(4)
            .content("Short") // Too short - needs at least 10 characters
            .build();

        HttpEntity<CreateReviewRequest> shortContentRequest = new HttpEntity<>(shortContentReview, createAuthHeaders());

        ResponseEntity<ApiResponse<ReviewResponse>> shortContentResponse = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            shortContentRequest,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Should reject too short content
        assertThat(shortContentResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    @DisplayName("Should maintain transaction consistency during review submission")
    void shouldMaintainTransactionConsistencyDuringReviewSubmission() {
        // Given: Review for non-existent place
        CreateReviewRequest reviewForNonExistentPlace = createReviewRequest(
            "999999", // Non-existent place ID
            4,
            "This review is for a place that doesn't exist and should cause transaction rollback."
        );

        HttpEntity<CreateReviewRequest> request = new HttpEntity<>(reviewForNonExistentPlace, createAuthHeaders());

        // Count reviews before request
        long reviewCountBefore = testReviewer.getReviews().size();

        // When: Submitting review for non-existent place
        ResponseEntity<ApiResponse<ReviewResponse>> response = restTemplate.exchange(
            baseUrl + "/reviews",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<ReviewResponse>>() {}
        );

        // Then: Should return error and not create partial data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        long reviewCountAfter = testReviewer.getReviews().size();
        assertThat(reviewCountAfter).isEqualTo(reviewCountBefore);

        // Verify no orphaned review data was created
        List<Review> allReviews = userRepository.findById(testReviewer.getId())
            .map(User::getReviews)
            .orElse(List.of());
        
        boolean hasOrphanedReview = allReviews.stream()
            .anyMatch(review -> review.getPlace() == null);
        assertThat(hasOrphanedReview).isFalse();
    }

    /**
     * Helper method to authenticate a user and return access token
     */
    private String authenticateUserAndGetToken(User user) {
        LoginRequest loginRequest = LoginRequest.builder()
            .provider(user.getProvider())
            .providerId(user.getProviderId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .build();

        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData().getAccessToken();
    }

    @Override
    protected HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }
}