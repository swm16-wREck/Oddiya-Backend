package com.oddiya.mapper;

import com.oddiya.dto.PlaceDTO;
import com.oddiya.entity.Place;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PlaceMapper {
    
    PlaceDTO toDto(Place place);
    
    Place toEntity(PlaceDTO placeDTO);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(PlaceDTO placeDTO, @MappingTarget Place place);
}