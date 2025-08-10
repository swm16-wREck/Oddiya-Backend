package com.oddiya.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Travel recommendation request")
public class RecommendationRequest {
    
    @Schema(description = "Destination for recommendations", example = "Seoul")
    private String destination;
    
    @Schema(description = "Duration of travel in days", example = "5")
    private Integer duration;
    
    @Schema(description = "Budget in USD", example = "2000")
    private Integer budget;
    
    @Schema(description = "Travel interests", example = "[\"culture\", \"food\", \"shopping\"]")
    private List<String> interests;
    
    @Schema(description = "Travel style", example = "adventure")
    private String travelStyle;
    
    @Schema(description = "Season of travel", example = "spring")
    private String season;
    
    @Schema(description = "Number of travelers", example = "2")
    private Integer travelers;
}