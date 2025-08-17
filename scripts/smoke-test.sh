#!/bin/bash

# Smoke test for Oddiya API deployment
set -e

echo "🧪 Running smoke tests..."
echo "========================="

# Configuration
API_URL="${API_URL:-https://api.oddiya.click}"
TIMEOUT=10

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test function
test_endpoint() {
    local endpoint=$1
    local expected_status=$2
    local description=$3
    
    echo -n "Testing $description: "
    
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "$API_URL$endpoint" 2>/dev/null || echo "000")
    
    if [ "$STATUS" = "$expected_status" ] || ([ "$expected_status" = "2XX" ] && [ "${STATUS:0:1}" = "2" ]); then
        echo -e "${GREEN}✅ Passed (HTTP $STATUS)${NC}"
        return 0
    else
        echo -e "${RED}❌ Failed (Expected: $expected_status, Got: $STATUS)${NC}"
        return 1
    fi
}

echo "Testing endpoint: $API_URL"
echo ""

# Track test results
FAILED=0

# Health check
if ! test_endpoint "/actuator/health" "200" "Health check"; then
    FAILED=$((FAILED + 1))
fi

# API endpoints (may return 401 without auth, which is fine)
if ! test_endpoint "/api/v1/health" "2XX" "API health"; then
    FAILED=$((FAILED + 1))
fi

# Swagger UI
if ! test_endpoint "/swagger-ui/index.html" "200" "Swagger UI"; then
    FAILED=$((FAILED + 1))
fi

# Check ECS service status
echo ""
echo "Checking ECS deployment status..."
SERVICE_STATUS=$(aws ecs describe-services \
    --cluster oddiya-prod-cluster \
    --services oddiya-prod-app \
    --query 'services[0].{RunningCount:runningCount,DesiredCount:desiredCount,Status:status}' \
    --output json 2>/dev/null || echo "{}")

if [ "$SERVICE_STATUS" != "{}" ]; then
    RUNNING=$(echo "$SERVICE_STATUS" | jq -r '.RunningCount')
    DESIRED=$(echo "$SERVICE_STATUS" | jq -r '.DesiredCount')
    STATUS=$(echo "$SERVICE_STATUS" | jq -r '.Status')
    
    echo "ECS Service: $STATUS (Running: $RUNNING/$DESIRED)"
    
    if [ "$RUNNING" = "$DESIRED" ] && [ "$RUNNING" != "0" ]; then
        echo -e "${GREEN}✅ ECS service is healthy${NC}"
    else
        echo -e "${YELLOW}⚠️ ECS service is not fully healthy${NC}"
        FAILED=$((FAILED + 1))
    fi
fi

# Summary
echo ""
echo "========================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All smoke tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ $FAILED smoke test(s) failed${NC}"
    
    # Show troubleshooting info
    echo ""
    echo "Troubleshooting:"
    echo "1. Check ECS logs: aws logs tail /ecs/oddiya-prod-app --follow"
    echo "2. Check task status: aws ecs list-tasks --cluster oddiya-prod-cluster --service oddiya-prod-app"
    echo "3. Check target health: aws elbv2 describe-target-health --target-group-arn <ARN>"
    
    exit 1
fi