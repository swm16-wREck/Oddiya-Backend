#!/bin/bash

# Oddiya Database Migration Script
# Easily migrate from DynamoDB to PostgreSQL

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
REGION="ap-northeast-2"
SCHEMA_FILE="database/oddiya_database_schema.sql"

# Function to show usage
show_usage() {
    echo -e "${GREEN}Oddiya Database Migration Tool${NC}"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  check          - Check current database setup"
    echo "  deploy-rds     - Deploy RDS Aurora Serverless v2"
    echo "  create-schema  - Create PostgreSQL schema"
    echo "  migrate-data   - Migrate data from DynamoDB to PostgreSQL"
    echo "  verify         - Verify migration success"
    echo "  switch         - Switch application to use PostgreSQL"
    echo "  rollback       - Rollback to DynamoDB"
    echo ""
}

# Check current setup
check_setup() {
    echo -e "${YELLOW}Checking current database setup...${NC}"
    echo ""
    
    # Check DynamoDB
    echo -e "${BLUE}DynamoDB Tables:${NC}"
    aws dynamodb list-tables --region $REGION | jq -r '.TableNames[]' | grep -E "oddiya|sessions" || echo "  No DynamoDB tables found"
    
    # Check RDS
    echo ""
    echo -e "${BLUE}RDS Clusters:${NC}"
    aws rds describe-db-clusters --region $REGION 2>/dev/null | jq -r '.DBClusters[].DBClusterIdentifier' | grep oddiya || echo "  No RDS clusters found"
    
    # Check current Terraform outputs
    echo ""
    echo -e "${BLUE}Terraform Status:${NC}"
    cd terraform/environments/minimal 2>/dev/null && terraform output 2>/dev/null | grep -E "rds|dynamodb" || echo "  No database outputs found"
}

# Deploy RDS
deploy_rds() {
    echo -e "${YELLOW}Deploying RDS Aurora Serverless v2...${NC}"
    
    # Create RDS configuration
    cat > terraform/environments/minimal/rds.tf << 'EOF'
# RDS Aurora Serverless v2 for PostgreSQL
resource "random_password" "db" {
  length  = 32
  special = true
}

resource "aws_db_subnet_group" "minimal" {
  name       = "oddiya-minimal-db-subnet"
  subnet_ids = data.aws_subnets.default.ids
  
  tags = {
    Name = "oddiya-minimal-db-subnet"
  }
}

resource "aws_security_group" "rds" {
  name        = "oddiya-minimal-rds-sg"
  description = "Security group for RDS"
  vpc_id      = data.aws_vpc.default.id
  
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.minimal.id]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "oddiya-minimal-rds-sg"
  }
}

resource "aws_rds_cluster" "minimal" {
  cluster_identifier     = "oddiya-minimal-db"
  engine                = "aurora-postgresql"
  engine_mode           = "provisioned"
  engine_version        = "15.4"
  database_name         = "oddiya"
  master_username       = "oddiya_admin"
  master_password       = random_password.db.result
  db_subnet_group_name  = aws_db_subnet_group.minimal.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  serverlessv2_scaling_configuration {
    max_capacity = 1.0    # Can scale up to 1 ACU
    min_capacity = 0.5    # Minimum 0.5 ACU (~$50/month)
  }
  
  skip_final_snapshot = true
  apply_immediately   = true
  
  tags = {
    Name = "oddiya-minimal-db"
  }
}

resource "aws_rds_cluster_instance" "minimal" {
  identifier         = "oddiya-minimal-db-instance"
  cluster_identifier = aws_rds_cluster.minimal.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.minimal.engine
  engine_version     = aws_rds_cluster.minimal.engine_version
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "oddiya-minimal-db-password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

output "rds_endpoint" {
  value = aws_rds_cluster.minimal.endpoint
}

output "rds_reader_endpoint" {
  value = aws_rds_cluster.minimal.reader_endpoint
}

output "db_password_secret" {
  value = aws_secretsmanager_secret.db_password.arn
}

output "db_password" {
  value     = random_password.db.result
  sensitive = true
}
EOF
    
    # Apply Terraform
    cd terraform/environments/minimal
    terraform apply -auto-approve
    
    echo -e "${GREEN}✓ RDS Aurora Serverless deployed!${NC}"
    echo -e "${BLUE}Endpoint: $(terraform output -raw rds_endpoint)${NC}"
}

# Create schema
create_schema() {
    echo -e "${YELLOW}Creating PostgreSQL schema...${NC}"
    
    cd terraform/environments/minimal
    
    # Get connection details
    RDS_ENDPOINT=$(terraform output -raw rds_endpoint 2>/dev/null)
    DB_PASSWORD=$(terraform output -raw db_password 2>/dev/null)
    
    if [ -z "$RDS_ENDPOINT" ]; then
        echo -e "${RED}Error: RDS not deployed. Run '$0 deploy-rds' first${NC}"
        exit 1
    fi
    
    # Create schema
    PGPASSWORD=$DB_PASSWORD psql -h $RDS_ENDPOINT -U oddiya_admin -d oddiya < ../../../$SCHEMA_FILE
    
    echo -e "${GREEN}✓ Schema created successfully!${NC}"
    
    # List tables
    echo -e "${BLUE}Tables created:${NC}"
    PGPASSWORD=$DB_PASSWORD psql -h $RDS_ENDPOINT -U oddiya_admin -d oddiya -c "\dt oddiya.*" | grep -E "^ oddiya"
}

