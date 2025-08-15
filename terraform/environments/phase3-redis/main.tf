# Phase 3: Redis Cache Layer on EC2
# Agent 1 - EC2 Infrastructure Specialist

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "Oddiya"
      Environment = var.environment
      Phase       = "Phase3-Redis"
      ManagedBy   = "Terraform"
      CostCenter  = "Engineering"
    }
  }
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]
  
  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
  
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# VPC Configuration
resource "aws_vpc" "redis_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  tags = {
    Name = "${var.project_name}-redis-vpc-${var.environment}"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "redis_igw" {
  vpc_id = aws_vpc.redis_vpc.id
  
  tags = {
    Name = "${var.project_name}-redis-igw-${var.environment}"
  }
}

# Private Subnets for Redis nodes
resource "aws_subnet" "redis_private_subnets" {
  count             = var.redis_node_count
  vpc_id            = aws_vpc.redis_vpc.id
  cidr_block        = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  
  tags = {
    Name = "${var.project_name}-redis-private-subnet-${count.index + 1}-${var.environment}"
    Type = "Private"
  }
}

# Public Subnet for NAT Gateway
resource "aws_subnet" "redis_public_subnet" {
  vpc_id                  = aws_vpc.redis_vpc.id
  cidr_block              = "10.0.10.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true
  
  tags = {
    Name = "${var.project_name}-redis-public-subnet-${var.environment}"
    Type = "Public"
  }
}

# Elastic IP for NAT Gateway
resource "aws_eip" "nat_eip" {
  domain = "vpc"
  
  tags = {
    Name = "${var.project_name}-nat-eip-${var.environment}"
  }
}

# NAT Gateway
resource "aws_nat_gateway" "redis_nat" {
  allocation_id = aws_eip.nat_eip.id
  subnet_id     = aws_subnet.redis_public_subnet.id
  
  tags = {
    Name = "${var.project_name}-nat-gateway-${var.environment}"
  }
  
  depends_on = [aws_internet_gateway.redis_igw]
}

# Route Tables
resource "aws_route_table" "redis_private_rt" {
  vpc_id = aws_vpc.redis_vpc.id
  
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.redis_nat.id
  }
  
  tags = {
    Name = "${var.project_name}-redis-private-rt-${var.environment}"
  }
}

resource "aws_route_table" "redis_public_rt" {
  vpc_id = aws_vpc.redis_vpc.id
  
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.redis_igw.id
  }
  
  tags = {
    Name = "${var.project_name}-redis-public-rt-${var.environment}"
  }
}

# Route Table Associations
resource "aws_route_table_association" "redis_private_rta" {
  count          = length(aws_subnet.redis_private_subnets)
  subnet_id      = aws_subnet.redis_private_subnets[count.index].id
  route_table_id = aws_route_table.redis_private_rt.id
}

resource "aws_route_table_association" "redis_public_rta" {
  subnet_id      = aws_subnet.redis_public_subnet.id
  route_table_id = aws_route_table.redis_public_rt.id
}

# Security Groups
resource "aws_security_group" "redis_sg" {
  name_prefix = "${var.project_name}-redis-sg"
  vpc_id      = aws_vpc.redis_vpc.id
  
  # Redis port
  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  
  # Sentinel port
  ingress {
    from_port   = 26379
    to_port     = 26379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  
  # SSH access for management
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.management_cidr]
  }
  
  # Health check from ALB
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.redis_nlb_sg.id]
  }
  
  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "${var.project_name}-redis-sg-${var.environment}"
  }
}

resource "aws_security_group" "redis_nlb_sg" {
  name_prefix = "${var.project_name}-redis-nlb-sg"
  vpc_id      = aws_vpc.redis_vpc.id
  
  # Redis access from application
  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.app_cidr]
  }
  
  # Sentinel access
  ingress {
    from_port   = 26379
    to_port     = 26379
    protocol    = "tcp"
    cidr_blocks = [var.app_cidr]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "${var.project_name}-redis-nlb-sg-${var.environment}"
  }
}

# Key Pair for EC2 instances
resource "aws_key_pair" "redis_key" {
  key_name   = "${var.project_name}-redis-key-${var.environment}"
  public_key = var.redis_public_key
  
  tags = {
    Name = "${var.project_name}-redis-key-${var.environment}"
  }
}

