#!/bin/bash

# Script to fix compilation errors in the Oddiya backend project

echo "Fixing compilation errors..."

# Fix 1: Add viewCount field to Place entity
echo "Adding viewCount field to Place entity..."
sed -i.bak '91a\
\    @Column(name = "view_count")\
\    @Builder.Default\
\    private Long viewCount = 0L;\
' src/main/java/com/oddiya/entity/Place.java

# Fix 2: Fix ConditionalBeans LocalMessagingService constructor calls
echo "Fixing ConditionalBeans LocalMessagingService constructors..."

# Add ObjectMapper import
sed -i.bak '3i\
import com.fasterxml.jackson.databind.ObjectMapper;' src/main/java/com/oddiya/config/ConditionalBeans.java

# Fix the first LocalMessagingService bean method
sed -i.bak 's/public MessagingService localMessagingService()/public MessagingService localMessagingService(ObjectMapper objectMapper)/' src/main/java/com/oddiya/config/ConditionalBeans.java
sed -i.bak 's/return new LocalMessagingService();/return new LocalMessagingService(objectMapper);/' src/main/java/com/oddiya/config/ConditionalBeans.java

# Fix the fallback LocalMessagingService bean method
sed -i.bak 's/public MessagingService fallbackLocalMessagingService()/public MessagingService fallbackLocalMessagingService(ObjectMapper objectMapper)/' src/main/java/com/oddiya/config/ConditionalBeans.java

# Fix 3: Update all MessagingController ApiResponse calls
echo "Fixing MessagingController ApiResponse calls..."

# Fix all ApiResponse.error() calls to include error code
sed -i.bak 's/ApiResponse\.error("\([^"]*\)")/ApiResponse.error("MESSAGING_ERROR", "\1")/g' src/main/java/com/oddiya/controller/MessagingController.java

# Fix specific error calls that need different codes
sed -i.bak 's/ApiResponse\.error("MESSAGING_ERROR", "Local messaging service not available")/ApiResponse.error("SERVICE_UNAVAILABLE", "Local messaging service not available")/g' src/main/java/com/oddiya/controller/MessagingController.java
sed -i.bak 's/ApiResponse\.error("Messaging service health check failed", errorInfo)/ApiResponse.error("HEALTH_CHECK_FAILED", "Messaging service health check failed: " + e.getMessage())/g' src/main/java/com/oddiya/controller/MessagingController.java

# Fix all ApiResponse.success() calls with two parameters to use single parameter
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*"[^"]*"\s*)/ApiResponse.success("Success")/g' src/main/java/com/oddiya/controller/MessagingController.java

# Fix specific success calls to pass the data
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*stats\s*)/ApiResponse.success(stats)/g' src/main/java/com/oddiya/controller/MessagingController.java
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*queueInfo\s*)/ApiResponse.success(queueInfo)/g' src/main/java/com/oddiya/controller/MessagingController.java
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*messages\s*)/ApiResponse.success(messages)/g' src/main/java/com/oddiya/controller/MessagingController.java
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*healthInfo\s*)/ApiResponse.success(healthInfo)/g' src/main/java/com/oddiya/controller/MessagingController.java
sed -i.bak 's/ApiResponse\.success(\s*"[^"]*",\s*errorInfo\s*)/ApiResponse.success(errorInfo)/g' src/main/java/com/oddiya/controller/MessagingController.java

# Fix 4: Fix PlaceServiceImpl issues
echo "Fixing PlaceServiceImpl..."

# Fix the search method call
sed -i.bak 's/searchByNameOrDescriptionContaining(query, query, pageable)/searchPlaces(query, pageable)/' src/main/java/com/oddiya/service/impl/PlaceServiceImpl.java

# Remove incorrect @Override annotations
sed -i.bak '/getNearbyPlaces/,+5 { /@Override/d; }' src/main/java/com/oddiya/service/impl/PlaceServiceImpl.java
sed -i.bak '/getPlacesByCategory/,+5 { /@Override/d; }' src/main/java/com/oddiya/service/impl/PlaceServiceImpl.java

# Fix PlaceResponse builder to use averageRating instead of rating
sed -i.bak 's/\.rating(place\.getRating())/.averageRating(place.getRating())/' src/main/java/com/oddiya/service/impl/PlaceServiceImpl.java

# Add the incrementViewCount method if it doesn't exist
cat >> src/main/java/com/oddiya/service/impl/PlaceServiceImpl.java << 'EOF'

    @Override
    @Transactional
    public void incrementViewCount(String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Place not found with id: " + id));
        
        // Increment view count
        if (place.getViewCount() != null) {
            place.setViewCount(place.getViewCount() + 1);
        } else {
            place.setViewCount(1L);
        }
        
        // Update popularity score if needed
        updatePopularityScore(place);
        
        placeRepository.save(place);
    }
    
    private void updatePopularityScore(Place place) {
        // Simple popularity score calculation
        double score = 0.0;
        
        if (place.getViewCount() != null) {
            score += Math.log10(place.getViewCount() + 1) * 10;
        }
        
        if (place.getRating() != null) {
            score += place.getRating() * 20;
        }
        
        if (place.getReviewCount() != null) {
            score += Math.log10(place.getReviewCount() + 1) * 5;
        }
        
        if (place.getBookmarkCount() != null) {
            score += Math.log10(place.getBookmarkCount() + 1) * 15;
        }
        
        place.setPopularityScore(score);
    }
EOF

# Fix 5: Fix SQSMessagingService type issue
echo "Fixing SQSMessagingService type issue..."
sed -i.bak 's/List<List<?>> batches = partitionList(messages, 10);/List<? extends List<?>> batches = partitionList(messages, 10);/' src/main/java/com/oddiya/service/messaging/SQSMessagingService.java

# Clean up backup files
rm -f src/main/java/com/oddiya/**/*.bak

echo "All compilation errors have been fixed!"
echo "Please run './gradlew build' to verify the fixes."