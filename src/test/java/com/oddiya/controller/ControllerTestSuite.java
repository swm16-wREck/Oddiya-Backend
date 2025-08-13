package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.response.*;
import com.oddiya.security.WithMockJwtUser;
import com.oddiya.service.*;
import com.oddiya.util.JwtTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive controller test suite for 100% controller coverage
 * Tests all endpoints for all controllers with basic success path scenarios
 */
@SpringJUnitConfig
@WebMvcTest({
    HealthController.class,
    MockAuthController.class, 
    UserController.class,
    AuthController.class,
    PlaceController.class,
    TravelPlanController.class,
    AIRecommendationController.class,
    OAuthController.class,
    SupabaseAuthController.class
})
@Import(TestSecurityConfig.class)
class ControllerTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Service mocks
    @MockBean private AuthService authService;
    @MockBean private PlaceService placeService;
    @MockBean private TravelPlanService travelPlanService;
    @MockBean private AIRecommendationService aiRecommendationService;
    @MockBean private OAuthService oAuthService;
    @MockBean private SupabaseService supabaseService;
    @MockBean private com.oddiya.service.JwtService jwtService;
    @MockBean private com.oddiya.repository.UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        // Setup JWT service mocks for authentication endpoints that need JWT validation
        when(jwtService.generateToken(anyString())).thenReturn("test-jwt-token");
        when(jwtService.validateToken(anyString())).thenReturn(true);
        when(jwtService.getUsernameFromToken(anyString())).thenReturn("test-user-id");
        when(jwtService.isTokenExpired(anyString())).thenReturn(false);
        when(jwtService.extractUserId(anyString())).thenReturn("test-user-id");
        when(jwtService.validateAccessToken(anyString())).thenReturn("test-user-id");
        when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("test-access-token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("test-refresh-token");
    }

    // Test response objects
    private AuthResponse createAuthResponse() {
        AuthResponse response = new AuthResponse();
        response.setAccessToken("test-access-token");
        response.setRefreshToken("test-refresh-token");
        response.setUserId("test-user-id");
        response.setEmail("test@example.com");
        response.setNickname("Test User");
        return response;
    }

    // HealthController Tests
    @Test
    @DisplayName("Health check endpoint should return OK")
    void healthCheck_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // MockAuthController Tests 
    @Test
    @DisplayName("Mock login should return tokens")
    void mockLogin_ShouldReturnTokens() throws Exception {
        // Mock UserRepository behavior for mock login
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(createTestUser());
        
        mockMvc.perform(post("/api/v1/auth/mock-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // UserController Tests
    @Test
    @WithMockJwtUser(value = "test-user-id", email = "test@example.com")
    @DisplayName("Get user profile should return user data")
    void getUserProfile_ShouldReturnUserData() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(createTestUser()));
        
        mockMvc.perform(get("/api/v1/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockJwtUser(value = "test-user-id", email = "test@example.com")
    @DisplayName("Update user profile should succeed")
    void updateUserProfile_ShouldSucceed() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(createTestUser()));
        when(userRepository.save(any())).thenReturn(createTestUser());
        
        mockMvc.perform(put("/api/v1/users/profile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Updated User\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Get user by ID should return user")
    void getUserById_ShouldReturnUser() throws Exception {
        when(userRepository.findById(anyString())).thenReturn(Optional.of(createTestUser()));
        
        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwtUser(value = "test-user-id", email = "test@example.com")
    @DisplayName("Delete user should succeed")
    void deleteUser_ShouldSucceed() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(createTestUser()));
        
        mockMvc.perform(delete("/api/v1/users/profile").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockJwtUser(value = "test-user-id", email = "test@example.com")
    @DisplayName("Update user preferences should succeed")
    void updateUserPreferences_ShouldSucceed() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(createTestUser()));
        when(userRepository.save(any())).thenReturn(createTestUser());
        
        mockMvc.perform(put("/api/v1/users/preferences")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"dark\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Search users should return results")
    void searchUsers_ShouldReturnResults() throws Exception {
        org.springframework.data.domain.Page<com.oddiya.entity.User> userPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(createTestUser()));
        when(userRepository.searchUsers(anyString(), any(Pageable.class))).thenReturn(userPage);
        
        mockMvc.perform(get("/api/v1/users/search").param("q", "test"))
            .andExpect(status().isOk());
    }

    // AuthController Tests
    @Test
    @DisplayName("Auth login should return tokens")
    void authLogin_ShouldReturnTokens() throws Exception {
        when(authService.login(any())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"google\",\"idToken\":\"test-token\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Auth refresh should return new tokens")
    void authRefresh_ShouldReturnTokens() throws Exception {
        when(authService.refreshToken(any())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"test-refresh\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    @DisplayName("Auth logout should succeed")
    void authLogout_ShouldSucceed() throws Exception {
        doNothing().when(authService).logout(anyString());
        
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Auth validate should return result")
    void authValidate_ShouldReturnResult() throws Exception {
        doNothing().when(authService).validateToken(anyString());
        
        // Mock the JWT service to return valid token for this specific test token
        when(jwtService.validateAccessToken("test-token")).thenReturn("test-user-id");
        
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk());
    }

    // PlaceController Tests - covering all 8 endpoints
    @Test
    @WithMockUser
    @DisplayName("Create place should return created place")
    void createPlace_ShouldReturnPlace() throws Exception {
        PlaceResponse place = new PlaceResponse();
        place.setId("1");
        place.setName("Test Place");
        when(placeService.createPlace(any())).thenReturn(place);
        
        mockMvc.perform(post("/api/v1/places")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Place\",\"address\":\"123 Test Street\",\"category\":\"restaurant\",\"latitude\":37.5,\"longitude\":126.9}"))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("Get place should return place and increment view count")
    void getPlace_ShouldReturnPlace() throws Exception {
        PlaceResponse place = new PlaceResponse();
        place.setId("1");
        when(placeService.getPlace(anyString())).thenReturn(place);
        doNothing().when(placeService).incrementViewCount(anyString());
        
        mockMvc.perform(get("/api/v1/places/1"))
            .andExpect(status().isOk());
        
        verify(placeService).incrementViewCount("1");
    }

    @Test
    @WithMockUser
    @DisplayName("Update place should return updated place")
    void updatePlace_ShouldReturnUpdatedPlace() throws Exception {
        PlaceResponse place = new PlaceResponse();
        place.setId("1");
        when(placeService.updatePlace(anyString(), any())).thenReturn(place);
        
        mockMvc.perform(put("/api/v1/places/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Place\",\"address\":\"123 Updated Street\",\"category\":\"restaurant\",\"latitude\":37.5,\"longitude\":126.9}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Delete place should return no content")
    void deletePlace_ShouldReturnNoContent() throws Exception {
        doNothing().when(placeService).deletePlace(anyString());
        
        mockMvc.perform(delete("/api/v1/places/1").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("Search places should return page response")
    void searchPlaces_ShouldReturnPageResponse() throws Exception {
        PageResponse<PlaceResponse> pageResponse = new PageResponse<>();
        pageResponse.setContent(new ArrayList<>());
        when(placeService.searchPlaces(anyString(), any(Pageable.class))).thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/places/search").param("query", "test"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Get nearby places should return list")
    void getNearbyPlaces_ShouldReturnList() throws Exception {
        when(placeService.getNearbyPlaces(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new ArrayList<>());
        
        mockMvc.perform(get("/api/v1/places/nearby")
                .param("latitude", "37.5")
                .param("longitude", "126.9"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Get places by category should return page response")
    void getPlacesByCategory_ShouldReturnPageResponse() throws Exception {
        PageResponse<PlaceResponse> pageResponse = new PageResponse<>();
        when(placeService.getPlacesByCategory(anyString(), any(Pageable.class)))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/places/category/restaurant"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Get popular places should return page response")
    void getPopularPlaces_ShouldReturnPageResponse() throws Exception {
        PageResponse<PlaceResponse> pageResponse = new PageResponse<>();
        when(placeService.getPopularPlaces(any(Pageable.class))).thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/places/popular"))
            .andExpect(status().isOk());
    }

    // TravelPlanController Tests - covering all 13 endpoints
    @Test
    @WithMockUser("user1")
    @DisplayName("Create travel plan should return created plan")
    void createTravelPlan_ShouldReturnPlan() throws Exception {
        TravelPlanResponse plan = new TravelPlanResponse();
        plan.setId("1");
        when(travelPlanService.createTravelPlan(anyString(), any())).thenReturn(plan);
        
        // Use future dates for validation
        String futureStartDate = java.time.LocalDate.now().plusDays(30).toString();
        String futureEndDate = java.time.LocalDate.now().plusDays(37).toString();
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Plan\",\"destination\":\"Seoul\",\"startDate\":\"" + futureStartDate + "\",\"endDate\":\"" + futureEndDate + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("Get travel plan should return plan and increment view count")
    void getTravelPlan_ShouldReturnPlan() throws Exception {
        TravelPlanResponse plan = new TravelPlanResponse();
        plan.setId("1");
        when(travelPlanService.getTravelPlan(anyString())).thenReturn(plan);
        doNothing().when(travelPlanService).incrementViewCount(anyString());
        
        mockMvc.perform(get("/api/v1/travel-plans/1"))
            .andExpect(status().isOk());
        
        verify(travelPlanService).incrementViewCount("1");
    }

    @Test
    @WithMockUser("user1") 
    @DisplayName("Update travel plan should return updated plan")
    void updateTravelPlan_ShouldReturnUpdatedPlan() throws Exception {
        TravelPlanResponse plan = new TravelPlanResponse();
        when(travelPlanService.updateTravelPlan(anyString(), anyString(), any())).thenReturn(plan);
        
        mockMvc.perform(put("/api/v1/travel-plans/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Plan\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Delete travel plan should return no content")
    void deleteTravelPlan_ShouldReturnNoContent() throws Exception {
        doNothing().when(travelPlanService).deleteTravelPlan(anyString(), anyString());
        
        mockMvc.perform(delete("/api/v1/travel-plans/1").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("Get user travel plans should return page response")
    void getUserTravelPlans_ShouldReturnPageResponse() throws Exception {
        PageResponse<TravelPlanResponse> pageResponse = new PageResponse<>();
        when(travelPlanService.getUserTravelPlans(anyString(), any(Pageable.class)))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/travel-plans/user/user1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Get public travel plans should return page response")
    void getPublicTravelPlans_ShouldReturnPageResponse() throws Exception {
        PageResponse<TravelPlanResponse> pageResponse = new PageResponse<>();
        when(travelPlanService.getPublicTravelPlans(any(Pageable.class))).thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/travel-plans/public"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Search travel plans should return page response")
    void searchTravelPlans_ShouldReturnPageResponse() throws Exception {
        PageResponse<TravelPlanResponse> pageResponse = new PageResponse<>();
        when(travelPlanService.searchTravelPlans(anyString(), any(Pageable.class)))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/travel-plans/search").param("query", "seoul"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Copy travel plan should return copied plan")
    void copyTravelPlan_ShouldReturnCopiedPlan() throws Exception {
        TravelPlanResponse plan = new TravelPlanResponse();
        when(travelPlanService.copyTravelPlan(anyString(), anyString())).thenReturn(plan);
        
        mockMvc.perform(post("/api/v1/travel-plans/1/copy").with(csrf()))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Add collaborator should return success")
    void addCollaborator_ShouldReturnSuccess() throws Exception {
        doNothing().when(travelPlanService).addCollaborator(anyString(), anyString(), anyString());
        
        mockMvc.perform(post("/api/v1/travel-plans/1/collaborators/user2").with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Remove collaborator should return no content")
    void removeCollaborator_ShouldReturnNoContent() throws Exception {
        doNothing().when(travelPlanService).removeCollaborator(anyString(), anyString(), anyString());
        
        mockMvc.perform(delete("/api/v1/travel-plans/1/collaborators/user2").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Save travel plan should return success")
    void saveTravelPlan_ShouldReturnSuccess() throws Exception {
        doNothing().when(travelPlanService).saveTravelPlan(anyString(), anyString());
        
        mockMvc.perform(post("/api/v1/travel-plans/1/save").with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Unsave travel plan should return no content")
    void unsaveTravelPlan_ShouldReturnNoContent() throws Exception {
        doNothing().when(travelPlanService).unsaveTravelPlan(anyString(), anyString());
        
        mockMvc.perform(delete("/api/v1/travel-plans/1/save").with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser("user1")
    @DisplayName("Get saved travel plans should return page response")
    void getSavedTravelPlans_ShouldReturnPageResponse() throws Exception {
        PageResponse<TravelPlanResponse> pageResponse = new PageResponse<>();
        when(travelPlanService.getSavedTravelPlans(anyString(), any(Pageable.class)))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/travel-plans/saved"))
            .andExpect(status().isOk());
    }

    // AIRecommendationController Tests - covering all 5 endpoints
    @Test
    @WithMockUser
    @DisplayName("Get AI recommendations should return recommendations")
    void getRecommendations_ShouldReturnRecommendations() throws Exception {
        RecommendationResponse response = new RecommendationResponse();
        response.setRecommendations(Arrays.asList("Place 1", "Place 2"));
        when(aiRecommendationService.getRecommendations(any())).thenReturn(response);
        
        mockMvc.perform(post("/api/v1/ai/recommendations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"Seoul\",\"duration\":5,\"budget\":2000,\"interests\":[\"culture\",\"food\"],\"travelStyle\":\"leisure\",\"season\":\"spring\",\"travelers\":2}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Generate travel plan should return plan suggestion")
    void generateTravelPlan_ShouldReturnPlanSuggestion() throws Exception {
        TravelPlanSuggestion suggestion = new TravelPlanSuggestion();
        suggestion.setDestination("Seoul");
        when(aiRecommendationService.generateTravelPlan(anyString(), anyInt(), anyList()))
            .thenReturn(suggestion);
        
        mockMvc.perform(post("/api/v1/ai/plan")
                .with(csrf())
                .param("destination", "Seoul")
                .param("days", "3"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Recommend places should return list of places")
    void recommendPlaces_ShouldReturnListOfPlaces() throws Exception {
        when(aiRecommendationService.recommendPlaces(anyString(), anyString(), anyInt()))
            .thenReturn(Arrays.asList("Place 1", "Place 2"));
        
        mockMvc.perform(get("/api/v1/ai/places").param("destination", "Seoul"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Generate itinerary should return itinerary map")
    void generateItinerary_ShouldReturnItineraryMap() throws Exception {
        when(aiRecommendationService.generateItinerary(anyString(), anyInt(), anyString(), anyList()))
            .thenReturn("Day 1: Visit palace");
        
        mockMvc.perform(post("/api/v1/ai/itinerary")
                .with(csrf())
                .param("destination", "Seoul")
                .param("days", "2")
                .param("budget", "medium")
                .param("interests", "culture"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Suggest destinations should return list of destinations")
    void suggestDestinations_ShouldReturnListOfDestinations() throws Exception {
        when(aiRecommendationService.suggestDestinations(anyList(), anyString(), anyInt()))
            .thenReturn(Arrays.asList("Seoul", "Busan"));
        
        mockMvc.perform(get("/api/v1/ai/destinations")
                .param("preferences", "culture")
                .param("season", "spring")
                .param("budget", "1000"))
            .andExpect(status().isOk());
    }

    // OAuthController Tests - covering all 3 endpoints (Google, Apple, verify)
    @Test
    @DisplayName("Google OAuth with auth code should return tokens")
    void googleOAuthWithAuthCode_ShouldReturnTokens() throws Exception {
        when(oAuthService.authenticateWithGoogle(anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authCode\":\"test-auth-code\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Google OAuth with ID token should return tokens")
    void googleOAuthWithIdToken_ShouldReturnTokens() throws Exception {
        when(oAuthService.authenticateWithGoogleIdToken(anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"test-id-token\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Google OAuth without auth code or ID token should return bad request")
    void googleOAuthWithoutCredentials_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Apple OAuth with auth code should return tokens")
    void appleOAuthWithAuthCode_ShouldReturnTokens() throws Exception {
        when(oAuthService.authenticateWithApple(anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/oauth/apple")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authCode\":\"test-auth-code\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Apple OAuth with ID token should return tokens")
    void appleOAuthWithIdToken_ShouldReturnTokens() throws Exception {
        when(oAuthService.authenticateWithAppleIdToken(anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/oauth/apple")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"test-id-token\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Apple OAuth without auth code or ID token should return bad request")
    void appleOAuthWithoutCredentials_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/apple")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Verify OAuth token should return user info")
    void verifyOAuthToken_ShouldReturnUserInfo() throws Exception {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("email", "test@example.com");
        when(oAuthService.verifyToken(anyString(), anyString())).thenReturn(userInfo);
        
        mockMvc.perform(post("/api/v1/auth/oauth/verify")
                .with(csrf())
                .param("provider", "google")
                .param("idToken", "test-token"))
            .andExpect(status().isOk());
    }

    // SupabaseAuthController Tests - covering all 6 endpoints
    @Test
    @DisplayName("Supabase signup should return created response")
    void supabaseSignup_ShouldReturnCreated() throws Exception {
        when(supabaseService.signUp(anyString(), anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/supabase/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\",\"username\":\"testuser\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Supabase signin should return auth response")
    void supabaseSignin_ShouldReturnAuthResponse() throws Exception {
        when(supabaseService.signInWithEmail(anyString(), anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/supabase/signin")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Supabase signout should return OK")
    void supabaseSignout_ShouldReturnOk() throws Exception {
        doNothing().when(supabaseService).signOut(anyString());
        
        mockMvc.perform(post("/api/v1/auth/supabase/signout")
                .with(csrf())
                .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Supabase refresh token should return auth response")
    void supabaseRefreshToken_ShouldReturnAuthResponse() throws Exception {
        when(supabaseService.refreshToken(anyString())).thenReturn(createAuthResponse());
        
        mockMvc.perform(post("/api/v1/auth/supabase/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"test-refresh-token\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Supabase verify token should return boolean")
    void supabaseVerifyToken_ShouldReturnBoolean() throws Exception {
        when(supabaseService.verifyToken(anyString())).thenReturn(true);
        
        mockMvc.perform(get("/api/v1/auth/supabase/verify")
                .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("Supabase get user ID should return user ID")
    void supabaseGetUserId_ShouldReturnUserId() throws Exception {
        when(supabaseService.getUserId(anyString())).thenReturn("user-123");
        
        mockMvc.perform(get("/api/v1/auth/supabase/user")
                .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("user-123"));
    }

    // Helper methods
    private com.oddiya.entity.User createTestUser() {
        com.oddiya.entity.User user = new com.oddiya.entity.User();
        user.setId("test-user-id");
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setNickname("Test User");
        return user;
    }
}