package com.oddiya.repository.dynamodb.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Geospatial utility functions for DynamoDB place queries.
 * Provides geohash generation, distance calculations, and proximity searches.
 */
@Component
@Slf4j
public class GeospatialUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final Map<Character, Integer> BASE32_MAP = new HashMap<>();
    
    static {
        for (int i = 0; i < BASE32.length(); i++) {
            BASE32_MAP.put(BASE32.charAt(i), i);
        }
    }

    /**
     * Calculate distance between two points using the Haversine formula
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Generate a geohash string for given coordinates
     */
    public String generateGeohash(double latitude, double longitude, int precision) {
        if (precision <= 0 || precision > 12) {
            precision = 9; // Default precision
        }
        
        double[] latRange = {-90.0, 90.0};
        double[] lonRange = {-180.0, 180.0};
        
        StringBuilder geohash = new StringBuilder();
        int bits = 0;
        int bit = 0;
        boolean evenBit = true;
        
        while (geohash.length() < precision) {
            if (evenBit) {
                // Process longitude
                double mid = (lonRange[0] + lonRange[1]) / 2;
                if (longitude > mid) {
                    bit = (bit << 1) | 1;
                    lonRange[0] = mid;
                } else {
                    bit = bit << 1;
                    lonRange[1] = mid;
                }
            } else {
                // Process latitude
                double mid = (latRange[0] + latRange[1]) / 2;
                if (latitude > mid) {
                    bit = (bit << 1) | 1;
                    latRange[0] = mid;
                } else {
                    bit = bit << 1;
                    latRange[1] = mid;
                }
            }
            
            evenBit = !evenBit;
            bits++;
            
            if (bits == 5) {
                geohash.append(BASE32.charAt(bit));
                bits = 0;
                bit = 0;
            }
        }
        
        return geohash.toString();
    }

    /**
     * Get geohash neighbors for proximity search
     */
    public Set<String> getGeohashNeighbors(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<String> neighbors = new HashSet<>();
        
        // Add the original geohash
        neighbors.add(geohash);
        
        // Generate neighbor geohashes
        try {
            // Get all 8 neighboring cells
            String[] directions = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            
            for (String direction : directions) {
                String neighbor = calculateNeighbor(geohash, direction);
                if (neighbor != null && !neighbor.isEmpty()) {
                    neighbors.add(neighbor);
                }
            }
            
        } catch (Exception e) {
            log.warn("Error generating geohash neighbors for {}: {}", geohash, e.getMessage());
        }
        
        return neighbors;
    }

    /**
     * Get geohash prefixes for area search
     */
    public List<String> getGeohashPrefixes(double latitude, double longitude, double radiusKm, int precision) {
        String centerGeohash = generateGeohash(latitude, longitude, precision);
        Set<String> prefixes = new HashSet<>();
        
        // Calculate approximate degree ranges for the radius
        double latRange = radiusKm / 111.32; // 1 degree lat ≈ 111.32 km
        double lonRange = radiusKm / (111.32 * Math.cos(Math.toRadians(latitude)));
        
        // Generate grid of points and their geohashes
        int steps = Math.max(2, (int) Math.ceil(radiusKm / 10)); // Adjust step size based on radius
        
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                double testLat = latitude + (i * latRange / steps);
                double testLon = longitude + (j * lonRange / steps);
                
                // Check if point is within radius
                if (calculateDistance(latitude, longitude, testLat, testLon) <= radiusKm) {
                    String geohash = generateGeohash(testLat, testLon, precision);
                    String prefix = geohash.substring(0, Math.min(precision - 1, geohash.length()));
                    prefixes.add(prefix);
                }
            }
        }
        
        return new ArrayList<>(prefixes);
    }

    /**
     * Calculate bounding box for a given center point and radius
     */
    public BoundingBox calculateBoundingBox(double centerLat, double centerLon, double radiusKm) {
        double latOffset = radiusKm / 111.32; // 1 degree latitude ≈ 111.32 km
        double lonOffset = radiusKm / (111.32 * Math.cos(Math.toRadians(centerLat)));
        
        return new BoundingBox(
            centerLat - latOffset,  // minLat
            centerLat + latOffset,  // maxLat
            centerLon - lonOffset,  // minLon
            centerLon + lonOffset   // maxLon
        );
    }

    /**
     * Check if a point is within a bounding box
     */
    public boolean isPointInBoundingBox(double lat, double lon, BoundingBox bbox) {
        return lat >= bbox.getMinLat() && lat <= bbox.getMaxLat() &&
               lon >= bbox.getMinLon() && lon <= bbox.getMaxLon();
    }

    /**
     * Filter locations by distance
     */
    public <T extends LocationProvider> List<T> filterByDistance(
            List<T> locations, 
            double centerLat, 
            double centerLon, 
            double radiusKm) {
        
        return locations.stream()
                .filter(location -> {
                    double distance = calculateDistance(
                        centerLat, centerLon,
                        location.getLatitude(), location.getLongitude()
                    );
                    return distance <= radiusKm;
                })
                .collect(Collectors.toList());
    }

    /**
     * Sort locations by distance from center point
     */
    public <T extends LocationProvider> List<T> sortByDistance(
            List<T> locations,
            double centerLat,
            double centerLon) {
        
        return locations.stream()
                .sorted((a, b) -> {
                    double distanceA = calculateDistance(centerLat, centerLon, a.getLatitude(), a.getLongitude());
                    double distanceB = calculateDistance(centerLat, centerLon, b.getLatitude(), b.getLongitude());
                    return Double.compare(distanceA, distanceB);
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate neighbor geohash for a given direction
     */
    private String calculateNeighbor(String geohash, String direction) {
        // Simplified neighbor calculation - in production use proper geohash library
        try {
            if (geohash.length() <= 1) {
                return geohash;
            }
            
            // This is a simplified implementation
            // For production use, implement proper geohash neighbor algorithm
            // or use existing libraries like ch.hsr.geohash
            
            char lastChar = geohash.charAt(geohash.length() - 1);
            String parent = geohash.substring(0, geohash.length() - 1);
            
            // Simplified neighbor calculation based on direction
            Integer index = BASE32_MAP.get(lastChar);
            if (index == null) {
                return geohash;
            }
            
            int neighborIndex = index;
            switch (direction.toLowerCase()) {
                case "n":
                    neighborIndex = (index + 8) % 32;
                    break;
                case "s":
                    neighborIndex = (index - 8 + 32) % 32;
                    break;
                case "e":
                    neighborIndex = (index + 1) % 32;
                    break;
                case "w":
                    neighborIndex = (index - 1 + 32) % 32;
                    break;
                case "ne":
                    neighborIndex = (index + 9) % 32;
                    break;
                case "nw":
                    neighborIndex = (index + 7) % 32;
                    break;
                case "se":
                    neighborIndex = (index - 7 + 32) % 32;
                    break;
                case "sw":
                    neighborIndex = (index - 9 + 32) % 32;
                    break;
            }
            
            return parent + BASE32.charAt(neighborIndex);
            
        } catch (Exception e) {
            log.debug("Error calculating neighbor for geohash {}: {}", geohash, e.getMessage());
            return geohash;
        }
    }

    /**
     * Interface for objects that provide location coordinates
     */
    public interface LocationProvider {
        double getLatitude();
        double getLongitude();
    }

    /**
     * Bounding box data class
     */
    public static class BoundingBox {
        private final double minLat;
        private final double maxLat;
        private final double minLon;
        private final double maxLon;

        public BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        public double getMinLat() { return minLat; }
        public double getMaxLat() { return maxLat; }
        public double getMinLon() { return minLon; }
        public double getMaxLon() { return maxLon; }

        public double getCenterLat() { return (minLat + maxLat) / 2; }
        public double getCenterLon() { return (minLon + maxLon) / 2; }
        public double getLatRange() { return maxLat - minLat; }
        public double getLonRange() { return maxLon - minLon; }

        @Override
        public String toString() {
            return String.format("BoundingBox{lat: %.6f-%.6f, lon: %.6f-%.6f}", 
                               minLat, maxLat, minLon, maxLon);
        }
    }
}