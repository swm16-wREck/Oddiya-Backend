#!/bin/bash

# Messaging/SQS Integration Testing Script
# This script tests all messaging endpoints and SQS integration functionality

set -e

# Configuration
BASE_URL="http://localhost:8080"
ADMIN_TOKEN=""  # Set this to a valid admin JWT token
REGION="ap-northeast-2"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if server is running
    if curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        print_success "Server is running at $BASE_URL"
    else
        print_error "Server is not running at $BASE_URL"
        echo "Please start the server with: ./gradlew bootRun"
        exit 1
    fi
    
    # Check if jq is available for JSON parsing
    if command -v jq &> /dev/null; then
        print_success "jq is available for JSON parsing"
    else
        print_warning "jq not available - responses will not be formatted"
    fi
    
    # Check if AWS CLI is available
    if command -v aws &> /dev/null; then
        print_success "AWS CLI is available"
    else
        print_warning "AWS CLI not available - SQS verification will be skipped"
    fi
    
    # Check if admin token is set
    if [ -z "$ADMIN_TOKEN" ]; then
        print_warning "ADMIN_TOKEN not set - authentication tests will be skipped"
    else
        print_success "Admin token is configured"
    fi
}

test_health_endpoint() {
    print_header "Testing Health Endpoint"
    
    local url="$BASE_URL/api/v1/messaging/health"
    local auth_header=""
    
    if [ -n "$ADMIN_TOKEN" ]; then
        auth_header="-H \"Authorization: Bearer $ADMIN_TOKEN\""
    fi
    
    echo "Testing: GET $url"
    
    local response
    if response=$(eval curl -s -w "\\n%{http_code}" $auth_header "$url" 2>/dev/null); then
        local body=$(echo "$response" | head -n -1)
        local status=$(echo "$response" | tail -n 1)
        
        echo "Status Code: $status"
        
        if [ "$status" = "200" ]; then
            print_success "Health endpoint is accessible"
            if command -v jq &> /dev/null; then
                echo "$body" | jq '.'
            else
                echo "$body"
            fi
        elif [ "$status" = "401" ] || [ "$status" = "403" ]; then
            print_warning "Authentication required (status: $status)"
        else
            print_error "Unexpected status code: $status"
            echo "$body"
        fi
    else
        print_error "Failed to connect to health endpoint"
    fi
}

test_queue_stats_endpoint() {
    print_header "Testing Queue Statistics Endpoint"
    
    local url="$BASE_URL/api/v1/messaging/queues/stats"
    local auth_header=""
    
    if [ -n "$ADMIN_TOKEN" ]; then
        auth_header="-H \"Authorization: Bearer $ADMIN_TOKEN\""
    fi
    
    echo "Testing: GET $url"
    
    local response
    if response=$(eval curl -s -w "\\n%{http_code}" $auth_header "$url" 2>/dev/null); then
        local body=$(echo "$response" | head -n -1)
        local status=$(echo "$response" | tail -n 1)
        
        echo "Status Code: $status"
        
        if [ "$status" = "200" ]; then
            print_success "Queue statistics endpoint is accessible"
            if command -v jq &> /dev/null; then
                echo "$body" | jq '.'
            else
                echo "$body"
            fi
        elif [ "$status" = "401" ] || [ "$status" = "403" ]; then
            print_warning "Authentication required (status: $status)"
        else
            print_error "Unexpected status code: $status"
            echo "$body"
        fi
    else
        print_error "Failed to connect to queue statistics endpoint"
    fi
}

