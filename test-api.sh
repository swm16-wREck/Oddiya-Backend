#!/bin/bash

# API Testing Script for Oddiya
# Make sure the application is running with: SPRING_PROFILES_ACTIVE=test ./gradlew bootRun

BASE_URL="http://localhost:8080/api/v1"
ACCESS_TOKEN=""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "================================"
echo "Oddiya API Testing Script"
echo "================================"
echo ""

# Function to print test results
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# 1. Test Health Endpoint
echo "1. Testing Health Endpoint..."
response=$(curl -s -w "\n%{http_code}" $BASE_URL/health)
http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    print_result 0 "Health check passed"
else
    print_result 1 "Health check failed (HTTP $http_code)"
fi
echo ""

# 2. Test Mock Login
echo "2. Testing Mock Login..."
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/auth/mock-login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com","name":"Test User"}')
    
http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    print_result 0 "Mock login successful"
    # Extract access token using sed (works on both Linux and macOS)
    ACCESS_TOKEN=$(echo "$body" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
    if [ -n "$ACCESS_TOKEN" ]; then
        echo "  Access token obtained: ${ACCESS_TOKEN:0:20}..."
    fi
else
    print_result 1 "Mock login failed (HTTP $http_code)"
    echo "Response: $body"
    exit 1
fi
echo ""

# 3. Test Create Travel Plan (requires authentication)
echo "3. Testing Create Travel Plan..."
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/travel-plans \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{
        "title": "Tokyo Adventure",
        "description": "5 days exploring Tokyo",
        "destination": "Tokyo, Japan",
        "startDate": "2024-06-01",
        "endDate": "2024-06-05",
        "isPublic": true,
        "tags": ["japan", "tokyo", "adventure"]
    }')

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "201" ]; then
    print_result 0 "Travel plan created successfully"
    PLAN_ID=$(echo "$body" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
    echo "  Travel plan ID: $PLAN_ID"
else
    print_result 1 "Travel plan creation failed (HTTP $http_code)"
    echo "Response: $body"
fi
echo ""

# 4. Test Get Public Travel Plans
echo "4. Testing Get Public Travel Plans..."
response=$(curl -s -w "\n%{http_code}" "$BASE_URL/travel-plans/public?page=0&size=10")

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    print_result 0 "Public travel plans retrieved"
else
    print_result 1 "Failed to get public travel plans (HTTP $http_code)"
fi
echo ""

# 5. Test Create Place
echo "5. Testing Create Place..."
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/places \
    -H "Content-Type: application/json" \
    -d '{
        "name": "Tokyo Tower",
        "description": "Iconic landmark of Tokyo",
        "address": "4 Chome-2-8 Shibakoen, Minato City, Tokyo",
        "latitude": 35.6586,
        "longitude": 139.7454,
        "category": "ATTRACTION",
        "tags": ["landmark", "observation deck"]
    }')

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "201" ]; then
    print_result 0 "Place created successfully"
    PLACE_ID=$(echo "$body" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
    echo "  Place ID: $PLACE_ID"
else
    print_result 1 "Place creation failed (HTTP $http_code)"
fi
echo ""

# 6. Test Search Places
echo "6. Testing Search Places..."
response=$(curl -s -w "\n%{http_code}" "$BASE_URL/places/search?query=tokyo")

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    print_result 0 "Place search successful"
else
    print_result 1 "Place search failed (HTTP $http_code)"
fi
echo ""

# 7. Test Token Validation
echo "7. Testing Token Validation..."
response=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/auth/validate \
    -H "Authorization: Bearer $ACCESS_TOKEN")

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    print_result 0 "Token validation successful"
else
    print_result 1 "Token validation failed (HTTP $http_code)"
fi
echo ""

echo "================================"
echo "Testing Complete!"
echo "================================"