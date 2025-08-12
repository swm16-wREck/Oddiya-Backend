package com.oddiya.controller;

import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.UserProfileResponse;
import com.oddiya.service.JwtService;
import com.oddiya.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Enhanced User Controller
 * Handles comprehensive user management including social features per PRD specifications
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User management and social features API")
public class UserController {
    
    private final UserService userService;
    private final JwtService jwtService;
    
    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get current user profile", description = "Get the authenticated user's profile")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(HttpServletRequest request) {
        try {
            String userId = jwtService.extractUserIdFromRequest(request);
            UserProfileResponse userProfile = userService.getUserProfile(userId);
            
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .success(true)
                    .message("User profile retrieved successfully")
                    .data(userProfile)
                    .build());
        } catch (Exception e) {
            log.error("Error getting user profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UserProfileResponse>builder()
                    .success(false)
                    .message("Failed to get user profile: " + e.getMessage())
                    .build());
        }
    }
    
    @PutMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update user profile", description = "Update the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = jwtService.extractUserIdFromRequest(httpRequest);
            UserProfileResponse updatedProfile = userService.updateUserProfile(userId, request);
            
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .success(true)
                    .message("Profile updated successfully")
                    .data(updatedProfile)
                    .build());
        } catch (Exception e) {
            log.error("Error updating user profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UserProfileResponse>builder()
                    .success(false)
                    .message("Failed to update profile: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Get user information by ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @Parameter(description = "User ID") @PathVariable String userId) {
        try {
            UserProfileResponse userProfile = userService.getUserProfile(userId);
            
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .success(true)
                    .message("User profile retrieved successfully")
                    .data(userProfile)
                    .build());
        } catch (Exception e) {
            log.error("Error getting user by ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UserProfileResponse>builder()
                    .success(false)
                    .message("User not found")
                    .build());
        }
    }
    
    @DeleteMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Delete user account", description = "GDPR compliant account deletion")
    public ResponseEntity<ApiResponse<Void>> deleteUserAccount(HttpServletRequest request) {
        try {
            String userId = jwtService.extractUserIdFromRequest(request);
            userService.deleteUserAccount(userId);
            
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("Account deleted successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error deleting user account: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message("Failed to delete account: " + e.getMessage())
                    .build());
        }
    }
    
    @PutMapping("/preferences")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update user preferences", description = "Update user preferences")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserPreferences(
            @RequestBody Map<String, String> preferences,
            HttpServletRequest request) {
        try {
            String userId = jwtService.extractUserIdFromRequest(request);
            UserProfileResponse updatedProfile = userService.updateUserPreferences(userId, preferences);
            
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .success(true)
                    .message("Preferences updated successfully")
                    .data(updatedProfile)
                    .build());
        } catch (Exception e) {
            log.error("Error updating user preferences: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UserProfileResponse>builder()
                    .success(false)
                    .message("Failed to update preferences: " + e.getMessage())
                    .build());
        }
    }
    
    @PutMapping("/travel-preferences")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update travel preferences", description = "Update travel preferences for AI planning")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateTravelPreferences(
            @RequestBody Map<String, String> travelPreferences,
            HttpServletRequest request) {
        try {
            String userId = jwtService.extractUserIdFromRequest(request);
            UserProfileResponse updatedProfile = userService.updateTravelPreferences(userId, travelPreferences);
            
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .success(true)
                    .message("Travel preferences updated successfully")
                    .data(updatedProfile)
                    .build());
        } catch (Exception e) {
            log.error("Error updating travel preferences: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UserProfileResponse>builder()
                    .success(false)
                    .message("Failed to update travel preferences: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by nickname or email")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> searchUsers(
            @Parameter(description = "Search query") @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            PageResponse<UserProfileResponse> users = userService.searchUsers(q, pageable);
            
            return ResponseEntity.ok(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(true)
                    .message("Users retrieved successfully")
                    .data(users)
                    .build());
        } catch (Exception e) {
            log.error("Error searching users: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(false)
                    .message("Failed to search users: " + e.getMessage())
                    .build());
        }
    }
    
    @PostMapping("/{userId}/follow")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Follow user", description = "Follow another user")
    public ResponseEntity<ApiResponse<Void>> followUser(
            @Parameter(description = "User ID to follow") @PathVariable String userId,
            HttpServletRequest request) {
        try {
            String currentUserId = jwtService.extractUserIdFromRequest(request);
            userService.followUser(currentUserId, userId);
            
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("User followed successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error following user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message("Failed to follow user: " + e.getMessage())
                    .build());
        }
    }
    
    @DeleteMapping("/{userId}/follow")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Unfollow user", description = "Unfollow another user")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @Parameter(description = "User ID to unfollow") @PathVariable String userId,
            HttpServletRequest request) {
        try {
            String currentUserId = jwtService.extractUserIdFromRequest(request);
            userService.unfollowUser(currentUserId, userId);
            
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("User unfollowed successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error unfollowing user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message("Failed to unfollow user: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/{userId}/followers")
    @Operation(summary = "Get user followers", description = "Get list of user's followers")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> getFollowers(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            PageResponse<UserProfileResponse> followers = userService.getFollowers(userId, pageable);
            
            return ResponseEntity.ok(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(true)
                    .message("Followers retrieved successfully")
                    .data(followers)
                    .build());
        } catch (Exception e) {
            log.error("Error getting followers for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(false)
                    .message("Failed to get followers: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/{userId}/following")
    @Operation(summary = "Get users being followed", description = "Get list of users this user is following")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> getFollowing(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            PageResponse<UserProfileResponse> following = userService.getFollowing(userId, pageable);
            
            return ResponseEntity.ok(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(true)
                    .message("Following retrieved successfully")
                    .data(following)
                    .build());
        } catch (Exception e) {
            log.error("Error getting following for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<PageResponse<UserProfileResponse>>builder()
                    .success(false)
                    .message("Failed to get following: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/{userId}/statistics")
    @Operation(summary = "Get user statistics", description = "Get user's travel and activity statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserStatistics(
            @Parameter(description = "User ID") @PathVariable String userId) {
        try {
            Map<String, Object> statistics = userService.getUserStatistics(userId);
            
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("User statistics retrieved successfully")
                    .data(statistics)
                    .build());
        } catch (Exception e) {
            log.error("Error getting statistics for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to get user statistics: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/{userId}/is-following/{targetUserId}")
    @Operation(summary = "Check if following", description = "Check if user is following another user")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isFollowing(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Target User ID") @PathVariable String targetUserId) {
        try {
            boolean isFollowing = userService.isFollowing(userId, targetUserId);
            
            return ResponseEntity.ok(ApiResponse.<Map<String, Boolean>>builder()
                    .success(true)
                    .message("Follow status retrieved successfully")
                    .data(Map.of("isFollowing", isFollowing))
                    .build());
        } catch (Exception e) {
            log.error("Error checking follow status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Boolean>>builder()
                    .success(false)
                    .message("Failed to check follow status: " + e.getMessage())
                    .build());
        }
    }
}