test_email_message_endpoint() {
    print_header "Testing Email Message Endpoint"
    
    local url="$BASE_URL/api/v1/messaging/email"
    local auth_header=""
    
    if [ -n "$ADMIN_TOKEN" ]; then
        auth_header="-H \"Authorization: Bearer $ADMIN_TOKEN\""
    fi
    
    # Sample email message
    local message='{
        "messageId": "test-email-001",
        "templateType": "welcome",
        "recipientEmail": "test@example.com",
        "recipientName": "Test User",
        "subject": "Test Email from Messaging System",
        "htmlContent": "<h1>Test Email</h1><p>This is a test email from the Oddiya messaging system.</p>",
        "textContent": "Test Email\n\nThis is a test email from the Oddiya messaging system.",
        "priority": "HIGH",
        "templateVariables": {
            "userName": "Test User",
            "testMode": true
        },
        "tags": ["test", "messaging"],
        "userId": 123,
        "retryCount": 0,
        "maxRetries": 3
    }'
    
    echo "Testing: POST $url"
    echo "Payload: $message" | jq '.' 2>/dev/null || echo "Payload: $message"
    
    local response
    if response=$(eval curl -s -X POST -w "\\n%{http_code}" \
        $auth_header \
        -H "\"Content-Type: application/json\"" \
        -d "\"$message\"" \
        "$url" 2>/dev/null); then
        
        local body=$(echo "$response" | head -n -1)
        local status=$(echo "$response" | tail -n 1)
        
        echo "Status Code: $status"
        
        if [ "$status" = "200" ]; then
            print_success "Email message sent successfully"
            if command -v jq &> /dev/null; then
                echo "$body" | jq '.'
            else
                echo "$body"
            fi
        elif [ "$status" = "401" ] || [ "$status" = "403" ]; then
            print_warning "Authentication required (status: $status)"
        elif [ "$status" = "400" ]; then
            print_error "Bad request - validation error (status: $status)"
            echo "$body"
        else
            print_error "Unexpected status code: $status"
            echo "$body"
        fi
    else
        print_error "Failed to send email message"
    fi
}

test_batch_email_endpoint() {
    print_header "Testing Batch Email Message Endpoint"
    
    local url="$BASE_URL/api/v1/messaging/email/batch"
    local auth_header=""
    
    if [ -n "$ADMIN_TOKEN" ]; then
        auth_header="-H \"Authorization: Bearer $ADMIN_TOKEN\""
    fi
    
    # Sample batch email messages
    local messages='[
        {
            "messageId": "batch-email-001",
            "templateType": "weekly_digest",
            "recipientEmail": "user1@example.com",
            "recipientName": "User One",
            "subject": "Weekly Travel Digest",
            "htmlContent": "<h1>Weekly Digest</h1>",
            "priority": "NORMAL",
            "userId": 101
        },
        {
            "messageId": "batch-email-002",
            "templateType": "weekly_digest",
            "recipientEmail": "user2@example.com",
            "recipientName": "User Two",
            "subject": "Weekly Travel Digest",
            "htmlContent": "<h1>Weekly Digest</h1>",
            "priority": "NORMAL",
            "userId": 102
        }
    ]'
    
    echo "Testing: POST $url"
    echo "Payload (2 messages):"
    echo "$messages" | jq '.' 2>/dev/null || echo "$messages"
    
    local response
    if response=$(eval curl -s -X POST -w "\\n%{http_code}" \
        $auth_header \
        -H "\"Content-Type: application/json\"" \
        -d "\"$messages\"" \
        "$url" 2>/dev/null); then
        
        local body=$(echo "$response" | head -n -1)
        local status=$(echo "$response" | tail -n 1)
        
        echo "Status Code: $status"
        
        if [ "$status" = "200" ]; then
            print_success "Batch email messages sent successfully"
            if command -v jq &> /dev/null; then
                echo "$body" | jq '.'
            else
                echo "$body"
            fi
        elif [ "$status" = "401" ] || [ "$status" = "403" ]; then
            print_warning "Authentication required (status: $status)"
        else
            print_error "Unexpected status code: $status"
            echo "$body"
        fi
    else
        print_error "Failed to send batch email messages"
    fi
}

