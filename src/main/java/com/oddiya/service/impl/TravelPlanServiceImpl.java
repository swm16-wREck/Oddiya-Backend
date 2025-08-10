package com.oddiya.service.impl;

import com.oddiya.dto.request.CreateItineraryItemRequest;
import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.ItineraryItemResponse;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.entity.*;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.NotFoundException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.*;
import com.oddiya.service.TravelPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelPlanServiceImpl implements TravelPlanService {
    
    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final SavedPlanRepository savedPlanRepository;
    
    @Override
    @Transactional
    public TravelPlanResponse createTravelPlan(String userId, CreateTravelPlanRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Start date must be before end date");
        }
        
        TravelPlan travelPlan = TravelPlan.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .isAiGenerated(request.getAiGenerated() != null ? request.getAiGenerated() : false)
                .coverImageUrl(request.getImageUrl())
                .tags(request.getTags())
                .status(TravelPlanStatus.DRAFT)
                .viewCount(0L)
                .saveCount(0L)
                .build();
        
        travelPlan = travelPlanRepository.save(travelPlan);
        
        if (request.getItineraryItems() != null) {
            for (CreateItineraryItemRequest itemRequest : request.getItineraryItems()) {
                createItineraryItem(travelPlan, itemRequest);
            }
        }
        
        return mapToResponse(travelPlan);
    }
    
    @Override
    public TravelPlanResponse getTravelPlan(String id) {
        TravelPlan travelPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!travelPlan.isPublic()) {
            // Check if user has access (owner or collaborator)
            // For now, we'll allow all public plans
        }
        
        return mapToResponse(travelPlan);
    }
    
    @Override
    @Transactional
    public TravelPlanResponse updateTravelPlan(String userId, String id, UpdateTravelPlanRequest request) {
        TravelPlan travelPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!travelPlan.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to update this travel plan");
        }
        
        if (request.getTitle() != null) {
            travelPlan.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            travelPlan.setDescription(request.getDescription());
        }
        if (request.getDestination() != null) {
            travelPlan.setDestination(request.getDestination());
        }
        if (request.getStartDate() != null) {
            travelPlan.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            travelPlan.setEndDate(request.getEndDate());
        }
        if (request.getIsPublic() != null) {
            travelPlan.setPublic(request.getIsPublic());
        }
        if (request.getImageUrl() != null) {
            travelPlan.setCoverImageUrl(request.getImageUrl());
        }
        if (request.getTags() != null) {
            travelPlan.setTags(request.getTags());
        }
        if (request.getStatus() != null) {
            travelPlan.setStatus(TravelPlanStatus.valueOf(request.getStatus()));
        }
        
        travelPlan = travelPlanRepository.save(travelPlan);
        return mapToResponse(travelPlan);
    }
    
    @Override
    @Transactional
    public void deleteTravelPlan(String userId, String id) {
        TravelPlan travelPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!travelPlan.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to delete this travel plan");
        }
        
        travelPlanRepository.delete(travelPlan);
    }
    
    @Override
    public PageResponse<TravelPlanResponse> getUserTravelPlans(String userId, Pageable pageable) {
        Page<TravelPlan> page = travelPlanRepository.findByUserId(userId, pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    public PageResponse<TravelPlanResponse> getPublicTravelPlans(Pageable pageable) {
        Page<TravelPlan> page = travelPlanRepository.findByIsPublicTrue(pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    public PageResponse<TravelPlanResponse> searchTravelPlans(String query, Pageable pageable) {
        Page<TravelPlan> page = travelPlanRepository.findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
                query, query, pageable);
        return mapToPageResponse(page);
    }
    
    @Override
    @Transactional
    public TravelPlanResponse copyTravelPlan(String userId, String id) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        TravelPlan originalPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        TravelPlan copiedPlan = TravelPlan.builder()
                .user(user)
                .title(originalPlan.getTitle() + " (Copy)")
                .description(originalPlan.getDescription())
                .destination(originalPlan.getDestination())
                .startDate(originalPlan.getStartDate())
                .endDate(originalPlan.getEndDate())
                .isPublic(false)
                .isAiGenerated(false)
                .coverImageUrl(originalPlan.getCoverImageUrl())
                .tags(new ArrayList<>(originalPlan.getTags()))
                .status(TravelPlanStatus.DRAFT)
                .viewCount(0L)
                .saveCount(0L)
                .build();
        
        copiedPlan = travelPlanRepository.save(copiedPlan);
        
        // Copy itinerary items
        for (ItineraryItem item : originalPlan.getItineraryItems()) {
            ItineraryItem copiedItem = ItineraryItem.builder()
                    .travelPlan(copiedPlan)
                    .dayNumber(item.getDayNumber())
                    .sequence(item.getSequence())
                    .place(item.getPlace())
                    .startTime(item.getStartTime())
                    .endTime(item.getEndTime())
                    .notes(item.getNotes())
                    .transportMode(item.getTransportMode())
                    .transportDurationMinutes(item.getTransportDurationMinutes())
                    .build();
            itineraryItemRepository.save(copiedItem);
        }
        
        return mapToResponse(copiedPlan);
    }
    
    @Override
    @Transactional
    public void addCollaborator(String userId, String planId, String collaboratorId) {
        TravelPlan travelPlan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!travelPlan.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to add collaborators to this travel plan");
        }
        
        User collaborator = userRepository.findById(collaboratorId)
                .orElseThrow(() -> new NotFoundException("Collaborator not found"));
        
        if (!travelPlan.getCollaborators().contains(collaborator)) {
            travelPlan.getCollaborators().add(collaborator);
            travelPlanRepository.save(travelPlan);
        }
    }
    
    @Override
    @Transactional
    public void removeCollaborator(String userId, String planId, String collaboratorId) {
        TravelPlan travelPlan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!travelPlan.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to remove collaborators from this travel plan");
        }
        
        User collaborator = userRepository.findById(collaboratorId)
                .orElseThrow(() -> new NotFoundException("Collaborator not found"));
        
        travelPlan.getCollaborators().remove(collaborator);
        travelPlanRepository.save(travelPlan);
    }
    
    @Override
    @Transactional
    public void incrementViewCount(String id) {
        travelPlanRepository.findById(id).ifPresent(plan -> {
            plan.setViewCount(plan.getViewCount() + 1);
            travelPlanRepository.save(plan);
        });
    }
    
    @Override
    @Transactional
    public void saveTravelPlan(String userId, String id) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        TravelPlan travelPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        if (!savedPlanRepository.existsByUserIdAndTravelPlanId(userId, id)) {
            SavedPlan savedPlan = SavedPlan.builder()
                    .user(user)
                    .travelPlan(travelPlan)
                    .build();
            savedPlanRepository.save(savedPlan);
            
            travelPlan.setSaveCount(travelPlan.getSaveCount() + 1);
            travelPlanRepository.save(travelPlan);
        }
    }
    
    @Override
    @Transactional
    public void unsaveTravelPlan(String userId, String id) {
        SavedPlan savedPlan = savedPlanRepository.findByUserIdAndTravelPlanId(userId, id)
                .orElseThrow(() -> new NotFoundException("Saved plan not found"));
        
        savedPlanRepository.delete(savedPlan);
        
        TravelPlan travelPlan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
        
        travelPlan.setSaveCount(Math.max(0, travelPlan.getSaveCount() - 1));
        travelPlanRepository.save(travelPlan);
    }
    
    @Override
    public PageResponse<TravelPlanResponse> getSavedTravelPlans(String userId, Pageable pageable) {
        Page<SavedPlan> savedPlans = savedPlanRepository.findByUserId(userId, pageable);
        Page<TravelPlan> travelPlans = savedPlans.map(SavedPlan::getTravelPlan);
        return mapToPageResponse(travelPlans);
    }
    
    private void createItineraryItem(TravelPlan travelPlan, CreateItineraryItemRequest request) {
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new NotFoundException("Place not found"));
        
        ItineraryItem item = ItineraryItem.builder()
                .travelPlan(travelPlan)
                .dayNumber(request.getDayNumber())
                .sequence(request.getOrder())
                .place(place)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .notes(request.getNotes())
                .transportMode(request.getTransportMode())
                .transportDurationMinutes(request.getTransportDuration())
                .build();
        
        itineraryItemRepository.save(item);
    }
    
    private TravelPlanResponse mapToResponse(TravelPlan travelPlan) {
        List<ItineraryItemResponse> itineraryItems = travelPlan.getItineraryItems() != null
                ? travelPlan.getItineraryItems().stream()
                    .map(this::mapItineraryItemToResponse)
                    .collect(Collectors.toList())
                : new ArrayList<>();
        
        List<String> collaboratorIds = travelPlan.getCollaborators() != null
                ? travelPlan.getCollaborators().stream()
                    .map(User::getId)
                    .collect(Collectors.toList())
                : new ArrayList<>();
        
        return TravelPlanResponse.builder()
                .id(travelPlan.getId())
                .userId(travelPlan.getUser().getId())
                .userName(travelPlan.getUser().getNickname())
                .userProfilePicture(travelPlan.getUser().getProfileImageUrl())
                .title(travelPlan.getTitle())
                .description(travelPlan.getDescription())
                .destination(travelPlan.getDestination())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .status(travelPlan.getStatus().name())
                .isPublic(travelPlan.isPublic())
                .aiGenerated(travelPlan.isAiGenerated())
                .imageUrl(travelPlan.getCoverImageUrl())
                .tags(travelPlan.getTags())
                .viewCount(travelPlan.getViewCount())
                .saveCount(travelPlan.getSaveCount())
                .itineraryItems(itineraryItems)
                .collaboratorIds(collaboratorIds)
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();
    }
    
    private ItineraryItemResponse mapItineraryItemToResponse(ItineraryItem item) {
        PlaceResponse placeResponse = PlaceResponse.builder()
                .id(item.getPlace().getId())
                .name(item.getPlace().getName())
                .description(item.getPlace().getDescription())
                .address(item.getPlace().getAddress())
                .latitude(item.getPlace().getLatitude())
                .longitude(item.getPlace().getLongitude())
                .category(item.getPlace().getCategory())
                .phoneNumber(item.getPlace().getPhoneNumber())
                .website(item.getPlace().getWebsite())
                .openingHours(item.getPlace().getOpeningHours())
                .priceRange(null)
                .averageRating(item.getPlace().getRating())
                .reviewCount(item.getPlace().getReviewCount())
                .images(item.getPlace().getImages())
                .tags(item.getPlace().getTags())
                .googlePlaceId(null)
                .metadata(null)
                .createdAt(item.getPlace().getCreatedAt())
                .updatedAt(item.getPlace().getUpdatedAt())
                .build();
        
        return ItineraryItemResponse.builder()
                .id(item.getId())
                .dayNumber(item.getDayNumber())
                .order(item.getSequence())
                .place(placeResponse)
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .notes(item.getNotes())
                .transportMode(item.getTransportMode())
                .transportDuration(item.getTransportDurationMinutes())
                .build();
    }
    
    private PageResponse<TravelPlanResponse> mapToPageResponse(Page<TravelPlan> page) {
        List<TravelPlanResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        return PageResponse.<TravelPlanResponse>builder()
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