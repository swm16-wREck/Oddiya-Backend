#!/bin/bash

# LocalStack AWS Resources Initialization Script
# This script sets up AWS resources for testing

set -e

echo "🚀 Starting LocalStack AWS resources setup..."

# Set AWS CLI configuration for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=ap-northeast-2
export AWS_ENDPOINT_URL=http://localhost:4566

# Wait for LocalStack to be ready
echo "⏳ Waiting for LocalStack to be ready..."
until curl -s http://localhost:4566/health | grep -q '"dynamodb": "available"'; do
    echo "Waiting for LocalStack services..."
    sleep 2
done

echo "✅ LocalStack is ready!"

# Create S3 bucket
echo "🪣 Creating S3 bucket..."
awslocal s3 mb s3://oddiya-test-bucket 2>/dev/null || echo "Bucket already exists"

# Enable S3 versioning
echo "🔄 Enabling S3 versioning..."
awslocal s3api put-bucket-versioning \
    --bucket oddiya-test-bucket \
    --versioning-configuration Status=Enabled

# Create S3 bucket for file uploads
echo "📁 Creating S3 bucket for uploads..."
awslocal s3 mb s3://oddiya-uploads-test 2>/dev/null || echo "Upload bucket already exists"

# Set S3 bucket CORS policy
echo "🌐 Setting S3 CORS policy..."
awslocal s3api put-bucket-cors \
    --bucket oddiya-test-bucket \
    --cors-configuration file:///etc/localstack/init/ready.d/s3-cors-policy.json 2>/dev/null || echo "CORS policy not set"

# Create DynamoDB tables
echo "🗄️ Creating DynamoDB tables..."

# Users table
awslocal dynamodb create-table \
    --table-name test_oddiya_users \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region ap-northeast-2 2>/dev/null || echo "Users table already exists"

