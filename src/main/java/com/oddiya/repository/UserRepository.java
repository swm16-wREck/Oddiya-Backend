package com.oddiya.repository;

import com.oddiya.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    
    boolean existsByEmail(String email);
    
    boolean existsByNickname(String nickname);
    
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isDeleted = false")
    Page<User> findActiveUsers(Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.nickname) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.bio) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND u.isActive = true AND u.isDeleted = false")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN u.followers f WHERE f.id = :followerId")
    Page<User> findFollowing(@Param("followerId") String followerId, Pageable pageable);
    
    @Query("SELECT f FROM User u JOIN u.followers f WHERE u.id = :userId")
    Page<User> findFollowers(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT COUNT(f) FROM User u JOIN u.followers f WHERE u.id = :userId")
    Long countFollowers(@Param("userId") String userId);
    
    @Query("SELECT COUNT(u) FROM User u JOIN u.followers f WHERE f.id = :userId")
    Long countFollowing(@Param("userId") String userId);
}