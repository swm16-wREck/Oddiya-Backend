# AWS Cost-Optimized Terraform Module

A comprehensive Terraform module for deploying cost-optimized AWS infrastructure with automatic savings of 60-90% compared to standard deployments.

## 🎯 Key Features & Savings

| Optimization | Potential Savings | Description |
|-------------|-------------------|-------------|
| **Spot Instances** | 70-90% | Use spot instances for non-critical workloads |
| **Graviton (ARM)** | 20% | ARM-based instances for compatible workloads |
| **Single NAT Gateway** | $90/month | Use one NAT gateway instead of multiple |
| **Aurora Serverless v2** | 50-80% | Pay-per-request database pricing |
| **S3 Intelligent Tiering** | 30-40% | Automatic storage class transitions |
| **Auto Stop/Start** | 60-75% | Schedule non-production resources |
| **Reserved Capacity** | 40-70% | Recommendations for RIs and Savings Plans |

## 📁 Module Structure

```
aws-cost-optimized/
├── main.tf                 # Main orchestration
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── versions.tf             # Provider requirements
├── terraform.tfvars.example # Example configuration
└── modules/
    ├── compute/            # EC2, Auto Scaling, Spot instances
    ├── storage/            # S3, EBS, lifecycle policies
    ├── database/           # RDS, Aurora Serverless
    ├── networking/         # VPC, NAT, endpoints
    ├── scheduling/         # Auto stop/start Lambda
    └── monitoring/         # Cost alerts, dashboards
```

## 🚀 Quick Start

### 1. Prerequisites

- AWS CLI configured
- Terraform >= 1.5.0
- AWS account with appropriate permissions

### 2. Basic Usage

```hcl
module "cost_optimized_infra" {
  source = "./terraform/aws-cost-optimized"
  
  aws_region   = "ap-northeast-2"
  environment  = "dev"
  project_name = "my-project"
  
  # Enable all cost optimizations
  use_spot_instances      = true
  use_graviton            = true
  use_single_nat_gateway  = true
  enable_auto_stop        = true
  
  # Budget alerts
  budget_alert = {
    monthly_limit_usd = 500
    alert_thresholds  = [50, 80, 100]
    alert_emails      = ["team@example.com"]
  }
}
```

### 3. Deploy

```bash
# Copy and customize the example variables
cp terraform.tfvars.example terraform.tfvars

# Initialize Terraform
terraform init

# Review the plan
terraform plan

# Apply the configuration
terraform apply
```

## 💰 Cost Optimization Strategies

### 1. Compute Optimization

**Spot Instances** (70-90% savings)
- Automatically uses spot instances for non-production
- Implements spot instance diversification
- Handles interruptions gracefully

**Graviton Instances** (20% savings)
- ARM-based processors for better price/performance
- Compatible with most modern applications
- Automatic AMI selection

**Burstable Instances**
- T4g/T3 instances for variable workloads
- CPU credits for burst performance
- Ideal for development environments

### 2. Storage Optimization

**S3 Intelligent Tiering**
- Automatic movement between access tiers
- No retrieval fees
- 30-40% savings on storage costs

**Lifecycle Policies**
- Transition to cheaper storage classes:
  - Standard → Standard-IA (30 days)
  - Standard-IA → Glacier (90 days)
  - Glacier → Deep Archive (180 days)

**EBS Optimization**
- GP3 volumes (20% cheaper than GP2)
- Snapshot lifecycle management
- Automatic cleanup of unused volumes

### 3. Database Optimization

**Aurora Serverless v2**
- Scale to zero when idle
- Pay only for database capacity used
- Automatic pause/resume

**Read Replicas**
- Only in production
- Cross-AZ for high availability
- Automatic failover

### 4. Network Optimization

**Single NAT Gateway**
- Save $90/month per avoided gateway
- Suitable for non-critical workloads
- High availability trade-off

**VPC Endpoints**
- Reduce data transfer costs
- Keep traffic within AWS network
- Gateway endpoints for S3 and DynamoDB

### 5. Scheduling & Auto-Scaling

**Auto Stop/Start**
- Schedule resources for business hours
- 60-75% savings on non-production
- Lambda-based automation

