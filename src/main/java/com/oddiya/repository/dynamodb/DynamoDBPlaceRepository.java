package com.oddiya.repository.dynamodb;

import com.oddiya.entity.Place;
import com.oddiya.entity.dynamodb.DynamoDBPlace;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.converter.DynamoDBConverters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of PlaceRepository interface.
 * Provides place database operations with geospatial query support using geohash.
 */
@Repository
@Slf4j
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
public class DynamoDBPlaceRepository extends AbstractDynamoDBRepository<DynamoDBPlace, String> 
                                     implements PlaceRepository {
    
    private final DynamoDBConverters converters;
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    public DynamoDBPlaceRepository(DynamoDbEnhancedClient enhancedClient,
                                  DynamoDbClient client,
                                  DynamoDBConverters converters,
                                  String tableName) {
        super(enhancedClient, client, DynamoDBPlace.class, tableName != null ? tableName : "oddiya_places");
        this.converters = converters;
    }
    
    @Override
    protected Key buildKey(String id) {
        return Key.builder().partitionValue(id).build();
    }
    
    // JPA Repository methods implementation
    
    @Override
    public <S extends Place> S save(S entity) {
        try {
            DynamoDBPlace dynamoEntity = converters.toPlaceDynamoDB(entity);
            // Generate geohash for geospatial queries
            if (entity.getLatitude() != null && entity.getLongitude() != null) {
                String geohash = generateGeohash(entity.getLatitude(), entity.getLongitude(), 7);
                dynamoEntity.setGeohash(geohash);
            }
            DynamoDBPlace saved = save(dynamoEntity);
            return (S) converters.toPlaceJPA(saved);
        } catch (Exception e) {
            log.error("Error saving place {}: {}", entity.getId(), e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to save place", e);
        }
    }
    
    @Override
    public <S extends Place> List<S> saveAll(Iterable<S> entities) {
        try {
            List<DynamoDBPlace> dynamoEntities = 
                ((List<S>) entities).stream()
                                   .map(entity -> {
                                       DynamoDBPlace dynamoEntity = converters.toPlaceDynamoDB(entity);
                                       if (entity.getLatitude() != null && entity.getLongitude() != null) {
                                           String geohash = generateGeohash(entity.getLatitude(), entity.getLongitude(), 7);
                                           dynamoEntity.setGeohash(geohash);
                                       }
                                       return dynamoEntity;
                                   })
                                   .collect(Collectors.toList());
            
            List<DynamoDBPlace> saved = saveAll(dynamoEntities);
            
            return (List<S>) saved.stream()
                                  .map(converters::toPlaceJPA)
                                  .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error batch saving places: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save places", e);
        }
    }
    
    @Override
    public Optional<Place> findById(String id) {
        try {
            Optional<DynamoDBPlace> result = super.findById(id);
            return result.map(converters::toPlaceJPA);
        } catch (Exception e) {
            log.error("Error finding place by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find place by id", e);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
    
    @Override
    public List<Place> findAll() {
        try {
            return scanAll().stream()
                           .flatMap(page -> page.items().stream())
                           .filter(place -> !Boolean.TRUE.equals(place.getIsDeleted()))
                           .map(converters::toPlaceJPA)
                           .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding all places: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find all places", e);
        }
    }
    
    @Override
    public List<Place> findAllById(Iterable<String> ids) {
        try {
            List<DynamoDBPlace> results = super.findAllById((List<String>) ids);
            return results.stream()
                         .filter(place -> !Boolean.TRUE.equals(place.getIsDeleted()))
                         .map(converters::toPlaceJPA)
                         .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding places by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places by ids", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            // Soft delete - mark as deleted instead of physical deletion
            Optional<DynamoDBPlace> placeOpt = super.findById(id);
            if (placeOpt.isPresent()) {
                DynamoDBPlace place = placeOpt.get();
                place.setIsDeleted(true);
                place.setDeletedAt(java.time.Instant.now());
                save(place);
            }
        } catch (Exception e) {
            log.error("Error deleting place by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete place by id", e);
        }
    }
    
    @Override
    public void delete(Place entity) {
        deleteById(entity.getId());
    }
    
    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        try {
            List<DynamoDBPlace> toDelete = super.findAllById((List<String>) ids);
            toDelete.forEach(place -> {
                place.setIsDeleted(true);
                place.setDeletedAt(java.time.Instant.now());
            });
            saveAll(toDelete);
        } catch (Exception e) {
            log.error("Error batch deleting places by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete places by ids", e);
        }
    }
    
    @Override
    public void deleteAll(Iterable<? extends Place> entities) {
        try {
            List<DynamoDBPlace> dynamoEntities = 
                ((List<Place>) entities).stream()
                                        .map(converters::toPlaceDynamoDB)
                                        .collect(Collectors.toList());
            
            dynamoEntities.forEach(place -> {
                place.setIsDeleted(true);
                place.setDeletedAt(java.time.Instant.now());
            });
            
            saveAll(dynamoEntities);
        } catch (Exception e) {
            log.error("Error batch deleting places: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete places", e);
        }
    }
    
    @Override
    public void deleteAll() {
        try {
            List<Place> allPlaces = findAll();
            deleteAll(allPlaces);
        } catch (Exception e) {
            log.error("Error deleting all places: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete all places", e);
        }
    }
    
    // Custom repository methods implementation
    
    @Override
    public Optional<Place> findByNaverPlaceId(String naverPlaceId) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#naverPlaceId = :naverPlaceId AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#naverPlaceId", "naverPlaceId")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":naverPlaceId", AttributeValue.fromS(naverPlaceId))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            Optional<DynamoDBPlace> firstResult = results.stream()
                                                         .flatMap(page -> page.items().stream())
                                                         .findFirst();
            
            return firstResult.map(converters::toPlaceJPA);
            
        } catch (Exception e) {
            log.error("Error finding place by naverPlaceId {}: {}", naverPlaceId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find place by naverPlaceId", e);
        }
    }
    
    @Override
    public boolean existsByNaverPlaceId(String naverPlaceId) {
        return findByNaverPlaceId(naverPlaceId).isPresent();
    }
    
    @Override
    public Page<Place> searchPlaces(String query, Pageable pageable) {
        try {
            String lowercaseQuery = query.toLowerCase();
            
            Expression filterExpression = Expression.builder()
                .expression("(contains(#name, :query) OR contains(#address, :query) OR contains(#description, :query)) " +
                           "AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#name", "name")
                .putExpressionName("#address", "address")
                .putExpressionName("#description", "description")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":query", AttributeValue.fromS(lowercaseQuery))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error searching places with query {}: {}", query, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to search places", e);
        }
    }
    
    @Override
    public List<Place> findNearbyPlaces(Double latitude, Double longitude, Integer radiusInMeters) {
        try {
            // Generate geohash for the search center
            String centerGeohash = generateGeohash(latitude, longitude, 7);
            
            // Get geohash prefixes for nearby search
            List<String> geohashPrefixes = getGeohashPrefixes(centerGeohash, radiusInMeters);
            
            List<Place> nearbyPlaces = geohashPrefixes.stream()
                .flatMap(prefix -> findPlacesByGeohashPrefix(prefix).stream())
                .filter(place -> {
                    if (place.getLatitude() == null || place.getLongitude() == null) {
                        return false;
                    }
                    double distance = calculateDistance(latitude, longitude, 
                                                      place.getLatitude(), place.getLongitude());
                    return distance * 1000 <= radiusInMeters; // Convert km to meters
                })
                .sorted((p1, p2) -> {
                    double dist1 = calculateDistance(latitude, longitude, p1.getLatitude(), p1.getLongitude());
                    double dist2 = calculateDistance(latitude, longitude, p2.getLatitude(), p2.getLongitude());
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
            
            return nearbyPlaces;
            
        } catch (Exception e) {
            log.error("Error finding nearby places at ({}, {}) within {} meters: {}", 
                     latitude, longitude, radiusInMeters, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find nearby places", e);
        }
    }
    
    private List<Place> findPlacesByGeohashPrefix(String geohashPrefix) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("begins_with(#geohash, :prefix) AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#geohash", "geohash")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":prefix", AttributeValue.fromS(geohashPrefix))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .map(converters::toPlaceJPA)
                         .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding places by geohash prefix {}: {}", geohashPrefix, e.getMessage(), e);
            return List.of();
        }
    }
    
    @Override
    public Page<Place> findByCategoryAndIsDeletedFalse(String category, Pageable pageable) {
        return findByCategory(category, pageable);
    }
    
    @Override
    public Page<Place> findByCategoriesIn(List<String> categories, Pageable pageable) {
        try {
            Expression filterExpression = categories.stream()
                .map(category -> "#category = :category" + categories.indexOf(category))
                .collect(Collectors.joining(" OR ", "(", ") AND (attribute_not_exists(#deleted) OR #deleted = :false)"));
            
            Expression.Builder builder = Expression.builder()
                .expression(filterExpression)
                .putExpressionName("#category", "category")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":false", AttributeValue.fromBool(false));
            
            for (int i = 0; i < categories.size(); i++) {
                builder.putExpressionValue(":category" + i, AttributeValue.fromS(categories.get(i)));
            }
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(builder.build());
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error finding places by categories {}: {}", categories, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places by categories", e);
        }
    }
    
    @Override
    public List<Place> findTopPopularPlaces(Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#isVerified = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#isVerified", "isVerified")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .map(converters::toPlaceJPA)
                         .sorted((p1, p2) -> Double.compare(p2.getPopularityScore(), p1.getPopularityScore()))
                         .limit(pageable.getPageSize())
                         .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error finding top popular places: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find top popular places", e);
        }
    }
    
    @Override
    public Page<Place> findByTags(List<String> tags, Pageable pageable) {
        try {
            // Build filter for tags (assuming tags are stored as a list attribute)
            String tagsFilter = tags.stream()
                .map(tag -> "contains(#tags, :tag" + tags.indexOf(tag) + ")")
                .collect(Collectors.joining(" OR "));
            
            Expression.Builder builder = Expression.builder()
                .expression("(" + tagsFilter + ") AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#tags", "tags")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":false", AttributeValue.fromBool(false));
            
            for (int i = 0; i < tags.size(); i++) {
                builder.putExpressionValue(":tag" + i, AttributeValue.fromS(tags.get(i)));
            }
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(builder.build());
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error finding places by tags {}: {}", tags, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places by tags", e);
        }
    }
    
    @Override
    public Page<Place> findByMinimumRating(Double minRating, Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#rating >= :minRating AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#rating", "rating")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":minRating", AttributeValue.fromN(minRating.toString()))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .sorted((p1, p2) -> Double.compare(p2.getRating(), p1.getRating()))
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error finding places by minimum rating {}: {}", minRating, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places by minimum rating", e);
        }
    }
    
    @Override
    public Page<Place> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
            String name, String address, Pageable pageable) {
        try {
            String nameLower = name.toLowerCase();
            String addressLower = address.toLowerCase();
            
            Expression filterExpression = Expression.builder()
                .expression("(contains(#name, :name) OR contains(#address, :address)) " +
                           "AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#name", "name")
                .putExpressionName("#address", "address")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":name", AttributeValue.fromS(nameLower))
                .putExpressionValue(":address", AttributeValue.fromS(addressLower))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error searching places by name {} or address {}: {}", 
                     name, address, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to search places", e);
        }
    }
    
    @Override
    public Page<Place> findByCategory(String category, Pageable pageable) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(category).build());
            
            Expression filterExpression = buildNotDeletedExpression();
            
            PageIterable<DynamoDBPlace> results = queryIndexWithFilter("category-index", 
                                                                      queryConditional, 
                                                                      filterExpression);
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error finding places by category {}: {}", category, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places by category", e);
        }
    }
    
    @Override
    public Page<Place> findAllByOrderByPopularityScoreDesc(Pageable pageable) {
        try {
            Expression filterExpression = buildNotDeletedExpression();
            
            PageIterable<DynamoDBPlace> results = scanWithFilter(filterExpression);
            
            List<Place> places = results.stream()
                                       .flatMap(page -> page.items().stream())
                                       .map(converters::toPlaceJPA)
                                       .sorted((p1, p2) -> Double.compare(p2.getPopularityScore(), p1.getPopularityScore()))
                                       .collect(Collectors.toList());
            
            return createPage(places, pageable, places.size());
            
        } catch (Exception e) {
            log.error("Error finding places ordered by popularity: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find places ordered by popularity", e);
        }
    }
    
    // Geospatial utility methods
    
    private String generateGeohash(double latitude, double longitude, int precision) {
        // Simple geohash implementation - in production, use a library like ch.hsr:geohash
        String base32 = "0123456789bcdefghjkmnpqrstuvwxyz";
        boolean isLatitude = true;
        double latMin = -90.0, latMax = 90.0;
        double lonMin = -180.0, lonMax = 180.0;
        int bit = 0;
        int idx = 0;
        StringBuilder geohash = new StringBuilder();
        
        while (geohash.length() < precision) {
            if (isLatitude) {
                double mid = (latMin + latMax) / 2;
                if (latitude >= mid) {
                    idx = (idx << 1) + 1;
                    latMin = mid;
                } else {
                    idx = idx << 1;
                    latMax = mid;
                }
            } else {
                double mid = (lonMin + lonMax) / 2;
                if (longitude >= mid) {
                    idx = (idx << 1) + 1;
                    lonMin = mid;
                } else {
                    idx = idx << 1;
                    lonMax = mid;
                }
            }
            
            isLatitude = !isLatitude;
            if (++bit == 5) {
                geohash.append(base32.charAt(idx));
                bit = 0;
                idx = 0;
            }
        }
        
        return geohash.toString();
    }
    
    private List<String> getGeohashPrefixes(String centerGeohash, int radiusInMeters) {
        // Simplified approach - in production, calculate neighbor geohashes based on radius
        List<String> prefixes = List.of(
            centerGeohash.substring(0, Math.min(5, centerGeohash.length()))
        );
        
        // Add neighboring geohash prefixes for better coverage
        if (radiusInMeters > 1000) { // For larger radius, use shorter prefixes
            prefixes = List.of(
                centerGeohash.substring(0, Math.min(4, centerGeohash.length()))
            );
        }
        
        return prefixes;
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
}