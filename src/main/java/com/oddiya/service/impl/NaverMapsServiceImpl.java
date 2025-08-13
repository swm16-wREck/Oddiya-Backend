package com.oddiya.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.entity.Place;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.service.NaverMapsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Naver Maps API Service Implementation
 * Integrates with Naver Maps API for Korean location services
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverMapsServiceImpl implements NaverMapsService {

    private final RestTemplate restTemplate;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.naver.maps.client-id:}")
    private String clientId;

    @Value("${app.naver.maps.client-secret:}")
    private String clientSecret;

    @Value("${app.naver.maps.search-url:https://openapi.naver.com/v1/search/local.json}")
    private String searchUrl;

    @Value("${app.naver.maps.geocode-url:https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode}")
    private String geocodeUrl;

    @Value("${app.naver.maps.reverse-geocode-url:https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc}")
    private String reverseGeocodeUrl;

    @Value("${app.naver.maps.direction-url:https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving}")
    private String directionUrl;

    @Override
    @Cacheable(value = "naverPlaces", key = "#query + '_' + #latitude + '_' + #longitude + '_' + #radius")
    public List<PlaceResponse> searchPlaces(String query, Double latitude, Double longitude, Integer radius) {
        log.info("Searching places with query: {} near location ({}, {}) within {}m", 
                query, latitude, longitude, radius);

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(searchUrl)
                    .queryParam("query", query)
                    .queryParam("display", 20)
                    .queryParam("start", 1)
                    .queryParam("sort", "random");

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            List<PlaceResponse> places = parseSearchResponse(response.getBody());
            
            // Filter by radius if coordinates provided
            if (latitude != null && longitude != null && radius != null) {
                places = filterPlacesByRadius(places, latitude, longitude, radius);
            }

            // Save/update places in database
            savePlacesToDatabase(places);

            log.info("Found {} places for query: {}", places.size(), query);
            return places;

        } catch (Exception e) {
            log.error("Error searching places with Naver Maps API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "naverPlaceDetails", key = "#naverPlaceId")
    public PlaceResponse getPlaceDetails(String naverPlaceId) {
        log.info("Getting place details for Naver Place ID: {}", naverPlaceId);

        try {
            // First check if we have the place in our database
            Optional<Place> existingPlace = placeRepository.findByNaverPlaceId(naverPlaceId);
            if (existingPlace.isPresent()) {
                return mapPlaceToResponse(existingPlace.get());
            }

            // If not in database, search via Naver API
            // Note: Naver doesn't have a direct place details API like Google
            // We would need to use the search API to find the place
            log.warn("Place details not available for Naver Place ID: {}", naverPlaceId);
            return null;

        } catch (Exception e) {
            log.error("Error getting place details for ID {}: {}", naverPlaceId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Cacheable(value = "naverPlacesByCategory", key = "#category + '_' + #latitude + '_' + #longitude + '_' + #radius")
    public List<PlaceResponse> searchByCategory(String category, Double latitude, Double longitude, Integer radius, Integer limit) {
        log.info("Searching {} places near ({}, {}) within {}m", category, latitude, longitude, radius);

        try {
            // Map category to Korean search terms
            String searchQuery = mapCategoryToKoreanQuery(category);
            
            List<PlaceResponse> places = searchPlaces(searchQuery, latitude, longitude, radius);
            
            // Limit results
            if (limit != null && places.size() > limit) {
                places = places.subList(0, limit);
            }

            return places;

        } catch (Exception e) {
            log.error("Error searching places by category {}: {}", category, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "naverPopularPlaces", key = "#latitude + '_' + #longitude + '_' + #radius + '_' + #category")
    public List<PlaceResponse> getPopularPlaces(Double latitude, Double longitude, Integer radius, String category) {
        log.info("Getting popular places near ({}, {}) for category: {}", latitude, longitude, category);

        try {
            List<PlaceResponse> places;
            
            if (category != null) {
                places = searchByCategory(category, latitude, longitude, radius, 10);
            } else {
                // Search for popular general places
                places = searchPlaces("맛집 관광지", latitude, longitude, radius);
            }

            // Sort by rating/popularity if available
            return places.stream()
                    .sorted((p1, p2) -> {
                        Double rating1 = p1.getAverageRating() != null ? p1.getAverageRating() : 0.0;
                        Double rating2 = p2.getAverageRating() != null ? p2.getAverageRating() : 0.0;
                        return Double.compare(rating2, rating1);
                    })
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting popular places: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "naverGeocode", key = "#address")
    public Map<String, Double> geocodeAddress(String address) {
        log.info("Geocoding address: {}", address);

        try {
            HttpHeaders headers = createGeoHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(geocodeUrl)
                    .queryParam("query", address);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return parseGeocodeResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error geocoding address {}: {}", address, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    @Cacheable(value = "naverReverseGeocode", key = "#latitude + '_' + #longitude")
    public Map<String, Object> reverseGeocode(Double latitude, Double longitude) {
        log.info("Reverse geocoding coordinates: ({}, {})", latitude, longitude);

        try {
            HttpHeaders headers = createGeoHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reverseGeocodeUrl)
                    .queryParam("coords", longitude + "," + latitude)
                    .queryParam("sourcecrs", "epsg:4326")
                    .queryParam("targetcrs", "epsg:4326")
                    .queryParam("orders", "roadaddr,addr");

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return parseReverseGeocodeResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error reverse geocoding coordinates ({}, {}): {}", latitude, longitude, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getDirections(Double startLat, Double startLng, Double endLat, Double endLng, String option) {
        log.info("Getting directions from ({}, {}) to ({}, {})", startLat, startLng, endLat, endLng);

        try {
            HttpHeaders headers = createGeoHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String start = startLng + "," + startLat;
            String goal = endLng + "," + endLat;
            String optionParam = option != null ? option : "trafast";

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(directionUrl)
                    .queryParam("start", start)
                    .queryParam("goal", goal)
                    .queryParam("option", optionParam);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return parseDirectionResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error getting directions: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public List<Map<String, Object>> getNearbyTransportation(Double latitude, Double longitude, Integer radius) {
        log.info("Getting nearby transportation for ({}, {}) within {}m", latitude, longitude, radius);

        try {
            // Search for subway stations and bus stops
            List<Map<String, Object>> transportation = new ArrayList<>();
            
            // Search subway stations
            List<PlaceResponse> subwayStations = searchPlaces("지하철역", latitude, longitude, radius);
            transportation.addAll(subwayStations.stream()
                    .map(this::mapPlaceToTransportation)
                    .collect(Collectors.toList()));

            // Search bus stops
            List<PlaceResponse> busStops = searchPlaces("버스정류장", latitude, longitude, radius);
            transportation.addAll(busStops.stream()
                    .map(this::mapPlaceToTransportation)
                    .collect(Collectors.toList()));

            return transportation.stream()
                    .limit(20)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting nearby transportation: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getServiceHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test a simple search API call
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(searchUrl)
                    .queryParam("query", "서울")
                    .queryParam("display", 1);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            health.put("status", "healthy");
            health.put("apiEndpoint", searchUrl);
            health.put("responseCode", response.getStatusCode().value());
            health.put("lastChecked", new Date());

        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("lastChecked", new Date());
            log.error("Naver Maps service health check failed: {}", e.getMessage());
        }

        return health;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);
        return headers;
    }

    private HttpHeaders createGeoHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
        headers.set("X-NCP-APIGW-API-KEY", clientSecret);
        return headers;
    }

    private List<PlaceResponse> parseSearchResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("items");
            
            List<PlaceResponse> places = new ArrayList<>();
            
            for (JsonNode item : items) {
                PlaceResponse place = PlaceResponse.builder()
                        .name(cleanText(item.path("title").asText()))
                        .description(cleanText(item.path("description").asText()))
                        .address(item.path("address").asText())
                        .category(item.path("category").asText())
                        .phoneNumber(item.path("telephone").asText())
                        .website(item.path("link").asText())
                        .build();
                
                // Parse coordinates if available in roadAddress
                String roadAddress = item.path("roadAddress").asText();
                if (!roadAddress.isEmpty()) {
                    // This would require additional geocoding to get exact coordinates
                    // For now, we'll skip coordinate parsing from search results
                }
                
                places.add(place);
            }
            
            return places;
            
        } catch (Exception e) {
            log.error("Error parsing search response: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Double> parseGeocodeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode addresses = root.path("addresses");
            
            if (addresses.isArray() && addresses.size() > 0) {
                JsonNode address = addresses.get(0);
                double lat = address.path("y").asDouble();
                double lng = address.path("x").asDouble();
                
                Map<String, Double> coordinates = new HashMap<>();
                coordinates.put("latitude", lat);
                coordinates.put("longitude", lng);
                return coordinates;
            }
            
        } catch (Exception e) {
            log.error("Error parsing geocode response: {}", e.getMessage(), e);
        }
        
        return Collections.emptyMap();
    }

    private Map<String, Object> parseReverseGeocodeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");
            
            Map<String, Object> addressInfo = new HashMap<>();
            
            if (results.isArray() && results.size() > 0) {
                for (JsonNode result : results) {
                    String name = result.path("name").asText();
                    JsonNode region = result.path("region");
                    
                    if ("roadaddr".equals(name)) {
                        JsonNode land = result.path("land");
                        addressInfo.put("roadAddress", land.path("name").asText());
                        addressInfo.put("buildingNumber", land.path("number1").asText());
                    } else if ("addr".equals(name)) {
                        addressInfo.put("jibunAddress", 
                                region.path("area1").path("name").asText() + " " +
                                region.path("area2").path("name").asText() + " " +
                                region.path("area3").path("name").asText());
                    }
                }
            }
            
            return addressInfo;
            
        } catch (Exception e) {
            log.error("Error parsing reverse geocode response: {}", e.getMessage(), e);
        }
        
        return Collections.emptyMap();
    }

    private Map<String, Object> parseDirectionResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode route = root.path("route");
            
            if (route.isArray() && route.size() > 0) {
                JsonNode firstRoute = route.get(0);
                JsonNode summary = firstRoute.path("summary");
                
                Map<String, Object> directions = new HashMap<>();
                directions.put("distance", summary.path("distance").asInt()); // meters
                directions.put("duration", summary.path("duration").asInt()); // milliseconds
                directions.put("tollFare", summary.path("tollFare").asInt());
                directions.put("fuelPrice", summary.path("fuelPrice").asInt());
                
                return directions;
            }
            
        } catch (Exception e) {
            log.error("Error parsing direction response: {}", e.getMessage(), e);
        }
        
        return Collections.emptyMap();
    }

    private String cleanText(String text) {
        if (text == null) return null;
        
        // Remove HTML tags from Naver API responses
        return text.replaceAll("<[^>]*>", "").trim();
    }

    private String mapCategoryToKoreanQuery(String category) {
        Map<String, String> categoryMap = Map.of(
                "restaurant", "맛집 식당",
                "cafe", "카페",
                "tourist_attraction", "관광지 명소",
                "hotel", "호텔 숙박",
                "shopping", "쇼핑몰 백화점",
                "transportation", "지하철역 버스정류장",
                "entertainment", "오락실 노래방",
                "nature", "공원 산"
        );
        
        return categoryMap.getOrDefault(category.toLowerCase(), category);
    }

    private List<PlaceResponse> filterPlacesByRadius(List<PlaceResponse> places, Double centerLat, Double centerLng, Integer radius) {
        return places.stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .filter(place -> {
                    double distance = calculateDistance(centerLat, centerLng, place.getLatitude(), place.getLongitude());
                    return distance <= radius;
                })
                .collect(Collectors.toList());
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    private void savePlacesToDatabase(List<PlaceResponse> places) {
        // This is a simplified implementation
        // In a real application, you would save/update places in the database
        // with proper entity mapping and duplicate handling
    }

    private PlaceResponse mapPlaceToResponse(Place place) {
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
                .averageRating(place.getRating())
                .reviewCount(place.getReviewCount())
                .images(place.getImages())
                .tags(place.getTags())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }

    private Map<String, Object> mapPlaceToTransportation(PlaceResponse place) {
        Map<String, Object> transport = new HashMap<>();
        transport.put("name", place.getName());
        transport.put("type", place.getCategory().contains("지하철") ? "subway" : "bus");
        transport.put("latitude", place.getLatitude());
        transport.put("longitude", place.getLongitude());
        transport.put("address", place.getAddress());
        return transport;
    }
}