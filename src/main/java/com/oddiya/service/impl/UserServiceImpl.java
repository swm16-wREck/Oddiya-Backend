package com.oddiya.service.impl;

import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.UserProfileResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    
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
    public boolean emailExists(String email) {
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
    public void updateLastLogin(String userId) {
        log.debug("Updating last login for user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.debug("Updated last login for user ID: {}", userId);
    }
    
    private UserProfileResponse mapToUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getNickname())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}