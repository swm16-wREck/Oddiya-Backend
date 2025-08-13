#!/bin/bash

echo "🚀 Starting Oddiya API with H2 Database..."
echo "=========================================="

# Kill any existing process on port 8080
echo "Checking for existing processes on port 8080..."
lsof -ti:8080 | xargs kill -9 2>/dev/null && echo "Killed existing process on port 8080" || echo "Port 8080 is free"

echo ""
echo "Starting application..."
echo ""

# Set environment variables for H2
export SPRING_PROFILES_ACTIVE=h2
export SERVER_PORT=8080

# Run the application
echo "Command: ./gradlew bootRun --args='--spring.profiles.active=h2'"
echo ""
echo "📌 Access Points:"
echo "  H2 Console: http://localhost:8080/h2-console"
echo "  Health API: http://localhost:8080/api/v1/health"
echo ""
echo "📝 H2 Console Login:"
echo "  JDBC URL: jdbc:h2:mem:oddiya"
echo "  Username: sa"
echo "  Password: (leave empty)"
echo ""
echo "⏹  Press Ctrl+C to stop"
echo "=========================================="
echo ""

# Run the application
exec ./gradlew bootRun --args='--spring.profiles.active=h2'