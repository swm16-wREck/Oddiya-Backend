#!/bin/bash

# Deployment Build Script
# This script builds the application JAR for deployment without running tests

echo "🚀 Starting deployment build..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "ℹ️  Tests and quality checks are skipped for faster deployment"
echo ""

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean --no-daemon --quiet

# Build deployment JAR
echo "🔨 Building deployment JAR..."
./gradlew buildForDeployment --no-daemon --stacktrace -PskipQualityChecks=true

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ Build successful!"
    echo ""
    
    # Display JAR information
    if [ -f build/libs/oddiya.jar ]; then
        JAR_SIZE=$(du -h build/libs/oddiya.jar | cut -f1)
        echo "📦 JAR Details:"
        echo "   Location: build/libs/oddiya.jar"
        echo "   Size: $JAR_SIZE"
        echo "   Built at: $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
        echo "🎯 Next steps:"
        echo "   1. Deploy the JAR to your server"
        echo "   2. Run with: java -jar oddiya.jar"
        echo "   3. Or use Docker: docker build -t oddiya ."
    fi
else
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ Build failed!"
    echo "   Check the error messages above for details"
    exit 1
fi