package com.oddiya.service;

import com.oddiya.dto.PlaceDTO;
import com.oddiya.entity.Place;
import com.oddiya.mapper.PlaceMapper;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.service.impl.PlaceServiceImplForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceMapper placeMapper;

    @InjectMocks
    private PlaceServiceImplForTest placeService;

    private Place testPlace;
    private PlaceDTO testPlaceDTO;
    private String testPlaceId = "test-place-id";

    @BeforeEach
    void setUp() {
        testPlace = new Place();
        testPlace.setId(testPlaceId);
        testPlace.setName("Test Restaurant");
        testPlace.setCategory("RESTAURANT");
        testPlace.setAddress("123 Test Street");
        testPlace.setLatitude(37.5665);
        testPlace.setLongitude(126.9780);
        testPlace.setRating(4.5);
        testPlace.setDescription("A great place to eat");
        testPlace.setCreatedAt(LocalDateTime.now());
        testPlace.setUpdatedAt(LocalDateTime.now());

        testPlaceDTO = new PlaceDTO();
        testPlaceDTO.setId(testPlaceId);
        testPlaceDTO.setName("Test Restaurant");
        testPlaceDTO.setCategory("RESTAURANT");
        testPlaceDTO.setAddress("123 Test Street");
        testPlaceDTO.setLatitude(37.5665);
        testPlaceDTO.setLongitude(126.9780);
        testPlaceDTO.setRating(4.5);
        testPlaceDTO.setDescription("A great place to eat");
    }

    @Test
    @DisplayName("Should find place by ID")
    void findById_WhenPlaceExists_ShouldReturnPlace() {
        // Given
        when(placeRepository.findById(testPlaceId)).thenReturn(Optional.of(testPlace));
        when(placeMapper.toDto(testPlace)).thenReturn(testPlaceDTO);

        // When
        PlaceDTO result = placeService.findById(testPlaceId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testPlaceId);
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(placeRepository).findById(testPlaceId);
        verify(placeMapper).toDto(testPlace);
    }

    @Test
    @DisplayName("Should throw exception when place not found")
    void findById_WhenPlaceDoesNotExist_ShouldThrowException() {
        // Given
        when(placeRepository.findById(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> placeService.findById("non-existent-id"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Place not found");
        
        verify(placeRepository).findById("non-existent-id");
        verify(placeMapper, never()).toDto(any());
    }

    @Test
    @DisplayName("Should find all places with pagination")
    void findAll_ShouldReturnPagedPlaces() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<Place> places = Arrays.asList(testPlace);
        Page<Place> placePage = new PageImpl<>(places, pageable, 1);
        
        when(placeRepository.findAll(pageable)).thenReturn(placePage);
        when(placeMapper.toDto(testPlace)).thenReturn(testPlaceDTO);

        // When
        Page<PlaceDTO> result = placeService.findAll(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Test Restaurant");
        verify(placeRepository).findAll(pageable);
        verify(placeMapper).toDto(testPlace);
    }

    @Test
    @DisplayName("Should save new place")
    void save_ShouldCreateNewPlace() {
        // Given
        when(placeMapper.toEntity(testPlaceDTO)).thenReturn(testPlace);
        when(placeRepository.save(any(Place.class))).thenReturn(testPlace);
        when(placeMapper.toDto(testPlace)).thenReturn(testPlaceDTO);

        // When
        PlaceDTO result = placeService.save(testPlaceDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(placeMapper).toEntity(testPlaceDTO);
        verify(placeRepository).save(any(Place.class));
        verify(placeMapper).toDto(testPlace);
    }

    @Test
    @DisplayName("Should update existing place")
    void update_WhenPlaceExists_ShouldUpdatePlace() {
        // Given
        PlaceDTO updateDTO = new PlaceDTO();
        updateDTO.setName("Updated Restaurant");
        updateDTO.setRating(4.8);
        
        Place updatedPlace = new Place();
        updatedPlace.setId(testPlaceId);
        updatedPlace.setName("Updated Restaurant");
        updatedPlace.setRating(4.8);
        
        when(placeRepository.findById(testPlaceId)).thenReturn(Optional.of(testPlace));
        when(placeRepository.save(any(Place.class))).thenReturn(updatedPlace);
        when(placeMapper.toDto(updatedPlace)).thenReturn(updateDTO);

        // When
        PlaceDTO result = placeService.update(testPlaceId, updateDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Restaurant");
        assertThat(result.getRating()).isEqualTo(4.8);
        verify(placeRepository).findById(testPlaceId);
        verify(placeRepository).save(any(Place.class));
    }

    @Test
    @DisplayName("Should delete place by ID")
    void deleteById_WhenPlaceExists_ShouldDeletePlace() {
        // Given
        when(placeRepository.existsById(testPlaceId)).thenReturn(true);
        doNothing().when(placeRepository).deleteById(testPlaceId);

        // When
        placeService.deleteById(testPlaceId);

        // Then
        verify(placeRepository).existsById(testPlaceId);
        verify(placeRepository).deleteById(testPlaceId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent place")
    void deleteById_WhenPlaceDoesNotExist_ShouldThrowException() {
        // Given
        when(placeRepository.existsById("non-existent-id")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> placeService.deleteById("non-existent-id"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Place not found");
        
        verify(placeRepository).existsById("non-existent-id");
        verify(placeRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("Should find places by category")
    void findByCategory_ShouldReturnPlacesInCategory() {
        // Given
        String category = "RESTAURANT";
        Pageable pageable = PageRequest.of(0, 10);
        List<Place> places = Arrays.asList(testPlace);
        Page<Place> placePage = new PageImpl<>(places, pageable, 1);
        
        when(placeRepository.findByCategory(category, pageable)).thenReturn(placePage);
        when(placeMapper.toDto(testPlace)).thenReturn(testPlaceDTO);

        // When
        Page<PlaceDTO> result = placeService.findByCategory(category, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("RESTAURANT");
        verify(placeRepository).findByCategory(category, pageable);
    }

    @Test
    @DisplayName("Should search places by name")
    void searchByName_ShouldReturnMatchingPlaces() {
        // Given
        String searchTerm = "Test";
        Pageable pageable = PageRequest.of(0, 10);
        List<Place> places = Arrays.asList(testPlace);
        Page<Place> placePage = new PageImpl<>(places, pageable, 1);
        
        when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(searchTerm, searchTerm, pageable)).thenReturn(placePage);
        when(placeMapper.toDto(testPlace)).thenReturn(testPlaceDTO);

        // When
        Page<PlaceDTO> result = placeService.searchByName(searchTerm, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).contains("Test");
        verify(placeRepository).findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(searchTerm, searchTerm, pageable);
    }
}