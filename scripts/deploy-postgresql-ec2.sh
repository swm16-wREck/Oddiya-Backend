#!/bin/bash

# PostgreSQL on EC2 Deployment Script for Oddiya
# Cost-effective solution: ~$30-50/month

set -e

# Configuration
REGION="ap-northeast-2"
INSTANCE_TYPE="t3.small"  # 2 vCPU, 2 GB RAM - sufficient for PostgreSQL
KEY_NAME="oddiya-db-key"
SECURITY_GROUP_NAME="oddiya-postgresql-sg"
DB_NAME="oddiya"
DB_USER="oddiya_user"
DB_PASSWORD="OddiyaSecure2025!"  # Change this!
INSTANCE_NAME="oddiya-postgresql"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Starting PostgreSQL EC2 Deployment${NC}"

# Step 1: Create SSH Key Pair (if not exists)
echo -e "${YELLOW}Step 1: Setting up SSH key pair...${NC}"
if aws ec2 describe-key-pairs --key-names "$KEY_NAME" --region "$REGION" 2>/dev/null; then
    echo "✅ Key pair $KEY_NAME already exists"
else
    aws ec2 create-key-pair --key-name "$KEY_NAME" --region "$REGION" \
        --query 'KeyMaterial' --output text > "${KEY_NAME}.pem"
    chmod 400 "${KEY_NAME}.pem"
    echo "✅ Key pair created and saved to ${KEY_NAME}.pem"
fi

# Step 2: Get VPC and Subnet info
echo -e "${YELLOW}Step 2: Getting VPC information...${NC}"
VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" \
    --filters "Name=is-default,Values=true" \
    --query "Vpcs[0].VpcId" --output text)

SUBNET_ID=$(aws ec2 describe-subnets --region "$REGION" \
    --filters "Name=vpc-id,Values=$VPC_ID" "Name=availability-zone,Values=${REGION}a" \
    --query "Subnets[0].SubnetId" --output text)

echo "✅ VPC: $VPC_ID, Subnet: $SUBNET_ID"

# Step 3: Create Security Group
echo -e "${YELLOW}Step 3: Setting up security group...${NC}"
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
    --filters "Name=group-name,Values=$SECURITY_GROUP_NAME" \
    --query "SecurityGroups[0].GroupId" --output text 2>/dev/null || echo "")

if [ -z "$SG_ID" ] || [ "$SG_ID" == "None" ]; then
    SG_ID=$(aws ec2 create-security-group \
        --group-name "$SECURITY_GROUP_NAME" \
        --description "Security group for Oddiya PostgreSQL" \
        --vpc-id "$VPC_ID" \
        --region "$REGION" \
        --query 'GroupId' --output text)
    
    # Allow PostgreSQL from anywhere in VPC
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_ID" \
        --protocol tcp \
        --port 5432 \
        --cidr 10.0.0.0/8 \
        --region "$REGION"
    
    # Allow SSH for management
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_ID" \
        --protocol tcp \
        --port 22 \
        --cidr 0.0.0.0/0 \
        --region "$REGION"
    
    echo "✅ Security group created: $SG_ID"
else
    echo "✅ Security group already exists: $SG_ID"
fi

# Step 4: Create User Data script for PostgreSQL installation
echo -e "${YELLOW}Step 4: Creating PostgreSQL installation script...${NC}"
cat > /tmp/postgresql-userdata.sh <<'EOF'
#!/bin/bash
set -e

# Update system
apt-get update
apt-get upgrade -y

# Install PostgreSQL 15 and PostGIS
apt-get install -y postgresql-15 postgresql-15-postgis-3 postgresql-contrib-15

# Configure PostgreSQL
systemctl stop postgresql

# Update PostgreSQL configuration for remote connections
cat >> /etc/postgresql/15/main/postgresql.conf <<EOL
listen_addresses = '*'
max_connections = 200
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
min_wal_size = 512MB
max_wal_size = 2GB
EOL

# Update pg_hba.conf to allow connections from VPC
echo "host    all             all             10.0.0.0/8              md5" >> /etc/postgresql/15/main/pg_hba.conf
echo "host    all             all             172.31.0.0/16           md5" >> /etc/postgresql/15/main/pg_hba.conf

