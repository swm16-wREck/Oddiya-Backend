package com.oddiya.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String email;
    private String username;
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private boolean isEmailVerified;
    private boolean isPremium;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}