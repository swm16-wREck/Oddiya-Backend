# Cost-optimized compute module with spot instances and auto-scaling

locals {
  # Select instance type based on architecture preference
  selected_instance_type = var.use_graviton ? var.instance_types.medium[0] : var.instance_types.medium[2]
  
  # Spot instance configuration
  spot_options = var.use_spot_instances ? {
    market_type = "spot"
    spot_options = {
      max_price                      = var.spot_max_price != "" ? var.spot_max_price : null
      spot_instance_type             = "one-time"
      instance_interruption_behavior = "terminate"
    }
  } : {
    market_type  = null
    spot_options = null
  }
}

# Latest Amazon Linux 2023 AMI (Graviton or x86)
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]
  
  filter {
    name   = "name"
    values = var.use_graviton ? 
      ["al2023-ami-*-arm64"] : 
      ["al2023-ami-*-x86_64"]
  }
  
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Launch Template for cost-optimized instances
resource "aws_launch_template" "main" {
  name_prefix   = "${var.name_prefix}-lt-"
  image_id      = data.aws_ami.amazon_linux.id
  instance_type = local.selected_instance_type
  
  # Spot instance configuration
  dynamic "instance_market_options" {
    for_each = var.use_spot_instances ? [1] : []
    content {
      market_type = "spot"
      
      spot_options {
        max_price                      = var.spot_max_price != "" ? var.spot_max_price : null
        spot_instance_type             = "persistent"
        instance_interruption_behavior = "stop"
        
        # Request valid until cancelled
        valid_until = null
      }
    }
  }
  
  # Use burstable performance
  credit_specification {
    cpu_credits = var.use_burstable_instances ? "standard" : null
  }
  
  # Cost-optimized storage
  block_device_mappings {
    device_name = "/dev/xvda"
    
    ebs {
      volume_size           = 20  # Minimum viable size
      volume_type           = "gp3"  # 20% cheaper than gp2
      iops                  = 3000   # Baseline IOPS
      throughput            = 125    # Baseline throughput
      encrypted             = true
      delete_on_termination = true
    }
  }
  
  # Enable detailed monitoring only in production
  monitoring {
    enabled = var.tags["Environment"] == "prod"
  }
  
  # Metadata options
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"  # IMDSv2
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "enabled"
  }
  
  network_interfaces {
    associate_public_ip_address = false
    delete_on_termination       = true
    security_groups             = [aws_security_group.compute.id]
  }
  
  # User data for initial setup and cost optimization
  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    environment = var.tags["Environment"]
    enable_cloudwatch = var.tags["Environment"] == "prod"
  }))
  
  tag_specifications {
    resource_type = "instance"
    tags = merge(var.tags, {
      Name = "${var.name_prefix}-instance"
      Type = var.use_spot_instances ? "spot" : "on-demand"
    })
  }
  
  tag_specifications {
    resource_type = "volume"
    tags = merge(var.tags, {
      Name = "${var.name_prefix}-volume"
    })
  }
  
  lifecycle {
    create_before_destroy = true
  }
}

# Auto Scaling Group with mixed instance types
resource "aws_autoscaling_group" "main" {
  name                = "${var.name_prefix}-asg"
  vpc_zone_identifier = var.subnet_ids
  
  min_size         = var.auto_scaling.min_size
  max_size         = var.auto_scaling.max_size
  desired_capacity = var.auto_scaling.desired_capacity
  
  # Health check configuration
  health_check_type         = "ELB"
  health_check_grace_period = 300
  default_cooldown          = var.auto_scaling.scale_in_cooldown
  
  # Instance refresh for rolling updates
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
      instance_warmup        = 300
    }
  }
  
  # Mixed instances policy for cost optimization
  mixed_instances_policy {
    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.main.id
        version            = "$Latest"
      }
      
      # Override with multiple instance types
      dynamic "override" {
        for_each = var.instance_types.medium
        content {
          instance_type     = override.value
          weighted_capacity = "1"
        }
      }
    }
    
    instances_distribution {
      on_demand_base_capacity                  = var.use_spot_instances ? 0 : 1
      on_demand_percentage_above_base_capacity = var.use_spot_instances ? 0 : 100
      spot_allocation_strategy                 = "price-capacity-optimized"
      spot_instance_pools                      = 3
    }
  }
  
  # Warm pool for faster scaling and cost savings
  warm_pool {
    pool_state                  = "Stopped"
    min_size                    = var.auto_scaling.min_size
    max_group_prepared_capacity = var.auto_scaling.max_size
    
    instance_reuse_policy {
      reuse_on_scale_in = true
    }
  }
  
  enabled_metrics = [
    "GroupMinSize",
    "GroupMaxSize",
    "GroupDesiredCapacity",
    "GroupInServiceInstances",
    "GroupTotalInstances"
  ]
  
  tag {
    key                 = "Name"
    value               = "${var.name_prefix}-asg-instance"
    propagate_at_launch = true
  }
  
  dynamic "tag" {
    for_each = var.tags
    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }
  
  lifecycle {
    create_before_destroy = true
    ignore_changes        = [desired_capacity]
  }
}

# Target Tracking Scaling Policy
resource "aws_autoscaling_policy" "target_tracking" {
  name                   = "${var.name_prefix}-target-tracking"
  autoscaling_group_name = aws_autoscaling_group.main.name
  policy_type            = "TargetTrackingScaling"
  
  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    
    target_value = var.auto_scaling.target_cpu_utilization
    
    # Aggressive scale-in for cost savings
    scale_in_cooldown  = var.auto_scaling.scale_in_cooldown
    scale_out_cooldown = var.auto_scaling.scale_out_cooldown
  }
}

# Predictive Scaling Policy (if enabled)
resource "aws_autoscaling_policy" "predictive" {
  count = var.auto_scaling.predictive_scaling ? 1 : 0
  
  name                   = "${var.name_prefix}-predictive"
  autoscaling_group_name = aws_autoscaling_group.main.name
  policy_type            = "PredictiveScaling"
  
  predictive_scaling_configuration {
    metric_specification {
      target_value = var.auto_scaling.target_cpu_utilization
      
      predefined_metric_pair_specification {
        predefined_metric_type = "ASGCPUUtilization"
      }
    }
    
    mode                          = "ForecastAndScale"
    scheduling_buffer_time        = 0
    max_capacity_breach_behavior  = "IncreaseMaxCapacity"
    max_capacity_buffer           = 10
  }
}

# Security Group with minimal rules
resource "aws_security_group" "compute" {
  name_prefix = "${var.name_prefix}-compute-"
  vpc_id      = var.vpc_id
  description = "Security group for compute instances"
  
  # Minimal egress for cost optimization
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS outbound"
  }
  
  egress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP outbound"
  }
  
  # VPC endpoints to reduce data transfer costs
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    prefix_list_ids = [data.aws_prefix_list.s3.id]
    description = "S3 VPC endpoint"
  }
  
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-compute-sg"
  })
  
  lifecycle {
    create_before_destroy = true
  }
}

# Get S3 prefix list for VPC endpoint
data "aws_prefix_list" "s3" {
  filter {
    name   = "prefix-list-name"
    values = ["com.amazonaws.*.s3"]
  }
}