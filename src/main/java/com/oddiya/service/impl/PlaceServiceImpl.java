package com.oddiya.service.impl;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.entity.Place;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.service.PlaceService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {
    
    private final PlaceRepository placeRepository;
    
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
    public PlaceResponse getPlace(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found"));
        return mapToResponse(place);
    }
    
    @Override
    @Transactional
    public PlaceResponse updatePlace(String id, CreatePlaceRequest request) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found"));
        
        if (request.getName() != null) {
            place.setName(request.getName());
        }
        if (request.getDescription() != null) {
            place.setDescription(request.getDescription());
        }
        if (request.getAddress() != null) {
            place.setAddress(request.getAddress());
        }
        if (request.getLatitude() != null) {
            place.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            place.setLongitude(request.getLongitude());
        }
        if (request.getCategory() != null) {
            place.setCategory(request.getCategory());
        }
        if (request.getPhoneNumber() != null) {
            place.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getWebsite() != null) {
            place.setWebsite(request.getWebsite());
        }
        if (request.getOpeningHours() != null) {
            place.setOpeningHours(request.getOpeningHours());
        }
        if (request.getImages() != null) {
            place.setImages(request.getImages());
        }
        if (request.getTags() != null) {
            place.setTags(request.getTags());
        }
        
        place = placeRepository.save(place);
        return mapToResponse(place);
    }
    
    @Override
    @Transactional
    public void deletePlace(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found"));
        placeRepository.delete(place);
    }
    
    @Override
    public PageResponse<PlaceResponse> searchPlaces(String query, Pageable pageable) {
        Page<Place> page = placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                query, query, pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    public List<PlaceResponse> getNearbyPlaces(double latitude, double longitude, double radius) {
        // For now, return empty list. In production, this would use spatial queries
        // with PostGIS or similar spatial database extensions
        return new ArrayList<>();
    }
    
    @Override
    public PageResponse<PlaceResponse> getPlacesByCategory(String category, Pageable pageable) {
        Page<Place> page = placeRepository.findByCategory(category, pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    public PageResponse<PlaceResponse> getPopularPlaces(Pageable pageable) {
        Page<Place> page = placeRepository.findAllByOrderByPopularityScoreDesc(pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    @Transactional
    public void incrementViewCount(String id) {
        placeRepository.findById(id).ifPresent(place -> {
            // Increment view count logic could be added here
            placeRepository.save(place);
        });
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
                .priceRange(null)
                .averageRating(place.getRating())
                .reviewCount(place.getReviewCount())
                .images(place.getImages())
                .tags(place.getTags())
                .googlePlaceId(null)
                .metadata(null)
                .isSaved(false)
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
    
    private PageResponse<PlaceResponse> mapToPageResponse(Page<Place> page) {
        List<PlaceResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        return PageResponse.<PlaceResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}