test_sqs_integration() {
    print_header "Testing AWS SQS Integration"
    
    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not available - skipping SQS tests"
        return
    fi
    
    # Check if AWS credentials are configured
    if ! aws sts get-caller-identity &> /dev/null; then
        print_warning "AWS credentials not configured - skipping SQS tests"
        return
    fi
    
    echo "Listing SQS queues in region $REGION..."
    
    local queues
    if queues=$(aws sqs list-queues --region "$REGION" 2>/dev/null); then
        print_success "Successfully connected to AWS SQS"
        
        # Check for Oddiya queues
        local oddiya_queues
        oddiya_queues=$(echo "$queues" | grep -o "oddiya-[^\"]*" || true)
        
        if [ -n "$oddiya_queues" ]; then
            print_success "Found Oddiya queues:"
            echo "$oddiya_queues"
            
            # Test queue attributes for the first queue found
            local first_queue=$(echo "$oddiya_queues" | head -n 1)
            local queue_url
            queue_url=$(echo "$queues" | grep "$first_queue" | sed 's/.*"\\([^"]*\\)".*/\\1/')
            
            if [ -n "$queue_url" ]; then
                echo -e "\\nTesting queue attributes for: $first_queue"
                
                local attributes
                if attributes=$(aws sqs get-queue-attributes \
                    --queue-url "$queue_url" \
                    --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible \
                    --region "$REGION" 2>/dev/null); then
                    
                    print_success "Retrieved queue attributes:"
                    if command -v jq &> /dev/null; then
                        echo "$attributes" | jq '.Attributes'
                    else
                        echo "$attributes"
                    fi
                else
                    print_error "Failed to get queue attributes"
                fi
            fi
        else
            print_warning "No Oddiya queues found in region $REGION"
        fi
    else
        print_error "Failed to list SQS queues"
    fi
}

test_cloudwatch_metrics() {
    print_header "Testing CloudWatch Metrics"
    
    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not available - skipping CloudWatch tests"
        return
    fi
    
    if ! aws sts get-caller-identity &> /dev/null; then
        print_warning "AWS credentials not configured - skipping CloudWatch tests"
        return
    fi
    
    echo "Checking CloudWatch metrics for Oddiya/SQS namespace..."
    
    local metrics
    if metrics=$(aws cloudwatch list-metrics \
        --namespace "Oddiya/SQS" \
        --region "$REGION" 2>/dev/null); then
        
        local metric_count
        metric_count=$(echo "$metrics" | jq '.Metrics | length' 2>/dev/null || echo "unknown")
        
        if [ "$metric_count" != "0" ] && [ "$metric_count" != "unknown" ]; then
            print_success "Found $metric_count CloudWatch metrics"
            if command -v jq &> /dev/null; then
                echo "$metrics" | jq '.Metrics[] | {MetricName, Dimensions}'
            fi
        else
            print_warning "No CloudWatch metrics found for Oddiya/SQS namespace"
        fi
    else
        print_error "Failed to list CloudWatch metrics"
    fi
}

# Main execution
main() {
    echo -e "${BLUE}Messaging/SQS Integration Testing Script${NC}"
    echo "========================================"
    
    check_prerequisites
    
    # Test endpoints
    test_health_endpoint
    test_queue_stats_endpoint
    test_email_message_endpoint
    test_batch_email_endpoint
    
    # Test AWS integration
    test_sqs_integration
    test_cloudwatch_metrics
    
    print_header "Testing Summary"
    echo "All available tests have been completed."
    echo ""
    echo "Next steps:"
    echo "1. If authentication errors occurred, set ADMIN_TOKEN variable"
    echo "2. If AWS tests were skipped, configure AWS CLI credentials"
    echo "3. Check application logs for detailed messaging service activity"
    echo "4. Monitor CloudWatch metrics for message processing"
    
    print_success "Testing script completed"
}

# Show usage if help requested
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0 [options]"
    echo ""
    echo "Environment variables:"
    echo "  BASE_URL     - Server base URL (default: http://localhost:8080)"
    echo "  ADMIN_TOKEN  - JWT token for authentication"
    echo "  REGION       - AWS region (default: ap-northeast-2)"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Run all tests"
    echo "  ADMIN_TOKEN=xyz $0                    # Run with authentication"
    echo "  BASE_URL=http://prod.example.com $0   # Test against production"
    exit 0
fi

# Run main function
main