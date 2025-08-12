package com.oddiya.service.impl;

import com.oddiya.config.ProfileConfiguration;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.entity.Place;
import com.oddiya.entity.dynamodb.DynamoDBPlace;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.repository.dynamodb.DynamoDBPlaceRepository;
import com.oddiya.service.PlaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DynamoDBPlaceRepository dynamoDBPlaceRepository;
    
    public PlaceServiceImpl(ProfileConfiguration profileConfiguration,
                           @Autowired(required = false) PlaceRepository placeRepository,
                           @Autowired(required = false) DynamoDBPlaceRepository dynamoDBPlaceRepository) {
        this.profileConfiguration = profileConfiguration;
        this.placeRepository = placeRepository;
        this.dynamoDBPlaceRepository = dynamoDBPlaceRepository;
        
        log.info("PlaceServiceImpl initialized with storage type: {}", 
                profileConfiguration.getStorageType());
    }
    
    @Override
    @Transactional
    public PlaceResponse createPlace(CreatePlaceRequest request) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            return createPlaceDynamoDB(request);
        } else {
            return createPlaceJPA(request);
        }
    }
    
    private PlaceResponse createPlaceJPA(CreatePlaceRequest request) {
        if (placeRepository == null) {
            throw new IllegalStateException("JPA PlaceRepository not available");
        }
        
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
        return mapJpaToResponse(place);
    }
    
    private PlaceResponse createPlaceDynamoDB(CreatePlaceRequest request) {
        if (dynamoDBPlaceRepository == null) {
            throw new IllegalStateException("DynamoDB PlaceRepository not available");
        }
        
        DynamoDBPlace place = new DynamoDBPlace();
        place.setPlaceId(java.util.UUID.randomUUID().toString());
        place.setName(request.getName());
        place.setDescription(request.getDescription());
        place.setAddress(request.getAddress());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setCategory(request.getCategory());
        place.setPhoneNumber(request.getPhoneNumber());
        place.setWebsite(request.getWebsite());
        place.setOpeningHours(request.getOpeningHours());
        place.setImages(request.getImages() != null ? request.getImages() : new ArrayList<>());
        place.setTags(request.getTags() != null ? request.getTags() : new ArrayList<>());
        place.setRating(0.0);
        place.setReviewCount(0);
        place.setBookmarkCount(0);
        place.setCreatedAt(java.time.LocalDateTime.now());
        place.setUpdatedAt(java.time.LocalDateTime.now());
        
        place = dynamoDBPlaceRepository.save(place);
        return mapDynamoToResponse(place);
    }
    
    @Override
    public PlaceResponse getPlace(String id) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            return getPlaceDynamoDB(id);
        } else {
            return getPlaceJPA(id);
        }
    }
    
    private PlaceResponse getPlaceJPA(String id) {
        if (placeRepository == null) {
            throw new IllegalStateException("JPA PlaceRepository not available");
        }
        
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found"));
        return mapJpaToResponse(place);
    }
    
    private PlaceResponse getPlaceDynamoDB(String id) {
        if (dynamoDBPlaceRepository == null) {
            throw new IllegalStateException("DynamoDB PlaceRepository not available");
        }
        
        DynamoDBPlace place = dynamoDBPlaceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found"));
        return mapDynamoToResponse(place);
    }
    
    @Override
    @Transactional
    public PlaceResponse updatePlace(String id, CreatePlaceRequest request) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            return updatePlaceDynamoDB(id, request);
        } else {
            return updatePlaceJPA(id, request);
        }
    }
    
    private PlaceResponse updatePlaceJPA(String id, CreatePlaceRequest request) {
        if (placeRepository == null) {
            throw new IllegalStateException("JPA PlaceRepository not available");
        }
        
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
        return mapJpaToResponse(place);
    }
    
    private PlaceResponse updatePlaceDynamoDB(String id, CreatePlaceRequest request) {
        if (dynamoDBPlaceRepository == null) {
            throw new IllegalStateException("DynamoDB PlaceRepository not available");
        }
        
        DynamoDBPlace place = dynamoDBPlaceRepository.findById(id)
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
        
        place.setUpdatedAt(java.time.LocalDateTime.now());
        place = dynamoDBPlaceRepository.save(place);
        return mapDynamoToResponse(place);
    }
    
    @Override
    @Transactional
    public void deletePlace(String id) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            if (dynamoDBPlaceRepository == null) {
                throw new IllegalStateException("DynamoDB PlaceRepository not available");
            }
            DynamoDBPlace place = dynamoDBPlaceRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Place not found"));
            dynamoDBPlaceRepository.delete(place);
        } else {
            if (placeRepository == null) {
                throw new IllegalStateException("JPA PlaceRepository not available");
            }
            Place place = placeRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Place not found"));
            placeRepository.delete(place);
        }
    }
    
    @Override
    public PageResponse<PlaceResponse> searchPlaces(String query, Pageable pageable) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            // For DynamoDB, implement a simple search (in production, use OpenSearch or similar)
            return PageResponse.<PlaceResponse>builder()
                    .content(new ArrayList<>())
                    .pageNumber(0)
                    .pageSize(10)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();
        } else {
            if (placeRepository == null) {
                throw new IllegalStateException("JPA PlaceRepository not available");
            }
            Page<Place> page = placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                    query, query, pageable);
            return mapToPageResponse(page);
        }
    }
    
    @Override
    public List<PlaceResponse> getNearbyPlaces(double latitude, double longitude, double radius) {
        // For now, return empty list. In production, this would use spatial queries
        // with PostGIS or similar spatial database extensions for JPA,
        // or geospatial queries for DynamoDB
        return new ArrayList<>();
    }
    
    @Override
    public PageResponse<PlaceResponse> getPlacesByCategory(String category, Pageable pageable) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            // For DynamoDB, use GSI on category (simplified implementation)
            return PageResponse.<PlaceResponse>builder()
                    .content(new ArrayList<>())
                    .pageNumber(0)
                    .pageSize(10)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();
        } else {
            if (placeRepository == null) {
                throw new IllegalStateException("JPA PlaceRepository not available");
            }
            Page<Place> page = placeRepository.findByCategory(category, pageable);
            return mapToPageResponse(page);
        }
    }
    
    @Override
    public PageResponse<PlaceResponse> getPopularPlaces(Pageable pageable) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            // For DynamoDB, implement popularity scoring (simplified implementation)
            return PageResponse.<PlaceResponse>builder()
                    .content(new ArrayList<>())
                    .pageNumber(0)
                    .pageSize(10)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();
        } else {
            if (placeRepository == null) {
                throw new IllegalStateException("JPA PlaceRepository not available");
            }
            Page<Place> page = placeRepository.findAllByOrderByPopularityScoreDesc(pageable);
            return mapToPageResponse(page);
        }
    }
    
    @Override
    @Transactional
    public void incrementViewCount(String id) {
        if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.DYNAMODB) {
            if (dynamoDBPlaceRepository != null) {
                dynamoDBPlaceRepository.findById(id).ifPresent(place -> {
                    // Increment view count logic could be added here
                    place.setUpdatedAt(java.time.LocalDateTime.now());
                    dynamoDBPlaceRepository.save(place);
                });
            }
        } else {
            if (placeRepository != null) {
                placeRepository.findById(id).ifPresent(place -> {
                    // Increment view count logic could be added here
                    placeRepository.save(place);
                });
            }
        }
    }
    
    private PlaceResponse mapJpaToResponse(Place place) {
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
    
    private PlaceResponse mapDynamoToResponse(DynamoDBPlace place) {
        return PlaceResponse.builder()
                .id(place.getPlaceId())
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
                .map(this::mapJpaToResponse)
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