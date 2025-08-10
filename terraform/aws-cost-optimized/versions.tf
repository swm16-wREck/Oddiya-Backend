terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }
  
  # Use S3 backend with cost-optimized settings
  backend "s3" {
    # bucket         = "your-terraform-state-bucket"
    # key            = "cost-optimized/terraform.tfstate"
    # region         = "us-east-1"  # Cheapest region
    # dynamodb_table = "terraform-state-lock"
    # encrypt        = true
    
    # Cost optimization: Use Standard-IA storage class for state
    # storage_class = "STANDARD_IA"
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      ManagedBy   = "Terraform"
      Environment = var.environment
      CostCenter  = var.cost_center
      Project     = var.project_name
      AutoStop    = var.auto_stop_enabled
    }
  }
}