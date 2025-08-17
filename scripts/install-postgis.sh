#!/bin/bash

# Install PostGIS extension on RDS via ECS task
# This script runs psql from within an ECS task to install PostGIS

echo "🔧 Installing PostGIS extension on RDS PostgreSQL..."
echo "================================================"

CLUSTER="oddiya-prod-cluster"
SERVICE="oddiya-prod-app"
REGION="ap-northeast-2"

# Get a running task
TASK_ARN=$(aws ecs list-tasks \
  --cluster $CLUSTER \
  --service-name $SERVICE \
  --desired-status RUNNING \
  --region $REGION \
  --query 'taskArns[0]' \
  --output text)

if [ -z "$TASK_ARN" ] || [ "$TASK_ARN" = "None" ]; then
  echo "❌ No running tasks found. Please ensure the service is running."
  exit 1
fi

echo "📦 Using task: $(basename $TASK_ARN)"

# Execute psql command via ECS Exec
echo "🔄 Installing PostGIS extensions..."

aws ecs execute-command \
  --cluster $CLUSTER \
  --task $TASK_ARN \
  --container oddiya-app \
  --interactive \
  --command "sh -c 'PGPASSWORD=OddiyaSecurePass2024! psql -h oddiya-postgres.crumakwmu9au.ap-northeast-2.rds.amazonaws.com -U oddiya_user -d oddiya -c \"CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS postgis_topology; CREATE EXTENSION IF NOT EXISTS fuzzystrmatch; CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;\"'" \
  --region $REGION

if [ $? -eq 0 ]; then
  echo "✅ PostGIS extensions installed successfully!"
  
  # Verify installation
  echo ""
  echo "🔍 Verifying PostGIS installation..."
  aws ecs execute-command \
    --cluster $CLUSTER \
    --task $TASK_ARN \
    --container oddiya-app \
    --interactive \
    --command "sh -c 'PGPASSWORD=OddiyaSecurePass2024! psql -h oddiya-postgres.crumakwmu9au.ap-northeast-2.rds.amazonaws.com -U oddiya_user -d oddiya -c \"SELECT postgis_version();\"'" \
    --region $REGION
else
  echo "❌ Failed to install PostGIS extensions"
  echo ""
  echo "Alternative: Connect to RDS from an EC2 instance in the same VPC:"
  echo "1. Launch an EC2 instance in the same VPC"
  echo "2. Install PostgreSQL client: sudo yum install postgresql15"
  echo "3. Connect and run:"
  echo "   PGPASSWORD='OddiyaSecurePass2024!' psql -h oddiya-postgres.crumakwmu9au.ap-northeast-2.rds.amazonaws.com -U oddiya_user -d oddiya"
  echo "4. Execute: CREATE EXTENSION postgis;"
fi

echo ""
echo "📝 Note: After PostGIS is installed, you can re-enable:"
echo "- Flyway migrations (set FLYWAY_ENABLED=true)"
echo "- Hibernate validation (set JPA_DDL_AUTO=validate)"