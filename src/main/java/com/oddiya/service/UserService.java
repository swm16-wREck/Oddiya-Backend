package com.oddiya.service;

import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.UserProfileResponse;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * User Service Interface
 * Handles user profile management, preferences, and social features per PRD specifications
 */
public interface UserService {
    
    /**
     * Get user profile by ID
     */
    UserProfileResponse getUserProfile(String userId);
    
    /**
     * Get user profile by email
     */
    UserProfileResponse getUserProfileByEmail(String email);
    
    /**
     * Update user profile
     */
    UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request);
    
    /**
     * Update user preferences
     */
    UserProfileResponse updateUserPreferences(String userId, Map<String, String> preferences);
    
    /**
     * Update travel preferences for AI planning
     */
    UserProfileResponse updateTravelPreferences(String userId, Map<String, String> travelPreferences);
    
    /**
     * Delete user account (GDPR compliant)
     */
    void deleteUserAccount(String userId);
    
    /**
     * Search users by nickname or email
     */
    PageResponse<UserProfileResponse> searchUsers(String query, Pageable pageable);
    
    /**
     * Follow another user (social feature)
     */
    void followUser(String userId, String targetUserId);
    
    /**
     * Unfollow another user
     */
    void unfollowUser(String userId, String targetUserId);
    
    /**
     * Get user's followers
     */
    PageResponse<UserProfileResponse> getFollowers(String userId, Pageable pageable);
    
    /**
     * Get users that this user is following
     */
    PageResponse<UserProfileResponse> getFollowing(String userId, Pageable pageable);
    
    /**
     * Check if user exists by ID
     */
    boolean userExists(String userId);
    
    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if user exists by provider and provider ID (OAuth)
     */
    boolean existsByProviderAndProviderId(String provider, String providerId);
    
    /**
     * Update last login time
     */
    void updateLastLoginTime(String userId);
    
    /**
     * Activate user account
     */
    void activateUser(String userId);
    
    /**
     * Deactivate user account
     */
    void deactivateUser(String userId);
    
    /**
     * Get user statistics for dashboard
     */
    Map<String, Object> getUserStatistics(String userId);
    
    /**
     * Check if user is following another user
     */
    boolean isFollowing(String userId, String targetUserId);
}