#!/bin/bash

echo "Starting Oddiya API with H2 Database..."
echo "======================================="
echo ""
echo "Profile: H2 (In-Memory Database)"
echo "H2 Console URL: http://localhost:8080/h2-console"
echo "API Base URL: http://localhost:8080/api/v1"
echo ""
echo "H2 Console Login:"
echo "  JDBC URL: jdbc:h2:mem:oddiya"
echo "  Username: sa"
echo "  Password: (leave empty)"
echo ""
echo "Press Ctrl+C to stop the application"
echo "======================================="
echo ""

# Run the application with H2 profile
./gradlew bootRun --args='--spring.profiles.active=h2'