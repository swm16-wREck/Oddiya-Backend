# RDS Aurora PostgreSQL Module

resource "random_password" "db_password" {
  length  = 32
  special = true
}

resource "aws_db_subnet_group" "main" {
  name       = "oddiya-${var.environment}-db-subnet"
  subnet_ids = var.data_subnet_ids

  tags = {
    Name        = "oddiya-${var.environment}-db-subnet-group"
    Environment = var.environment
  }
}

resource "aws_rds_cluster_parameter_group" "main" {
  family = "aurora-postgresql15"
  name   = "oddiya-${var.environment}-cluster-pg"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_statement"
    value = "all"
  }
}

resource "aws_rds_cluster" "main" {
  cluster_identifier      = "oddiya-${var.environment}-db-cluster"
  engine                  = "aurora-postgresql"
  engine_version          = "15.4"
  database_name           = var.db_name
  master_username         = var.db_username
  master_password         = random_password.db_password.result
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [var.database_security_group_id]
  backup_retention_period = var.backup_retention_period
  preferred_backup_window = "03:00-04:00"
  preferred_maintenance_window = "sun:04:00-sun:05:00"
  storage_encrypted       = true
  enabled_cloudwatch_logs_exports = ["postgresql"]
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.main.name

  tags = {
    Name        = "oddiya-${var.environment}-db-cluster"
    Environment = var.environment
  }
}

resource "aws_rds_cluster_instance" "writer" {
  identifier         = "oddiya-${var.environment}-db-writer"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = var.db_instance_class.writer
  engine             = aws_rds_cluster.main.engine
  engine_version     = aws_rds_cluster.main.engine_version
  performance_insights_enabled = true
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  tags = {
    Name        = "oddiya-${var.environment}-db-writer"
    Environment = var.environment
  }
}

resource "aws_rds_cluster_instance" "reader" {
  identifier         = "oddiya-${var.environment}-db-reader"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = var.db_instance_class.reader
  engine             = aws_rds_cluster.main.engine
  engine_version     = aws_rds_cluster.main.engine_version
  performance_insights_enabled = true
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  tags = {
    Name        = "oddiya-${var.environment}-db-reader"
    Environment = var.environment
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name = "oddiya-${var.environment}-rds-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "monitoring.rds.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "oddiya-${var.environment}-db-password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}