package com.oddiya.service.impl;

import com.oddiya.dto.PlaceDTO;
import com.oddiya.entity.Place;
import com.oddiya.mapper.PlaceMapper;
import com.oddiya.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceServiceImplForTest {
    
    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;
    
    public PlaceDTO findById(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Place not found with id: " + id));
        return placeMapper.toDto(place);
    }
    
    public Page<PlaceDTO> findAll(Pageable pageable) {
        Page<Place> placePage = placeRepository.findAll(pageable);
        return placePage.map(placeMapper::toDto);
    }
    
    public PlaceDTO save(PlaceDTO placeDTO) {
        Place place = placeMapper.toEntity(placeDTO);
        place = placeRepository.save(place);
        return placeMapper.toDto(place);
    }
    
    public PlaceDTO update(String id, PlaceDTO updateDTO) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Place not found with id: " + id));
        
        if (updateDTO.getName() != null) {
            place.setName(updateDTO.getName());
        }
        if (updateDTO.getRating() != null) {
            place.setRating(updateDTO.getRating());
        }
        if (updateDTO.getDescription() != null) {
            place.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getAddress() != null) {
            place.setAddress(updateDTO.getAddress());
        }
        
        place = placeRepository.save(place);
        return placeMapper.toDto(place);
    }
    
    public void deleteById(String id) {
        if (!placeRepository.existsById(id)) {
            throw new RuntimeException("Place not found with id: " + id);
        }
        placeRepository.deleteById(id);
    }
    
    public Page<PlaceDTO> findByCategory(String category, Pageable pageable) {
        Page<Place> placePage = placeRepository.findByCategory(category, pageable);
        return placePage.map(placeMapper::toDto);
    }
    
    public Page<PlaceDTO> searchByName(String searchTerm, Pageable pageable) {
        Page<Place> placePage = placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(searchTerm, searchTerm, pageable);
        return placePage.map(placeMapper::toDto);
    }
}