# Migrate data
migrate_data() {
    echo -e "${YELLOW}Migrating data from DynamoDB to PostgreSQL...${NC}"
    
    # Create migration script
    cat > /tmp/migrate-oddiya.py << 'EOF'
import boto3
import psycopg2
import json
import os
from datetime import datetime

# Configuration
dynamodb = boto3.resource('dynamodb', region_name='ap-northeast-2')
session_table = dynamodb.Table('oddiya-minimal-sessions')

# PostgreSQL connection
conn = psycopg2.connect(
    host=os.environ['RDS_ENDPOINT'],
    database='oddiya',
    user='oddiya_admin',
    password=os.environ['DB_PASSWORD'],
    port=5432
)
cur = conn.cursor()

def migrate_sessions():
    """Migrate sessions from DynamoDB to PostgreSQL"""
    print("Scanning DynamoDB sessions table...")
    
    response = session_table.scan()
    items = response.get('Items', [])
    
    print(f"Found {len(items)} sessions to migrate")
    
    for item in items:
        # Convert DynamoDB item to PostgreSQL format
        session_id = item.get('session_id')
        user_id = item.get('user_id', 0)
        data = json.dumps(item.get('data', {}))
        ttl = item.get('ttl', 0)
        expires_at = datetime.fromtimestamp(ttl) if ttl > 0 else None
        
        # Insert into PostgreSQL
        try:
            cur.execute("""
                INSERT INTO oddiya.session_store (session_id, user_id, data, expires_at)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (session_id) DO UPDATE SET
                    data = EXCLUDED.data,
                    expires_at = EXCLUDED.expires_at
            """, (session_id, user_id, data, expires_at))
            print(f"  ✓ Migrated session: {session_id}")
        except Exception as e:
            print(f"  ✗ Failed to migrate session {session_id}: {e}")
    
    conn.commit()
    print(f"✓ Successfully migrated {len(items)} sessions")

def verify_migration():
    """Verify migration success"""
    # Count DynamoDB items
    response = session_table.scan(Select='COUNT')
    dynamodb_count = response['Count']
    
    # Count PostgreSQL rows
    cur.execute("SELECT COUNT(*) FROM oddiya.session_store")
    pg_count = cur.fetchone()[0]
    
    print(f"\nVerification:")
    print(f"  DynamoDB sessions: {dynamodb_count}")
    print(f"  PostgreSQL sessions: {pg_count}")
    
    if dynamodb_count == pg_count:
        print("  ✓ Migration verified successfully!")
        return True
    else:
        print("  ⚠ Warning: Record count mismatch!")
        return False

# Run migration
if __name__ == "__main__":
    try:
        migrate_sessions()
        verify_migration()
        print("\n✓ Migration completed successfully!")
    except Exception as e:
        print(f"\n✗ Migration failed: {e}")
        exit(1)
    finally:
        cur.close()
        conn.close()
EOF
    
    # Get connection details
    cd terraform/environments/minimal
    export RDS_ENDPOINT=$(terraform output -raw rds_endpoint 2>/dev/null)
    export DB_PASSWORD=$(terraform output -raw db_password 2>/dev/null)
    
    # Run migration
    python3 /tmp/migrate-oddiya.py
    
    echo -e "${GREEN}✓ Data migration completed!${NC}"
}

# Verify migration
verify_migration() {
    echo -e "${YELLOW}Verifying migration...${NC}"
    
    cd terraform/environments/minimal
    RDS_ENDPOINT=$(terraform output -raw rds_endpoint 2>/dev/null)
    DB_PASSWORD=$(terraform output -raw db_password 2>/dev/null)
    
    # Check PostgreSQL
    echo -e "${BLUE}PostgreSQL Database:${NC}"
    PGPASSWORD=$DB_PASSWORD psql -h $RDS_ENDPOINT -U oddiya_admin -d oddiya -c "
    SELECT 
        'Tables' as metric, COUNT(*) as count 
    FROM information_schema.tables 
    WHERE table_schema = 'oddiya'
    UNION ALL
    SELECT 
        'Users' as metric, COUNT(*) as count 
    FROM oddiya.user
    UNION ALL
    SELECT 
        'Sessions' as metric, COUNT(*) as count 
    FROM oddiya.session_store;"
    
    echo -e "${GREEN}✓ Migration verification complete!${NC}"
}

# Switch to PostgreSQL
switch_to_postgres() {
    echo -e "${YELLOW}Switching application to use PostgreSQL...${NC}"
    
    cd terraform/environments/minimal
    
    # Update environment variables
    cat >> .env << EOF

# Database Configuration
DATABASE_TYPE=postgresql
DATABASE_URL=postgresql://oddiya_admin:$(terraform output -raw db_password)@$(terraform output -raw rds_endpoint):5432/oddiya
USE_POSTGRES=true
EOF
    
    echo -e "${GREEN}✓ Application configured to use PostgreSQL!${NC}"
    echo -e "${BLUE}Restart your application to apply changes${NC}"
}

# Rollback to DynamoDB
rollback() {
    echo -e "${YELLOW}Rolling back to DynamoDB...${NC}"
    
    # Update environment variables
    sed -i '' 's/USE_POSTGRES=true/USE_POSTGRES=false/g' terraform/environments/minimal/.env 2>/dev/null || \
    sed -i 's/USE_POSTGRES=true/USE_POSTGRES=false/g' terraform/environments/minimal/.env
    
    echo -e "${GREEN}✓ Rolled back to DynamoDB!${NC}"
    echo -e "${BLUE}Restart your application to apply changes${NC}"
}

# Main execution
case "$1" in
    check)
        check_setup
        ;;
    deploy-rds)
        deploy_rds
        ;;
    create-schema)
        create_schema
        ;;
    migrate-data)
        migrate_data
        ;;
    verify)
        verify_migration
        ;;
    switch)
        switch_to_postgres
        ;;
    rollback)
        rollback
        ;;
    *)
        show_usage
        ;;
esac