package com.oddiya.controller;

import com.oddiya.dto.UserDTO;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.entity.User;
import com.oddiya.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User management API")
public class UserController {
    
    private final UserRepository userRepository;
    
    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(@AuthenticationPrincipal String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        UserDTO userDTO = convertToDTO(user);
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }
    
    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateUserProfile(
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody UserDTO updateRequest) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Update only allowed fields
        if (updateRequest.getNickname() != null) {
            user.setNickname(updateRequest.getNickname());
        }
        if (updateRequest.getBio() != null) {
            user.setBio(updateRequest.getBio());
        }
        if (updateRequest.getProfileImageUrl() != null) {
            user.setProfileImageUrl(updateRequest.getProfileImageUrl());
        }
        
        User savedUser = userRepository.save(user);
        UserDTO userDTO = convertToDTO(savedUser);
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Get user information by ID")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable String id) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        UserDTO userDTO = convertToDTO(userOptional.get());
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }
    
    @DeleteMapping("/profile")
    @Operation(summary = "Delete user account", description = "Delete the authenticated user's account")
    public ResponseEntity<ApiResponse<Void>> deleteUserAccount(@AuthenticationPrincipal String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/preferences")
    @Operation(summary = "Update user preferences", description = "Update user preferences")
    public ResponseEntity<ApiResponse<UserDTO>> updateUserPreferences(
            @AuthenticationPrincipal String userEmail,
            @RequestBody Map<String, String> preferences) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        user.setPreferences(preferences);
        User savedUser = userRepository.save(user);
        UserDTO userDTO = convertToDTO(savedUser);
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by username")
    public ResponseEntity<ApiResponse<List<UserDTO>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<User> userPage = userRepository.searchUsers(q, pageable);
        List<UserDTO> userDTOs = userPage.getContent().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(userDTOs));
    }
    
    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
            .id(user.getId())
            .email(user.getEmail())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .bio(user.getBio())
            .profileImageUrl(user.getProfileImageUrl())
            .isEmailVerified(user.isEmailVerified())
            .isPremium(user.isPremium())
            .isActive(user.isActive())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
}