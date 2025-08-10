# Cost-optimized database module with Aurora Serverless v2

locals {
  # Database engine selection based on cost
  engine_config = {
    mysql = {
      engine         = "aurora-mysql"
      engine_version = "8.0.mysql_aurora.3.04.0"
      family         = "aurora-mysql8.0"
      port           = 3306
    }
    postgres = {
      engine         = "aurora-postgresql"
      engine_version = "15.4"
      family         = "aurora-postgresql15"
      port           = 5432
    }
  }
  
  selected_engine = local.engine_config[var.database_engine]
  
  # Graviton instance classes for 20% savings
  instance_class = var.rds_optimization.use_graviton ? "db.t4g.medium" : "db.t3.medium"
}

# Subnet group for database
resource "aws_db_subnet_group" "main" {
  name_prefix = "${var.name_prefix}-db-"
  subnet_ids  = var.subnet_ids
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-db-subnet-group"
  })
}

# Security group for database
resource "aws_security_group" "database" {
  name_prefix = "${var.name_prefix}-db-"
  vpc_id      = var.vpc_id
  description = "Security group for RDS database"
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-db-sg"
  })
  
  lifecycle {
    create_before_destroy = true
  }
}

# Aurora Serverless v2 Cluster (pay-per-request)
resource "aws_rds_cluster" "serverless" {
  count = var.rds_optimization.use_aurora_serverless ? 1 : 0
  
  cluster_identifier     = "${var.name_prefix}-aurora-serverless"
  engine                 = local.selected_engine.engine
  engine_version         = local.selected_engine.engine_version
  engine_mode            = "provisioned"  # v2 uses provisioned mode
  database_name          = replace(var.name_prefix, "-", "_")
  master_username        = "admin"
  master_password        = random_password.db_password.result
  
  # Serverless v2 scaling configuration
  serverlessv2_scaling_configuration {
    min_capacity = var.is_production ? 0.5 : 0.5  # Minimum 0.5 ACU (~$43/month)
    max_capacity = var.is_production ? 4 : 1      # Scale as needed
  }
  
  # Cost optimization settings
  backup_retention_period      = var.rds_optimization.backup_retention_days
  preferred_backup_window      = "03:00-04:00"
  preferred_maintenance_window = "sun:04:00-sun:05:00"
  
  # Skip final snapshot for non-production
  skip_final_snapshot       = !var.is_production
  final_snapshot_identifier = var.is_production ? "${var.name_prefix}-final-snapshot-${formatdate("YYYY-MM-DD", timestamp())}" : null
  
  # Enable deletion protection only in production
  deletion_protection = var.is_production
  
  # Cost-saving options
  enabled_cloudwatch_logs_exports = var.is_production ? ["audit", "error", "slowquery"] : []
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  
  # Enable auto pause for non-production (saves costs when idle)
  tags = merge(var.tags, {
    Name      = "${var.name_prefix}-aurora-serverless"
    Type      = "serverless-v2"
    AutoPause = var.rds_optimization.enable_auto_pause && !var.is_production ? "enabled" : "disabled"
  })
  
  lifecycle {
    ignore_changes = [master_password]
  }
}

# Aurora Serverless v2 Instance
resource "aws_rds_cluster_instance" "serverless" {
  count = var.rds_optimization.use_aurora_serverless ? 1 : 0
  
  identifier         = "${var.name_prefix}-aurora-instance-1"
  cluster_identifier = aws_rds_cluster.serverless[0].id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.serverless[0].engine
  engine_version     = aws_rds_cluster.serverless[0].engine_version
  
  performance_insights_enabled = false  # Save costs on non-production
  monitoring_interval          = 0      # Disable enhanced monitoring to save costs
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-aurora-instance-1"
  })
}

# Standard RDS instance (fallback option)
resource "aws_db_instance" "standard" {
  count = var.rds_optimization.use_aurora_serverless ? 0 : 1
  
  identifier     = "${var.name_prefix}-rds"
  engine         = var.database_engine == "mysql" ? "mysql" : "postgres"
  engine_version = var.database_engine == "mysql" ? "8.0" : "15"
  instance_class = local.instance_class
  
  # Minimal storage with autoscaling
  allocated_storage     = 20
  max_allocated_storage = var.is_production ? 100 : 50
  storage_type          = "gp3"
  storage_encrypted     = true
  
  # Credentials
  db_name  = replace(var.name_prefix, "-", "_")
  username = "admin"
  password = random_password.db_password.result
  
  # Cost optimization
  backup_retention_period = var.rds_optimization.backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"
  
  # Multi-AZ only for production
  multi_az = var.is_production
  
  # Performance Insights disabled to save costs
  performance_insights_enabled = false
  
  # Disable enhanced monitoring to save costs
  enabled_cloudwatch_logs_exports = []
  monitoring_interval             = 0
  
  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  
  # Deletion protection
  deletion_protection = var.is_production
  skip_final_snapshot = !var.is_production
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-rds"
    Type = "standard"
  })
  
  lifecycle {
    ignore_changes = [password]
  }
}

# Random password for database
resource "random_password" "db_password" {
  length  = 32
  special = true
}

# Store password in SSM Parameter Store
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.name_prefix}/database/password"
  type  = "SecureString"
  value = random_password.db_password.result
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-db-password"
  })
}

# Parameter group with cost-optimized settings
resource "aws_rds_cluster_parameter_group" "aurora" {
  count = var.rds_optimization.use_aurora_serverless ? 1 : 0
  
  name_prefix = "${var.name_prefix}-aurora-"
  family      = local.selected_engine.family
  description = "Cost-optimized Aurora parameter group"
  
  # Cost optimization parameters
  parameter {
    name  = "slow_query_log"
    value = var.is_production ? "1" : "0"
  }
  
  parameter {
    name  = "long_query_time"
    value = "2"
  }
  
  dynamic "parameter" {
    for_each = var.database_engine == "mysql" ? [1] : []
    content {
      name  = "innodb_print_all_deadlocks"
      value = var.is_production ? "1" : "0"
    }
  }
  
  tags = var.tags
  
  lifecycle {
    create_before_destroy = true
  }
}

# DB parameter group for standard RDS
resource "aws_db_parameter_group" "standard" {
  count = var.rds_optimization.use_aurora_serverless ? 0 : 1
  
  name_prefix = "${var.name_prefix}-db-"
  family      = var.database_engine == "mysql" ? "mysql8.0" : "postgres15"
  description = "Cost-optimized RDS parameter group"
  
  tags = var.tags
  
  lifecycle {
    create_before_destroy = true
  }
}

# Read replica for production (optional)
resource "aws_db_instance" "read_replica" {
  count = var.is_production && var.enable_read_replica && !var.rds_optimization.use_aurora_serverless ? 1 : 0
  
  identifier             = "${var.name_prefix}-rds-read-replica"
  replicate_source_db    = aws_db_instance.standard[0].id
  instance_class         = local.instance_class
  
  # No backups for read replica (saves costs)
  backup_retention_period = 0
  
  # Performance Insights disabled
  performance_insights_enabled = false
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-rds-read-replica"
    Type = "read-replica"
  })
}

# Security group rules
resource "aws_security_group_rule" "database_ingress" {
  type              = "ingress"
  from_port         = local.selected_engine.port
  to_port           = local.selected_engine.port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.selected.cidr_block]
  security_group_id = aws_security_group.database.id
  description       = "Allow database access from VPC"
}

# Get VPC data
data "aws_vpc" "selected" {
  id = var.vpc_id
}