#!/bin/bash

# Add DynamoDB permissions to ECS task role

ROLE_NAME="oddiya-dev-ecs-task"
POLICY_NAME="oddiya-dev-ecs-task-policy"

# Create the updated policy document
cat > /tmp/updated-policy.json << 'EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "S3Access",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:s3:::oddiya-dev-media-501544476367/*",
                "arn:aws:s3:::oddiya-dev-media-501544476367"
            ]
        },
        {
            "Sid": "BedrockAccess",
            "Action": [
                "bedrock:InvokeModel"
            ],
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Sid": "DynamoDBAccess",
            "Action": [
                "dynamodb:CreateTable",
                "dynamodb:DescribeTable",
                "dynamodb:ListTables",
                "dynamodb:PutItem",
                "dynamodb:GetItem",
                "dynamodb:UpdateItem",
                "dynamodb:DeleteItem",
                "dynamodb:Query",
                "dynamodb:Scan",
                "dynamodb:BatchGetItem",
                "dynamodb:BatchWriteItem",
                "dynamodb:DescribeTimeToLive",
                "dynamodb:UpdateTimeToLive"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:dynamodb:ap-northeast-2:501544476367:table/oddiya_*",
                "arn:aws:dynamodb:ap-northeast-2:501544476367:table/oddiya_*/index/*"
            ]
        },
        {
            "Sid": "DynamoDBListTables",
            "Action": [
                "dynamodb:ListTables"
            ],
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Sid": "CloudWatchLogs",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Effect": "Allow",
            "Resource": "arn:aws:logs:ap-northeast-2:501544476367:*"
        }
    ]
}
EOF

echo "Updating IAM policy for role: $ROLE_NAME"
aws iam put-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-name "$POLICY_NAME" \
    --policy-document file:///tmp/updated-policy.json

if [ $? -eq 0 ]; then
    echo "✅ Successfully updated IAM policy with DynamoDB permissions"
else
    echo "❌ Failed to update IAM policy"
    exit 1
fi

# Clean up
rm /tmp/updated-policy.json

echo "Policy update complete. ECS tasks will now have DynamoDB access."