# Start PostgreSQL
systemctl start postgresql
systemctl enable postgresql

# Create database and user
sudo -u postgres psql <<EOSQL
CREATE USER oddiya_user WITH PASSWORD 'OddiyaSecure2025!';
CREATE DATABASE oddiya OWNER oddiya_user;
\c oddiya
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
GRANT ALL PRIVILEGES ON DATABASE oddiya TO oddiya_user;
GRANT CREATE ON SCHEMA public TO oddiya_user;
ALTER USER oddiya_user CREATEDB;
EOSQL

# Create backup script
cat > /home/ubuntu/backup-postgresql.sh <<'BACKUP'
#!/bin/bash
BACKUP_DIR="/home/ubuntu/backups"
mkdir -p $BACKUP_DIR
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
sudo -u postgres pg_dump oddiya | gzip > "$BACKUP_DIR/oddiya_$TIMESTAMP.sql.gz"
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
BACKUP
chmod +x /home/ubuntu/backup-postgresql.sh

# Add daily backup cron job
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/backup-postgresql.sh") | crontab -

# Install monitoring tools
apt-get install -y htop iotop

# Log installation complete
echo "PostgreSQL installation completed at $(date)" > /home/ubuntu/install.log
EOF

# Step 5: Launch EC2 Instance
echo -e "${YELLOW}Step 5: Launching EC2 instance...${NC}"

