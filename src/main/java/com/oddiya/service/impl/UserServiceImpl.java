package com.oddiya.service.impl;

import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.UserProfileResponse;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.User;
import com.oddiya.entity.Video;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.TravelPlanRepository;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String userId) {
        log.debug("Getting user profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        return mapToUserProfileResponse(user);
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByEmail(String email) {
        log.debug("Getting user profile for email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        return mapToUserProfileResponse(user);
    }
    
    @Override
    @Transactional
    public UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request) {
        log.debug("Updating user profile for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        // Update only non-null fields
        if (request.getName() != null) {
            user.setNickname(request.getName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getProfilePicture() != null) {
            user.setProfileImageUrl(request.getProfilePicture());
        }
        
        user = userRepository.save(user);
        log.info("Updated profile for user ID: {}", userId);
        return mapToUserProfileResponse(user);
    }
    
    @Override
    @Transactional
    public UserProfileResponse updateUserPreferences(String userId, Map<String, String> preferences) {
        log.debug("Updating preferences for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setPreferences(preferences);
        user = userRepository.save(user);
        
        log.info("Updated preferences for user ID: {}", userId);
        return mapToUserProfileResponse(user);
    }
    
    @Override
    @Transactional
    public void deleteUserAccount(String userId) {
        log.debug("Deleting user account for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        userRepository.delete(user);
        log.info("Deleted user account for user ID: {}", userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> searchUsers(String query, Pageable pageable) {
        log.debug("Searching users with query: '{}', page: {}", query, pageable.getPageNumber());
        
        Page<User> userPage = userRepository.searchUsers(query, pageable);
        List<UserProfileResponse> userResponses = userPage.getContent().stream()
                .map(this::mapToUserProfileResponse)
                .collect(Collectors.toList());
        
        return PageResponse.<UserProfileResponse>builder()
                .content(userResponses)
                .pageNumber(userPage.getNumber())
                .pageSize(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .empty(userPage.isEmpty())
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean userExists(String userId) {
        return userRepository.existsById(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
    
    @Override
    @Transactional
    public void activateUser(String userId) {
        log.debug("Activating user account for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setActive(true);
        userRepository.save(user);
        
        log.info("Activated user account for user ID: {}", userId);
    }
    
    @Override
    @Transactional
    public void deactivateUser(String userId) {
        log.debug("Deactivating user account for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setActive(false);
        userRepository.save(user);
        
        log.info("Deactivated user account for user ID: {}", userId);
    }
    
    @Override
    @Transactional
    public UserProfileResponse updateTravelPreferences(String userId, Map<String, String> travelPreferences) {
        log.debug("Updating travel preferences for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setTravelPreferences(travelPreferences);
        user = userRepository.save(user);
        
        log.info("Updated travel preferences for user ID: {}", userId);
        return mapToUserProfileResponse(user);
    }
    
    @Override
    @Transactional
    public void followUser(String userId, String targetUserId) {
        log.debug("User {} following user {}", userId, targetUserId);
        
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("Cannot follow yourself");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found with ID: " + targetUserId));
        
        if (!user.getFollowing().contains(targetUser)) {
            user.getFollowing().add(targetUser);
            userRepository.save(user);
            log.info("User {} now following user {}", userId, targetUserId);
        }
    }
    
    @Override
    @Transactional
    public void unfollowUser(String userId, String targetUserId) {
        log.debug("User {} unfollowing user {}", userId, targetUserId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found with ID: " + targetUserId));
        
        if (user.getFollowing().contains(targetUser)) {
            user.getFollowing().remove(targetUser);
            userRepository.save(user);
            log.info("User {} unfollowed user {}", userId, targetUserId);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getFollowers(String userId, Pageable pageable) {
        log.debug("Getting followers for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        // This would be optimized with a custom query in a real implementation
        List<UserProfileResponse> followers = user.getFollowers().stream()
                .map(this::mapToUserProfileResponse)
                .collect(Collectors.toList());
        
        // Simple pagination for followers list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), followers.size());
        List<UserProfileResponse> pagedFollowers = followers.subList(start, end);
        
        return PageResponse.<UserProfileResponse>builder()
                .content(pagedFollowers)
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalElements((long) followers.size())
                .totalPages((int) Math.ceil((double) followers.size() / pageable.getPageSize()))
                .first(pageable.getPageNumber() == 0)
                .last(end >= followers.size())
                .empty(followers.isEmpty())
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getFollowing(String userId, Pageable pageable) {
        log.debug("Getting following for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        // This would be optimized with a custom query in a real implementation
        List<UserProfileResponse> following = user.getFollowing().stream()
                .map(this::mapToUserProfileResponse)
                .collect(Collectors.toList());
        
        // Simple pagination for following list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), following.size());
        List<UserProfileResponse> pagedFollowing = following.subList(start, end);
        
        return PageResponse.<UserProfileResponse>builder()
                .content(pagedFollowing)
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalElements((long) following.size())
                .totalPages((int) Math.ceil((double) following.size() / pageable.getPageSize()))
                .first(pageable.getPageNumber() == 0)
                .last(end >= following.size())
                .empty(following.isEmpty())
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByProviderAndProviderId(String provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId).isPresent();
    }
    
    @Override
    @Transactional
    public void updateLastLoginTime(String userId) {
        log.debug("Updating last login time for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.debug("Updated last login time for user ID: {}", userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStatistics(String userId) {
        log.debug("Getting user statistics for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTravelPlans", user.getTravelPlans().size());
        stats.put("publicTravelPlans", user.getTravelPlans().stream()
                .filter(TravelPlan::isPublic)
                .count());
        stats.put("totalVideos", user.getVideos().size());
        stats.put("totalFollowers", user.getFollowers().size());
        stats.put("totalFollowing", user.getFollowing().size());
        stats.put("totalReviews", user.getReviews().size());
        stats.put("joinDate", user.getCreatedAt());
        stats.put("lastLogin", user.getLastLoginAt());
        stats.put("isActive", user.isActive());
        stats.put("isPremium", user.isPremium());
        
        return stats;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(String userId, String targetUserId) {
        log.debug("Checking if user {} is following user {}", userId, targetUserId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        return user.getFollowing().stream()
                .anyMatch(followedUser -> followedUser.getId().equals(targetUserId));
    }

    
    private UserProfileResponse mapToUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getNickname())
                .bio(user.getBio())
                .profilePicture(user.getProfileImageUrl())
                .followersCount((long) user.getFollowers().size())
                .followingCount((long) user.getFollowing().size())
                .travelPlansCount((long) user.getTravelPlans().size())
                .isActive(user.isActive())
                .isPremium(user.isPremium())
                .preferences(user.getPreferences())
                .travelPreferences(user.getTravelPreferences())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}