# Places table
awslocal dynamodb create-table \
    --table-name test_oddiya_places \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
        AttributeName=category,AttributeType=S \
        AttributeName=latitude,AttributeType=N \
        AttributeName=longitude,AttributeType=N \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=CategoryIndex,KeySchema=[{AttributeName=category,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
        IndexName=LocationIndex,KeySchema=[{AttributeName=latitude,KeyType=HASH},{AttributeName=longitude,KeyType=RANGE}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region ap-northeast-2 2>/dev/null || echo "Places table already exists"

# Travel Plans table
awslocal dynamodb create-table \
    --table-name test_oddiya_travel_plans \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
        AttributeName=userId,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=UserIndex,KeySchema=[{AttributeName=userId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region ap-northeast-2 2>/dev/null || echo "Travel plans table already exists"

# Reviews table
awslocal dynamodb create-table \
    --table-name test_oddiya_reviews \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
        AttributeName=placeId,AttributeType=S \
        AttributeName=userId,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=PlaceIndex,KeySchema=[{AttributeName=placeId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
        IndexName=UserIndex,KeySchema=[{AttributeName=userId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region ap-northeast-2 2>/dev/null || echo "Reviews table already exists"

# Videos table
awslocal dynamodb create-table \
    --table-name test_oddiya_videos \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
        AttributeName=userId,AttributeType=S \
        AttributeName=status,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=UserIndex,KeySchema=[{AttributeName=userId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
        IndexName=StatusIndex,KeySchema=[{AttributeName=status,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region ap-northeast-2 2>/dev/null || echo "Videos table already exists"

# Create SQS queues
echo "📬 Creating SQS queues..."

# Main queue with Dead Letter Queue
awslocal sqs create-queue \
    --queue-name oddiya-test-dlq \
    --region ap-northeast-2 2>/dev/null || echo "DLQ already exists"

# Get DLQ ARN
DLQ_URL=$(awslocal sqs get-queue-url --queue-name oddiya-test-dlq --query 'QueueUrl' --output text 2>/dev/null)
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text 2>/dev/null)

# Create main queue with DLQ
awslocal sqs create-queue \
    --queue-name oddiya-test-queue \
    --attributes '{
        "RedrivePolicy": "{\"deadLetterTargetArn\":\"'$DLQ_ARN'\",\"maxReceiveCount\":3}",
        "VisibilityTimeoutSeconds": "300",
        "MessageRetentionPeriod": "1209600"
    }' \
    --region ap-northeast-2 2>/dev/null || echo "Main queue already exists"

# Create additional queues for different message types
awslocal sqs create-queue \
    --queue-name oddiya-email-queue \
    --region ap-northeast-2 2>/dev/null || echo "Email queue already exists"

awslocal sqs create-queue \
    --queue-name oddiya-analytics-queue \
    --region ap-northeast-2 2>/dev/null || echo "Analytics queue already exists"

awslocal sqs create-queue \
    --queue-name oddiya-video-processing-queue \
    --region ap-northeast-2 2>/dev/null || echo "Video processing queue already exists"

# Create CloudWatch Log Group
echo "📊 Creating CloudWatch resources..."
awslocal logs create-log-group \
    --log-group-name /aws/lambda/oddiya-test \
    --region ap-northeast-2 2>/dev/null || echo "Log group already exists"

# Create CloudWatch custom metrics namespace
awslocal cloudwatch put-metric-data \
    --namespace "Oddiya/Test" \
    --metric-data MetricName=InitializationTest,Value=1,Unit=Count \
    --region ap-northeast-2 2>/dev/null || echo "Could not create test metric"

# Create Lambda function for testing (if needed)
echo "⚡ Setting up Lambda function..."
if [ -f /etc/localstack/init/ready.d/test-lambda.zip ]; then
    awslocal lambda create-function \
        --function-name oddiya-test-function \
        --runtime python3.9 \
        --role arn:aws:iam::000000000000:role/lambda-execution-role \
        --handler lambda_function.lambda_handler \
        --zip-file fileb:///etc/localstack/init/ready.d/test-lambda.zip \
        --region ap-northeast-2 2>/dev/null || echo "Lambda function already exists"
fi

# Insert test data into DynamoDB tables
echo "🌱 Inserting test data..."

# Test user data
awslocal dynamodb put-item \
    --table-name test_oddiya_users \
    --item '{
        "id": {"S": "test-user-1"},
        "username": {"S": "testuser"},
        "email": {"S": "test@example.com"},
        "name": {"S": "Test User"},
        "createdAt": {"S": "'$(date -u +%Y-%m-%dT%H:%M:%S)'"}
    }' \
    --region ap-northeast-2 2>/dev/null || echo "Could not insert test user"

# Test place data
awslocal dynamodb put-item \
    --table-name test_oddiya_places \
    --item '{
        "id": {"S": "test-place-1"},
        "name": {"S": "Test Restaurant"},
        "description": {"S": "A test restaurant for integration testing"},
        "address": {"S": "123 Test Street, Seoul"},
        "latitude": {"N": "37.5665"},
        "longitude": {"N": "126.9780"},
        "category": {"S": "restaurant"},
        "createdAt": {"S": "'$(date -u +%Y-%m-%dT%H:%M:%S)'"}
    }' \
    --region ap-northeast-2 2>/dev/null || echo "Could not insert test place"

# Upload test files to S3
echo "📤 Uploading test files to S3..."
echo "Test file content for integration testing" > /tmp/test-file.txt
awslocal s3 cp /tmp/test-file.txt s3://oddiya-test-bucket/test/test-file.txt 2>/dev/null || echo "Could not upload test file"

# Send test messages to SQS
echo "📨 Sending test messages to SQS..."
awslocal sqs send-message \
    --queue-url "http://localhost:4566/000000000000/oddiya-test-queue" \
    --message-body '{"messageType":"EMAIL","id":"test-msg-1","to":"test@example.com","subject":"Test Message","body":"Integration testing message"}' \
    --region ap-northeast-2 2>/dev/null || echo "Could not send test message"

# List created resources
echo "📋 Listing created resources..."

echo "S3 Buckets:"
awslocal s3 ls 2>/dev/null || echo "Could not list S3 buckets"

echo "DynamoDB Tables:"
awslocal dynamodb list-tables --region ap-northeast-2 2>/dev/null || echo "Could not list DynamoDB tables"

echo "SQS Queues:"
awslocal sqs list-queues --region ap-northeast-2 2>/dev/null || echo "Could not list SQS queues"

echo "CloudWatch Log Groups:"
awslocal logs describe-log-groups --region ap-northeast-2 2>/dev/null || echo "Could not list log groups"

echo "✅ LocalStack AWS resources setup completed successfully!"

# Health check
echo "🏥 Running health checks..."
curl -s http://localhost:4566/health | jq '.' 2>/dev/null || echo "Could not get health status"

echo "🎉 All AWS services are ready for integration testing!"