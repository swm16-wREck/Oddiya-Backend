# Security Groups for EC2 PostgreSQL Infrastructure

# ==========================================
# Security Groups
# ==========================================

# Application Security Group
resource "aws_security_group" "app" {
  name        = "${local.project_name}-app-sg"
  description = "Security group for application servers"
  vpc_id      = aws_vpc.main.id

  # HTTP access
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP access"
  }

  # HTTPS access
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS access"
  }

  # Application port
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Application port"
  }

  # SSH access (restricted to specified CIDR blocks)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidrs
    description = "SSH access"
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-app-sg"
  })
}

# Database Security Group
resource "aws_security_group" "database" {
  name        = "${local.project_name}-db-sg"
  description = "Security group for PostgreSQL database server"
  vpc_id      = aws_vpc.main.id

  # PostgreSQL access from application servers
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
    description     = "PostgreSQL access from app servers"
  }

  # PostgreSQL access from specified CIDR blocks (for admin access)
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.db_admin_cidrs
    description = "PostgreSQL admin access"
  }

  # SSH access for database administration
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidrs
    description = "SSH access for DB admin"
  }

  # Monitoring port (for custom monitoring tools)
  ingress {
    from_port   = 9187
    to_port     = 9187
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]  # Allow from VPC CIDR
    description = "PostgreSQL exporter for monitoring"
  }

  # All outbound traffic (for updates, backups to S3, etc.)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-db-sg"
  })
}

# Monitoring Security Group
resource "aws_security_group" "monitoring" {
  name        = "${local.project_name}-monitoring-sg"
  description = "Security group for monitoring services"
  vpc_id      = aws_vpc.main.id

  # HTTP access for monitoring dashboards
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = var.monitoring_allowed_cidrs
    description = "Grafana dashboard access"
  }

  # Prometheus access
  ingress {
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = var.monitoring_allowed_cidrs
    description = "Prometheus access"
  }

  # Node exporter access
  ingress {
    from_port   = 9100
    to_port     = 9100
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]  # Allow from VPC CIDR
    description = "Node exporter access"
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-monitoring-sg"
  })
}

# Bastion Host Security Group (optional)
resource "aws_security_group" "bastion" {
  count = var.enable_bastion ? 1 : 0

  name        = "${local.project_name}-bastion-sg"
  description = "Security group for bastion host"
  vpc_id      = aws_vpc.main.id

  # SSH access from internet (restricted to allowed IPs)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.bastion_allowed_cidrs
    description = "SSH access to bastion"
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-bastion-sg"
  })
}

# ==========================================
# Security Group Rules for Cross-Access
# ==========================================

# Allow SSH from bastion to database
resource "aws_security_group_rule" "bastion_to_db_ssh" {
  count = var.enable_bastion ? 1 : 0

  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.bastion[0].id
  security_group_id        = aws_security_group.database.id
  description              = "SSH access from bastion to database"
}

# Allow SSH from bastion to app servers
resource "aws_security_group_rule" "bastion_to_app_ssh" {
  count = var.enable_bastion ? 1 : 0

  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.bastion[0].id
  security_group_id        = aws_security_group.app.id
  description              = "SSH access from bastion to app servers"
}