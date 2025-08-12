package com.oddiya.repository.dynamodb.enhanced;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced DynamoDB utilities and query builders for improved repository operations.
 * Provides advanced query construction, geospatial calculations, and optimization helpers.
 */
@Component
@Slf4j
public class DynamoDBRepositoryEnhancements {

    // ========== GEOSPATIAL QUERY ENHANCEMENTS ==========
    
    /**
     * Calculate accurate distance between two geographic points using Haversine formula
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth's radius in kilometers
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // Distance in kilometers
    }
    
    /**
     * Generate geohash from coordinates for efficient spatial indexing
     */
    public String generateGeohash(double latitude, double longitude, int precision) {
        try {
            // Simple geohash implementation - in production use libraries like ch.hsr.geohash
            return String.format("%." + precision + "f,%." + precision + "f", latitude, longitude);
        } catch (Exception e) {
            log.warn("Failed to generate geohash for lat={}, lon={}: {}", latitude, longitude, e.getMessage());
            return String.format("%.6f,%.6f", latitude, longitude);
        }
    }
    
    /**
     * Get geohash prefixes for proximity search
     */
    public List<String> getGeohashPrefixes(double latitude, double longitude, double radiusKm, int precision) {
        List<String> prefixes = new ArrayList<>();
        
        // Calculate bounding box
        double latRange = radiusKm / 111.32; // Rough conversion: 1 degree latitude ≈ 111.32 km
        double lonRange = radiusKm / (111.32 * Math.cos(Math.toRadians(latitude)));
        
        // Generate geohash prefixes for the bounding box
        for (double lat = latitude - latRange; lat <= latitude + latRange; lat += latRange / 2) {
            for (double lon = longitude - lonRange; lon <= longitude + lonRange; lon += lonRange / 2) {
                String hash = generateGeohash(lat, lon, precision - 1);
                if (!prefixes.contains(hash)) {
                    prefixes.add(hash);
                }
            }
        }
        
        return prefixes;
    }
    
    // ========== ADVANCED QUERY BUILDERS ==========
    
    /**
     * Build complex filter expressions with multiple conditions
     */
    public Expression buildComplexFilterExpression(Map<String, Object> conditions, String operator) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        
        List<String> expressions = new ArrayList<>();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        
        int valueCounter = 0;
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String nameKey = "#" + key;
            String valueKey = ":val" + valueCounter++;
            
            expressionNames.put(nameKey, key);
            expressionValues.put(valueKey, convertToAttributeValue(value));
            
