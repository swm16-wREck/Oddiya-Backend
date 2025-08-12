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
        Page<Place> places = placeRepository.searchByNameOrDescriptionContaining(query, query, pageable);
        return mapToPageResponse(places);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceResponse> findPlacesByCategory(String category, Pageable pageable) {
        Page<Place> places = placeRepository.findByCategory(category, pageable);
        return mapToPageResponse(places);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PlaceResponse> findNearbyPlaces(double latitude, double longitude, double radiusInKm) {
        List<Place> places = placeRepository.findAll();
        
        // Filter places within radius
        List<Place> nearbyPlaces = places.stream()
                .filter(place -> {
                    if (place.getLatitude() == null || place.getLongitude() == null) {
                        return false;
                    }
                    double distance = calculateDistance(latitude, longitude, 
                            place.getLatitude(), place.getLongitude());
                    return distance <= radiusInKm;
                })
                .collect(Collectors.toList());
        
        return nearbyPlaces.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                .rating(place.getRating())
                .reviewCount(place.getReviewCount())
                .bookmarkCount(place.getBookmarkCount())
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
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula to calculate distance between two points
        double R = 6371; // Earth's radius in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}