**Predictive Scaling**
- ML-based capacity planning
- Proactive scaling for cost efficiency
- Reduced over-provisioning

## 📊 Monitoring & Alerts

### Cost Monitoring Dashboard
- Real-time cost tracking
- Service-level breakdown
- Trend analysis

### Budget Alerts
- Email notifications at thresholds
- Anomaly detection
- Weekly optimization reports

### Optimization Recommendations
- Weekly Lambda analysis
- Rightsizing suggestions
- Unused resource detection

## 🔧 Configuration Options

### Environment-Based Settings

```hcl
# Development
environment              = "dev"
use_spot_instances       = true
enable_auto_stop         = true
auto_scaling.min_size    = 1
auto_scaling.max_size    = 5

# Production
environment              = "prod"
use_spot_instances       = false
enable_auto_stop         = false
auto_scaling.min_size    = 3
auto_scaling.max_size    = 20
```

### Region Selection

```hcl
# Seoul (ap-northeast-2)
aws_region = "ap-northeast-2"

# Other cost-effective regions
# aws_region = "us-east-1"  # Virginia (usually cheapest)
# aws_region = "us-west-2"  # Oregon
```

## 📈 Cost Tracking

### Estimated Monthly Costs

| Component | On-Demand | Optimized | Savings |
|-----------|-----------|-----------|---------|
| EC2 (2x m5.large) | $140 | $20 | 86% |
| RDS (db.t3.medium) | $50 | $15 | 70% |
| NAT Gateway (3x) | $135 | $45 | 67% |
| S3 (1TB) | $23 | $14 | 39% |
| **Total** | **$348** | **$94** | **73%** |

### ROI Timeline
- Week 1: 40-50% immediate savings from spot instances
- Week 2: 60-70% with scheduling enabled
- Month 1: 70-80% with all optimizations
- Month 3: 80-90% with RI/Savings Plans

## 🛡️ Security Considerations

- All data encrypted at rest
- IMDSv2 enforced
- Security groups with minimal permissions
- VPC endpoints for private communication
- SSM for secure parameter storage

## 🔄 Maintenance

### Weekly Tasks
- Review cost optimization report
- Check for unused resources
- Validate scheduling is working

### Monthly Tasks
- Review and adjust budgets
- Analyze cost trends
- Update instance types if needed

### Quarterly Tasks
- Evaluate Reserved Instance options
- Review Savings Plans recommendations
- Optimize storage lifecycle policies

## 📝 Best Practices

1. **Start with Non-Production**
   - Test all optimizations in dev first
   - Gradually apply to staging
   - Carefully evaluate for production

2. **Monitor Continuously**
   - Set up budget alerts
   - Review weekly reports
   - Act on recommendations

3. **Iterate and Improve**
   - Start with easy wins (spot, scheduling)
   - Add complex optimizations gradually
   - Measure and validate savings

4. **Document Everything**
   - Keep track of configuration changes
   - Document cost savings achieved
   - Share learnings with team

## 🚨 Troubleshooting

### Common Issues

**Spot Instance Interruptions**
- Solution: Use mixed instance policies
- Enable warm pools for faster recovery

**NAT Gateway Failures**
- Solution: Monitor connectivity
- Consider multi-NAT for production

**Aurora Serverless Scaling**
- Solution: Adjust min/max ACUs
- Monitor scaling metrics

## 📚 Additional Resources

- [AWS Cost Optimization](https://aws.amazon.com/pricing/cost-optimization/)
- [Spot Instance Best Practices](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/spot-best-practices.html)
- [S3 Storage Classes](https://aws.amazon.com/s3/storage-classes/)
- [Aurora Serverless v2](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.html)

## 📄 License

MIT License - See LICENSE file for details

## 🤝 Contributing

Contributions are welcome! Please submit PRs with:
- New cost optimization strategies
- Bug fixes
- Documentation improvements

## 📧 Support

For issues or questions:
- Open an issue on GitHub
- Contact the infrastructure team
- Check the documentation

---

**Remember:** The biggest cloud cost optimization is turning off what you don't need! 💡