# Launch Template for Redis nodes
resource "aws_launch_template" "redis_template" {
  name_prefix   = "${var.project_name}-redis-template"
  image_id      = data.aws_ami.amazon_linux.id
  instance_type = var.redis_instance_type
  key_name      = aws_key_pair.redis_key.key_name
  
  vpc_security_group_ids = [aws_security_group.redis_sg.id]
  
  # EBS optimization
  ebs_optimized = true
  
  # Instance metadata
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }
  
  # Monitoring
  monitoring {
    enabled = true
  }
  
  # EBS configuration
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_type = "gp3"
      volume_size = var.redis_disk_size
      iops        = var.redis_disk_iops
      throughput  = var.redis_disk_throughput
      encrypted   = true
    }
  }
  
  # User data script
  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    redis_version     = var.redis_version
    redis_password    = var.redis_password
    node_index        = 0  # Will be overridden in launch configuration
    master_ip         = ""  # Will be set dynamically
    environment       = var.environment
    cloudwatch_region = var.aws_region
  }))
  
  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "${var.project_name}-redis-node-${var.environment}"
      Role = "redis-node"
    }
  }
  
  tags = {
    Name = "${var.project_name}-redis-launch-template-${var.environment}"
  }
}

# Auto Scaling Groups for Redis nodes
resource "aws_autoscaling_group" "redis_master_asg" {
  name                = "${var.project_name}-redis-master-asg-${var.environment}"
  vpc_zone_identifier = [aws_subnet.redis_private_subnets[0].id]
  target_group_arns   = [aws_lb_target_group.redis_tg.arn]
  
  min_size                  = 1
  max_size                  = 1
  desired_capacity          = 1
  health_check_type         = "EC2"
  health_check_grace_period = 300
  
  launch_template {
    id      = aws_launch_template.redis_template.id
    version = "$Latest"
  }
  
  # Spot instances for cost optimization
  mixed_instances_policy {
    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.redis_template.id
        version            = "$Latest"
      }
      
      override {
        instance_type     = var.redis_instance_type
        weighted_capacity = "1"
      }
      
      override {
        instance_type     = var.redis_instance_type_spot
        weighted_capacity = "2"
      }
    }
    
    instances_distribution {
      on_demand_base_capacity                  = var.redis_on_demand_base
      on_demand_percentage_above_base_capacity = var.redis_on_demand_percentage
      spot_allocation_strategy                 = "diversified"
    }
  }
  
  tag {
    key                 = "Name"
    value              = "${var.project_name}-redis-master-${var.environment}"
    propagate_at_launch = true
  }
  
  tag {
    key                 = "Role"
    value              = "redis-master"
    propagate_at_launch = true
  }
  
  tag {
    key                 = "NodeIndex"
    value              = "0"
    propagate_at_launch = true
  }
}

resource "aws_autoscaling_group" "redis_slave_asg" {
  count               = var.redis_node_count - 1
  name                = "${var.project_name}-redis-slave-${count.index + 1}-asg-${var.environment}"
  vpc_zone_identifier = [aws_subnet.redis_private_subnets[count.index + 1].id]
  target_group_arns   = [aws_lb_target_group.redis_slave_tg.arn]
  
  min_size                  = 1
  max_size                  = 1
  desired_capacity          = 1
  health_check_type         = "EC2"
  health_check_grace_period = 300
  
  launch_template {
    id      = aws_launch_template.redis_template.id
    version = "$Latest"
  }
  
  mixed_instances_policy {
    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.redis_template.id
        version            = "$Latest"
      }
      
      override {
        instance_type     = var.redis_instance_type
        weighted_capacity = "1"
      }
      
      override {
        instance_type     = var.redis_instance_type_spot
        weighted_capacity = "2"
      }
    }
    
    instances_distribution {
      on_demand_base_capacity                  = var.redis_on_demand_base
      on_demand_percentage_above_base_capacity = var.redis_on_demand_percentage
      spot_allocation_strategy                 = "diversified"
    }
  }
  
  tag {
    key                 = "Name"
    value              = "${var.project_name}-redis-slave-${count.index + 1}-${var.environment}"
    propagate_at_launch = true
  }
  
  tag {
    key                 = "Role"
    value              = "redis-slave"
    propagate_at_launch = true
  }
  
  tag {
    key                 = "NodeIndex"
    value              = tostring(count.index + 1)
    propagate_at_launch = true
  }
}

# Network Load Balancer for Redis
resource "aws_lb" "redis_nlb" {
  name                             = "${var.project_name}-redis-nlb-${var.environment}"
  internal                         = true
  load_balancer_type              = "network"
  subnets                         = aws_subnet.redis_private_subnets[*].id
  enable_cross_zone_load_balancing = true
  
  enable_deletion_protection = var.enable_deletion_protection
  
  tags = {
    Name = "${var.project_name}-redis-nlb-${var.environment}"
  }
}

