package com.oddiya.service.impl;

import com.oddiya.config.ProfileConfiguration;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.entity.Place;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.service.PlaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlaceServiceImpl implements PlaceService {
    
    private final ProfileConfiguration profileConfiguration;
    private final PlaceRepository placeRepository;
    
    public PlaceServiceImpl(ProfileConfiguration profileConfiguration,
                           PlaceRepository placeRepository) {
        this.profileConfiguration = profileConfiguration;
        this.placeRepository = placeRepository;
        
        log.info("PlaceServiceImpl initialized with storage type: {}", 
                profileConfiguration.getStorageType());
    }
    
    @Override
    @Transactional
    public PlaceResponse createPlace(CreatePlaceRequest request) {
        Place place = Place.builder()
                .name(request.getName())
                .description(request.getDescription())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .category(request.getCategory())
                .phoneNumber(request.getPhoneNumber())
                .website(request.getWebsite())
                .openingHours(request.getOpeningHours())
                .images(request.getImages() != null ? request.getImages() : new ArrayList<>())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .rating(0.0)
                .reviewCount(0)
                .bookmarkCount(0)
                .build();
        
        place = placeRepository.save(place);
        return mapToResponse(place);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PlaceResponse getPlace(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found with id: " + id));
        return mapToResponse(place);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceResponse> searchPlaces(String query, Pageable pageable) {
        Page<Place> places = placeRepository.searchPlaces(query, pageable);
        return mapToPageResponse(places);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PlaceResponse> getNearbyPlaces(double latitude, double longitude, double radius) {
        // Convert radius from km to meters for the query
        int radiusInMeters = (int) (radius * 1000);
        List<Place> places = placeRepository.findNearbyPlaces(latitude, longitude, radiusInMeters);
        
        return places.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceResponse> getPlacesByCategory(String category, Pageable pageable) {
        Page<Place> places = placeRepository.findByCategory(category, pageable);
        return mapToPageResponse(places);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceResponse> getPopularPlaces(Pageable pageable) {
        Page<Place> places = placeRepository.findAllByOrderByPopularityScoreDesc(pageable);
        return mapToPageResponse(places);
    }
    
    @Override
    @Transactional
    public PlaceResponse updatePlace(String id, CreatePlaceRequest request) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found with id: " + id));
        
        place.setName(request.getName());
        place.setDescription(request.getDescription());
        place.setAddress(request.getAddress());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setCategory(request.getCategory());
        place.setPhoneNumber(request.getPhoneNumber());
        place.setWebsite(request.getWebsite());
        place.setOpeningHours(request.getOpeningHours());
        place.setImages(request.getImages());
        place.setTags(request.getTags());
        
        place = placeRepository.save(place);
        return mapToResponse(place);
    }
    
    @Override
    @Transactional
    public void deletePlace(String id) {
        if (!placeRepository.existsById(id)) {
            throw new NotFoundException("Place not found with id: " + id);
        }
        placeRepository.deleteById(id);
    }
    
    @Override
    @Transactional
    public void incrementViewCount(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found with id: " + id));
        
        // Increment view count
        if (place.getViewCount() != null) {
            place.setViewCount(place.getViewCount() + 1);
        } else {
            place.setViewCount(1L);
        }
        
        // Update popularity score if needed
        updatePopularityScore(place);
        
        placeRepository.save(place);
    }
    
    private void updatePopularityScore(Place place) {
        // Simple popularity score calculation
        // You can enhance this with more sophisticated algorithms
        double score = 0.0;
        
        if (place.getViewCount() != null) {
            score += Math.log10(place.getViewCount() + 1) * 10;
        }
        
        if (place.getRating() != null) {
            score += place.getRating() * 20;
        }
        
        if (place.getReviewCount() != null) {
            score += Math.log10(place.getReviewCount() + 1) * 5;
        }
        
        if (place.getBookmarkCount() != null) {
            score += Math.log10(place.getBookmarkCount() + 1) * 15;
        }
        
        place.setPopularityScore(score);
    }
    
    private PlaceResponse mapToResponse(Place place) {
        return PlaceResponse.builder()
                .id(place.getId())
                .name(place.getName())
                .description(place.getDescription())
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .category(place.getCategory())
                .phoneNumber(place.getPhoneNumber())
                .website(place.getWebsite())
                .openingHours(place.getOpeningHours())
                .images(place.getImages())
                .tags(place.getTags())
                .averageRating(place.getRating())
                .reviewCount(place.getReviewCount())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
    
    private PageResponse<PlaceResponse> mapToPageResponse(Page<Place> placePage) {
        List<PlaceResponse> content = placePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        return PageResponse.<PlaceResponse>builder()
                .content(content)
                .pageNumber(placePage.getNumber())
                .pageSize(placePage.getSize())
                .totalElements(placePage.getTotalElements())
                .totalPages(placePage.getTotalPages())
                .last(placePage.isLast())
                .build();
    }
    
    @Override
    public boolean verifyPlaceData(String placeId) {
        log.info("Verifying place data for place ID: {}", placeId);
        // Mock implementation - in real implementation would verify against external APIs
        return placeRepository.existsById(placeId);
    }
    
    @Override
    public java.util.Map<String, Object> getPlaceStatistics(String placeId) {
        log.debug("Getting statistics for place ID: {}", placeId);
        
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new NotFoundException("Place not found with ID: " + placeId));
        
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("placeId", place.getId());
        stats.put("name", place.getName());
        stats.put("category", place.getCategory());
        stats.put("rating", place.getRating() != null ? place.getRating() : 0.0);
        stats.put("reviewCount", place.getReviewCount() != null ? place.getReviewCount() : 0);
        stats.put("bookmarkCount", place.getBookmarkCount() != null ? place.getBookmarkCount() : 0);
        stats.put("viewCount", place.getViewCount() != null ? place.getViewCount() : 0L);
        stats.put("popularityScore", place.getPopularityScore() != null ? place.getPopularityScore() : 0.0);
        stats.put("isVerified", place.isVerified() != null ? place.isVerified() : false);
        stats.put("createdAt", place.getCreatedAt());
        stats.put("updatedAt", place.getUpdatedAt());
        
        return stats;
    }
}