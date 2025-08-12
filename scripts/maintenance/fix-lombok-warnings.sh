#!/bin/bash

# Script to fix Lombok @Builder.Default warnings
# These warnings occur when using @Builder with initialized fields

echo "Fixing Lombok @Builder.Default warnings..."

# Files to fix based on the warnings
FILES=(
    "src/main/java/com/oddiya/entity/Review.java"
    "src/main/java/com/oddiya/entity/ItineraryItem.java"
    "src/main/java/com/oddiya/entity/Video.java"
    "src/main/java/com/oddiya/entity/TravelPlan.java"
    "src/main/java/com/oddiya/entity/Place.java"
    "src/main/java/com/oddiya/entity/BaseEntity.java"
    "src/main/java/com/oddiya/entity/User.java"
)

for FILE in "${FILES[@]}"; do
    if [ -f "$FILE" ]; then
        echo "Processing: $FILE"
        
        # Add @Builder.Default annotation before initialized fields
        # This is a simplified fix - in production, you'd want more precise regex
        
        # For List/Map/Set initializations
        sed -i '' 's/private \(List\|Map\|Set\)\(.*\) = new/\
    @Builder.Default\
    private \1\2 = new/g' "$FILE"
        
        # For primitive/wrapper initializations with = 
        sed -i '' 's/private \(Integer\|Long\|Double\|boolean\|.*Status\)\(.*\) = \(.*\);/\
    @Builder.Default\
    private \1\2 = \3;/g' "$FILE"
        
        # Remove duplicate @Builder.Default if any
        sed -i '' '/^[[:space:]]*@Builder.Default[[:space:]]*$/{N;/^[[:space:]]*@Builder.Default/d;}' "$FILE"
        
        echo "  ✓ Fixed"
    else
        echo "  ✗ File not found: $FILE"
    fi
done

echo ""
echo "Lombok warnings fix complete!"
echo "Note: Review the changes and ensure proper imports for @Builder.Default"
echo ""
echo "To apply these fixes:"
echo "1. Review the changes with: git diff"
echo "2. Test locally: ./gradlew clean build"
echo "3. Commit if successful: git add -A && git commit -m 'fix: Add @Builder.Default annotations to fix Lombok warnings'"