# Target Group for Redis Master
resource "aws_lb_target_group" "redis_tg" {
  name     = "${var.project_name}-redis-tg-${var.environment}"
  port     = 6379
  protocol = "TCP"
  vpc_id   = aws_vpc.redis_vpc.id
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    port                = "traffic-port"
    protocol            = "TCP"
  }
  
  tags = {
    Name = "${var.project_name}-redis-tg-${var.environment}"
  }
}

# Target Group for Redis Slaves
resource "aws_lb_target_group" "redis_slave_tg" {
  name     = "${var.project_name}-redis-slave-tg-${var.environment}"
  port     = 6379
  protocol = "TCP"
  vpc_id   = aws_vpc.redis_vpc.id
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    port                = "traffic-port"
    protocol            = "TCP"
  }
  
  tags = {
    Name = "${var.project_name}-redis-slave-tg-${var.environment}"
  }
}

# NLB Listeners
resource "aws_lb_listener" "redis_master_listener" {
  load_balancer_arn = aws_lb.redis_nlb.arn
  port              = "6379"
  protocol          = "TCP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.redis_tg.arn
  }
}

resource "aws_lb_listener" "redis_slave_listener" {
  load_balancer_arn = aws_lb.redis_nlb.arn
  port              = "6380"
  protocol          = "TCP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.redis_slave_tg.arn
  }
}

# Sentinel Target Group and Listener
resource "aws_lb_target_group" "redis_sentinel_tg" {
  name     = "${var.project_name}-redis-sentinel-tg-${var.environment}"
  port     = 26379
  protocol = "TCP"
  vpc_id   = aws_vpc.redis_vpc.id
  
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    port                = "traffic-port"
    protocol            = "TCP"
  }
  
  tags = {
    Name = "${var.project_name}-redis-sentinel-tg-${var.environment}"
  }
}

resource "aws_lb_listener" "redis_sentinel_listener" {
  load_balancer_arn = aws_lb.redis_nlb.arn
  port              = "26379"
  protocol          = "TCP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.redis_sentinel_tg.arn
  }
}

# IAM Role for Redis instances
resource "aws_iam_role" "redis_role" {
  name = "${var.project_name}-redis-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
  
  tags = {
    Name = "${var.project_name}-redis-role-${var.environment}"
  }
}

# IAM Policy for Redis instances
resource "aws_iam_role_policy" "redis_policy" {
  name = "${var.project_name}-redis-policy-${var.environment}"
  role = aws_iam_role.redis_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
          "logs:PutLogEvents",
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.redis_backups.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket"
        ]
        Resource = aws_s3_bucket.redis_backups.arn
      },
      {
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "ec2:DescribeTags",
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:DescribeAutoScalingInstances"
        ]
        Resource = "*"
      }
    ]
  })
}

# IAM Instance Profile
resource "aws_iam_instance_profile" "redis_profile" {
  name = "${var.project_name}-redis-profile-${var.environment}"
  role = aws_iam_role.redis_role.name
  
  tags = {
    Name = "${var.project_name}-redis-profile-${var.environment}"
  }
}

# S3 Bucket for Redis backups
resource "aws_s3_bucket" "redis_backups" {
  bucket        = "${var.project_name}-redis-backups-${var.environment}-${random_id.bucket_suffix.hex}"
  force_destroy = var.environment != "production"
  
  tags = {
    Name        = "${var.project_name}-redis-backups-${var.environment}"
    Purpose     = "Redis Backups"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_versioning" "redis_backups_versioning" {
  bucket = aws_s3_bucket.redis_backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_encryption" "redis_backups_encryption" {
  bucket = aws_s3_bucket.redis_backups.id
  
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "redis_backups_lifecycle" {
  bucket = aws_s3_bucket.redis_backups.id
  
  rule {
    id     = "redis_backup_lifecycle"
    status = "Enabled"
    
    expiration {
      days = var.backup_retention_days
    }
    
    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

# Random ID for S3 bucket uniqueness
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# CloudWatch Log Group for Redis
resource "aws_cloudwatch_log_group" "redis_logs" {
  name              = "/aws/ec2/redis/${var.environment}"
  retention_in_days = var.log_retention_days
  
  tags = {
    Name        = "${var.project_name}-redis-logs-${var.environment}"
    Environment = var.environment
  }
}