# Check if instance already exists
INSTANCE_ID=$(aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Name,Values=$INSTANCE_NAME" "Name=instance-state-name,Values=running" \
    --query "Reservations[0].Instances[0].InstanceId" --output text 2>/dev/null || echo "")

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" == "None" ]; then
    # Get Ubuntu 22.04 AMI
    AMI_ID=$(aws ec2 describe-images --region "$REGION" \
        --owners 099720109477 \
        --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
        --query 'Images | sort_by(@, &CreationDate) | [-1].ImageId' --output text)
    
    echo "Using AMI: $AMI_ID"
    
    # Launch instance
    INSTANCE_ID=$(aws ec2 run-instances \
        --image-id "$AMI_ID" \
        --instance-type "$INSTANCE_TYPE" \
        --key-name "$KEY_NAME" \
        --security-group-ids "$SG_ID" \
        --subnet-id "$SUBNET_ID" \
        --user-data file:///tmp/postgresql-userdata.sh \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$INSTANCE_NAME}]" \
        --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}" \
        --region "$REGION" \
        --query 'Instances[0].InstanceId' --output text)
    
    echo "✅ Instance launched: $INSTANCE_ID"
    echo "⏳ Waiting for instance to be running..."
    
    aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$REGION"
else
    echo "✅ Instance already exists: $INSTANCE_ID"
fi

# Step 6: Get instance details
echo -e "${YELLOW}Step 6: Getting instance details...${NC}"
INSTANCE_INFO=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" \
    --query 'Reservations[0].Instances[0].[PublicIpAddress,PrivateIpAddress]' --output text)
PUBLIC_IP=$(echo "$INSTANCE_INFO" | cut -f1)
PRIVATE_IP=$(echo "$INSTANCE_INFO" | cut -f2)

echo "✅ Instance is running"
echo "   Public IP: $PUBLIC_IP"
echo "   Private IP: $PRIVATE_IP"

# Step 7: Create ECS task definition environment file
echo -e "${YELLOW}Step 7: Creating ECS configuration...${NC}"
cat > ecs-environment.json <<EOF
{
  "DATABASE_URL": "jdbc:postgresql://${PRIVATE_IP}:5432/oddiya?sslmode=disable",
  "DATABASE_USERNAME": "${DB_USER}",
  "DATABASE_PASSWORD": "${DB_PASSWORD}",
  "SPRING_PROFILES_ACTIVE": "aws,postgresql",
  "SPRING_DATASOURCE_URL": "jdbc:postgresql://${PRIVATE_IP}:5432/oddiya?sslmode=disable",
  "SPRING_DATASOURCE_USERNAME": "${DB_USER}",
  "SPRING_DATASOURCE_PASSWORD": "${DB_PASSWORD}",
  "SPRING_JPA_DATABASE_PLATFORM": "org.hibernate.dialect.PostgreSQLDialect",
  "SPRING_JPA_HIBERNATE_DDL_AUTO": "update"
}
EOF

# Step 8: Update ECS Task Definition
echo -e "${YELLOW}Step 8: Updating ECS task definition...${NC}"
cat > task-definition.json <<EOF
{
  "family": "oddiya-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "oddiya",
      "image": "501544476367.dkr.ecr.ap-northeast-2.amazonaws.com/oddiya:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "DATABASE_URL", "value": "jdbc:postgresql://${PRIVATE_IP}:5432/oddiya?sslmode=disable"},
        {"name": "DATABASE_USERNAME", "value": "${DB_USER}"},
        {"name": "DATABASE_PASSWORD", "value": "${DB_PASSWORD}"},
        {"name": "SPRING_PROFILES_ACTIVE", "value": "aws,postgresql"},
        {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://${PRIVATE_IP}:5432/oddiya?sslmode=disable"},
        {"name": "SPRING_DATASOURCE_USERNAME", "value": "${DB_USER}"},
        {"name": "SPRING_DATASOURCE_PASSWORD", "value": "${DB_PASSWORD}"},
        {"name": "SPRING_JPA_DATABASE_PLATFORM", "value": "org.hibernate.dialect.PostgreSQLDialect"},
        {"name": "SPRING_JPA_HIBERNATE_DDL_AUTO", "value": "update"},
        {"name": "SERVER_PORT", "value": "8080"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/oddiya",
          "awslogs-region": "ap-northeast-2",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "essential": true
    }
  ],
  "executionRoleArn": "arn:aws:iam::501544476367:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::501544476367:role/ecsTaskExecutionRole"
}
EOF

# Register new task definition
aws ecs register-task-definition --cli-input-json file://task-definition.json --region "$REGION"

# Update ECS service with new task definition
aws ecs update-service \
    --cluster oddiya-cluster \
    --service oddiya-service \
    --task-definition oddiya-task \
    --force-new-deployment \
    --region "$REGION"

echo "✅ ECS task definition updated"

# Step 9: Create connection info file
echo -e "${YELLOW}Step 9: Saving connection information...${NC}"
cat > postgresql-connection-info.txt <<EOF
=================================
PostgreSQL EC2 Deployment Complete
=================================
Instance ID: $INSTANCE_ID
Public IP: $PUBLIC_IP
Private IP: $PRIVATE_IP (use this for ECS)

Database Connection:
- Host: $PRIVATE_IP
- Port: 5432
- Database: oddiya
- Username: oddiya_user
- Password: OddiyaSecure2025!

SSH Access:
ssh -i ${KEY_NAME}.pem ubuntu@$PUBLIC_IP

PostgreSQL Connection (from VPC):
psql -h $PRIVATE_IP -U oddiya_user -d oddiya

Check PostgreSQL Status:
ssh -i ${KEY_NAME}.pem ubuntu@$PUBLIC_IP "sudo systemctl status postgresql"

View PostgreSQL Logs:
ssh -i ${KEY_NAME}.pem ubuntu@$PUBLIC_IP "sudo tail -f /var/log/postgresql/postgresql-15-main.log"

Estimated Monthly Cost: ~\$30-40
- EC2 t3.small: ~\$15/month
- EBS 30GB: ~\$3/month
- Data transfer: ~\$10-20/month

IMPORTANT: 
1. Wait 3-5 minutes for PostgreSQL to be fully installed
2. The database is only accessible from within the VPC
3. Backups are configured to run daily at 2 AM
=================================
EOF

cat postgresql-connection-info.txt

echo -e "${GREEN}✅ PostgreSQL deployment complete!${NC}"
echo -e "${YELLOW}⏰ Note: Wait 3-5 minutes for PostgreSQL installation to complete.${NC}"
echo -e "${YELLOW}📝 Connection details saved to postgresql-connection-info.txt${NC}"