            if (value instanceof List) {
                expressions.add(nameKey + " IN (" + valueKey + ")");
            } else if (key.endsWith("_contains")) {
                String actualKey = key.replace("_contains", "");
                expressionNames.put(nameKey, actualKey);
                expressions.add("contains(" + nameKey + ", " + valueKey + ")");
            } else if (key.endsWith("_gte")) {
                String actualKey = key.replace("_gte", "");
                expressionNames.put(nameKey, actualKey);
                expressions.add(nameKey + " >= " + valueKey);
            } else if (key.endsWith("_lte")) {
                String actualKey = key.replace("_lte", "");
                expressionNames.put(nameKey, actualKey);
                expressions.add(nameKey + " <= " + valueKey);
            } else {
                expressions.add(nameKey + " = " + valueKey);
            }
        }
        
        String finalExpression = String.join(" " + operator.toUpperCase() + " ", expressions);
        
        return Expression.builder()
                .expression(finalExpression)
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();
    }
    
    /**
     * Build pagination expressions with LastEvaluatedKey support
     */
    public Expression buildPaginationExpression(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        
        // This would be used with query operations that support ExclusiveStartKey
        log.debug("Building pagination with lastEvaluatedKey: {}", lastEvaluatedKey.keySet());
        return null; // DynamoDB Enhanced Client handles this automatically
    }
    
    /**
     * Build text search expression for multiple fields
     */
    public Expression buildTextSearchExpression(String searchTerm, List<String> searchFields) {
        if (searchTerm == null || searchTerm.trim().isEmpty() || searchFields == null || searchFields.isEmpty()) {
            return null;
        }
        
        String lowerSearchTerm = searchTerm.toLowerCase();
        List<String> conditions = new ArrayList<>();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        
        for (int i = 0; i < searchFields.size(); i++) {
            String field = searchFields.get(i);
            String nameKey = "#field" + i;
            String valueKey = ":searchTerm" + i;
            
            expressionNames.put(nameKey, field);
            expressionValues.put(valueKey, AttributeValue.fromS(lowerSearchTerm));
            
            conditions.add("contains(" + nameKey + ", " + valueKey + ")");
        }
        
        String finalExpression = String.join(" OR ", conditions);
        
        return Expression.builder()
                .expression(finalExpression)
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();
    }
    
    /**
     * Build date range expression
     */
    public Expression buildDateRangeExpression(String dateField, String startDate, String endDate) {
        if (dateField == null || (startDate == null && endDate == null)) {
            return null;
        }
        
        List<String> conditions = new ArrayList<>();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        
        String nameKey = "#dateField";
        expressionNames.put(nameKey, dateField);
        
        if (startDate != null) {
            String startValueKey = ":startDate";
            expressionValues.put(startValueKey, AttributeValue.fromS(startDate));
            conditions.add(nameKey + " >= " + startValueKey);
        }
        
        if (endDate != null) {
            String endValueKey = ":endDate";
            expressionValues.put(endValueKey, AttributeValue.fromS(endDate));
            conditions.add(nameKey + " <= " + endValueKey);
        }
        
        String finalExpression = String.join(" AND ", conditions);
        
        return Expression.builder()
                .expression(finalExpression)
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Convert various data types to AttributeValue
     */
    private AttributeValue convertToAttributeValue(Object value) {
        if (value == null) {
            return AttributeValue.fromNul(true);
        } else if (value instanceof String) {
            return AttributeValue.fromS((String) value);
        } else if (value instanceof Number) {
            return AttributeValue.fromN(value.toString());
        } else if (value instanceof Boolean) {
            return AttributeValue.fromBool((Boolean) value);
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return AttributeValue.fromSs(list.stream().map(Object::toString).collect(Collectors.toList()));
        } else {
            return AttributeValue.fromS(value.toString());
        }
    }
    
    /**
     * Combine multiple expressions with AND/OR logic
     */
    public Expression combineExpressions(List<Expression> expressions, String operator) {
        if (expressions == null || expressions.isEmpty()) {
            return null;
        }
        
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        
        List<String> combinedExpressions = new ArrayList<>();
        Map<String, String> combinedNames = new HashMap<>();
        Map<String, AttributeValue> combinedValues = new HashMap<>();
        
        for (Expression expr : expressions) {
            if (expr != null) {
                combinedExpressions.add("(" + expr.expression() + ")");
                if (expr.expressionNames() != null) {
                    combinedNames.putAll(expr.expressionNames());
                }
                if (expr.expressionValues() != null) {
                    combinedValues.putAll(expr.expressionValues());
                }
            }
        }
        
        if (combinedExpressions.isEmpty()) {
            return null;
        }
        
        String finalExpression = String.join(" " + operator.toUpperCase() + " ", combinedExpressions);
        
        return Expression.builder()
                .expression(finalExpression)
                .expressionNames(combinedNames)
                .expressionValues(combinedValues)
                .build();
    }
    
    /**
     * Build conditional check expressions for optimistic locking
     */
    public Expression buildOptimisticLockExpression(String versionField, Long expectedVersion) {
        if (versionField == null || expectedVersion == null) {
            return null;
        }
        
        return Expression.builder()
                .expression("#version = :expectedVersion")
                .putExpressionName("#version", versionField)
                .putExpressionValue(":expectedVersion", AttributeValue.fromN(expectedVersion.toString()))
                .build();
    }
    
    /**
     * Build not deleted filter expression
     */
    public Expression buildNotDeletedExpression() {
        return Expression.builder()
                .expression("attribute_not_exists(#deleted) OR #deleted = :false")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
    }
    
    /**
     * Calculate optimal batch size based on item size
     */
    public int calculateOptimalBatchSize(int estimatedItemSizeBytes, int maxBatchSize) {
        // DynamoDB has a 400KB limit for batch operations
        final int MAX_BATCH_SIZE_BYTES = 400 * 1024; // 400KB
        final int SAFETY_FACTOR = (int) (0.8 * MAX_BATCH_SIZE_BYTES); // 80% of limit for safety
        
        if (estimatedItemSizeBytes <= 0) {
            return Math.min(maxBatchSize, 25); // Default to 25 for unknown size
        }
        
        int calculatedBatchSize = SAFETY_FACTOR / estimatedItemSizeBytes;
        return Math.min(Math.max(calculatedBatchSize, 1), maxBatchSize);
    }
    
    /**
     * Validate DynamoDB key format
     */
    public boolean isValidDynamoDBKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        // DynamoDB key constraints
        final int MAX_KEY_LENGTH = 2048;
        
        return key.length() <= MAX_KEY_LENGTH &&
               !key.contains("\u0000") && // No null characters
               key.trim().equals(key); // No leading/trailing whitespace
    }
    
    /**
     * Generate consistent sort key for composite operations
     */
    public String generateSortKey(String... components) {
        if (components == null || components.length == 0) {
            return "";
        }
        
        return Arrays.stream(components)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("#"));
    }
}