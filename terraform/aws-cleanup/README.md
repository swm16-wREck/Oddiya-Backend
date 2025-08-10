# AWS Resource Cleanup Module

Automated identification and removal of unused AWS resources to reduce costs. This Terraform module deploys a Lambda function that regularly scans your AWS account for unused resources and can automatically delete them.

## 🎯 Features

- **Automated Scanning**: Daily scans for unused resources
- **Dry Run Mode**: Test what would be deleted without actually deleting
- **Whitelist Protection**: Protect critical resources with tags or name prefixes
- **Email Notifications**: Get reports of cleaned resources
- **Cost Savings Estimation**: See potential monthly savings
- **Comprehensive Coverage**: Cleans 12+ resource types

## 💰 Potential Savings

| Resource Type | Typical Monthly Cost | Savings Potential |
|--------------|---------------------|-------------------|
| Unattached EBS Volumes | $10-50 per volume | High |
| Unused Elastic IPs | $3.60 per IP | Medium |
| Unused NAT Gateways | $45 per gateway | High |
| Unused Load Balancers | $18-25 per ALB | High |
| Old EBS Snapshots | $0.05 per GB | Medium |
| Old RDS Snapshots | $0.095 per GB | Medium |
| Old AMI Images | $0.05 per GB | Low |
| CloudWatch Logs | $0.50 per GB | Medium |
| **Total Potential** | **$100-500+** | **Per Month** |

## 📋 Resources Cleaned

1. **EBS Volumes** - Unattached volumes older than threshold
2. **EBS Snapshots** - Snapshots not associated with AMIs
3. **Elastic IPs** - Unassociated Elastic IPs
4. **Load Balancers** - ALBs/NLBs without targets
5. **NAT Gateways** - Unused NAT gateways
6. **RDS Snapshots** - Manual snapshots older than threshold
7. **AMI Images** - Custom AMIs older than threshold
8. **ECR Images** - Old container images (keeps last 10)
9. **CloudWatch Logs** - Empty log groups
10. **S3 Buckets** - Empty buckets older than threshold
11. **Security Groups** - Unused security groups
12. **Lambda Versions** - Old function versions (keeps last 3)

## 🚀 Quick Start

### 1. Prerequisites

- AWS CLI configured
- Terraform >= 1.5.0
- Appropriate AWS permissions

### 2. Deploy in Dry Run Mode (Recommended First)

```bash
# Clone or copy the module
cd terraform/aws-cleanup

# Copy and customize variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars - KEEP dry_run = true initially

# Deploy
terraform init
terraform plan
terraform apply
```

### 3. Test Manual Scan

```bash
# Trigger a manual cleanup scan
aws lambda invoke \
  --function-name aws-cleanup-resource-cleanup \
  --region ap-northeast-2 \
  /tmp/cleanup-output.json

# Check the results
cat /tmp/cleanup-output.json | jq
```

### 4. Review Results

- Check your email for the cleanup report
- Review CloudWatch logs for detailed information
- Access the dashboard URL from Terraform outputs

### 5. Enable Active Cleanup

Once satisfied with dry run results:

```hcl
# In terraform.tfvars, change:
dry_run = false

# Apply changes
terraform apply
```

## 🔧 Configuration

### Basic Configuration

```hcl
# terraform.tfvars
aws_region = "ap-northeast-2"
prefix     = "my-cleanup"
dry_run    = true  # Set to false to enable deletion

# Schedule - Examples:
schedule_expression = "rate(1 day)"          # Daily
schedule_expression = "rate(7 days)"         # Weekly  
schedule_expression = "cron(0 2 * * ? *)"    # 2 AM UTC daily

# Minimum age before deletion
resource_age_threshold_days = 30

# Notification emails
notification_emails = ["team@example.com"]
```

### Selective Cleanup

Disable specific resource types:

```hcl
cleanup_config = {
  ebs_volumes         = true
  ebs_snapshots       = true
  elastic_ips         = true
  load_balancers      = false  # Don't clean load balancers
  nat_gateways        = false  # Don't clean NAT gateways
  rds_snapshots       = true
  ami_images          = true
  ecr_images          = true
  cloudwatch_logs     = true
  s3_buckets          = false  # Don't clean S3 buckets
  security_groups     = true
  old_lambda_versions = true
}
```

### Protection Rules

Protect resources with tags or name prefixes:

```hcl
# Resources with these tags won't be deleted
whitelist_tags = [
  "Environment:prod",
  "Protected",
  "DoNotDelete",
  "Critical"
]

# Resources with these name prefixes won't be deleted
whitelist_name_prefixes = [
  "prod-",
  "production-",
  "critical-",
  "backup-"
]
```

## 📊 Monitoring

### CloudWatch Dashboard

Access the dashboard URL from Terraform outputs:
```bash
terraform output dashboard_url
```

### CloudWatch Logs

View detailed logs:
```bash
aws logs tail /aws/lambda/aws-cleanup-resource-cleanup --follow
```

### Email Reports

Reports include:
- Resources identified for cleanup
- Resources actually deleted (if not dry run)
- Estimated monthly savings
- Any errors encountered

## 🛡️ Safety Features

1. **Dry Run by Default** - Won't delete anything until explicitly enabled
2. **Age Threshold** - Only deletes resources older than specified days
3. **Whitelist Protection** - Critical resources can be protected
4. **Production Safeguards** - Production resources protected by default
5. **Detailed Logging** - All actions are logged for audit
6. **Email Notifications** - Get notified of all cleanup activities

## 📈 Cost Impact

### Example Monthly Savings

For a typical AWS account with moderate usage:

| Before Cleanup | After Cleanup | Monthly Savings |
|---------------|---------------|-----------------|
| $1,500 | $1,200 | $300 (20%) |

### ROI Timeline

- **Day 1**: Identify unused resources
- **Week 1**: Clean up obvious waste (30-50% of savings)
- **Month 1**: Establish regular cleanup routine
- **Month 3**: Achieve 80-90% of potential savings

## ⚠️ Important Considerations

### Before Running in Production

1. **Review Whitelist Rules** - Ensure critical resources are protected
2. **Test in Dry Run** - Always test with dry_run = true first
3. **Start Conservative** - Use longer age thresholds initially
4. **Monitor Closely** - Watch the first few cleanup runs carefully
5. **Backup Critical Data** - Ensure important snapshots are tagged

### Permissions Required

The Lambda function needs broad permissions to scan and delete resources. Review the IAM policy in `main.tf` to understand what permissions are granted.

### Limitations

- Only cleans resources in the specified region
- Some resources may have dependencies preventing deletion
- AWS API rate limits may affect large-scale cleanups
- Some resources may incur deletion costs (e.g., early deletion fees)

## 🔍 Troubleshooting

### Common Issues

**No resources found for cleanup**
- Check age threshold (might be too high)
- Verify resources exist in the specified region
- Check if resources are whitelisted

**Deletion failures**
- Check for resource dependencies
- Verify IAM permissions
- Look for AWS service limits

**Not receiving emails**
- Confirm SNS subscription
- Check spam folder
- Verify email address in terraform.tfvars

## 📝 Best Practices

1. **Regular Reviews**
   - Review cleanup reports weekly
   - Adjust thresholds based on your usage patterns
   - Update whitelist rules as needed

2. **Tagging Strategy**
   - Tag production resources with "Environment:prod"
   - Tag critical resources with "DoNotDelete"
   - Use consistent naming conventions

3. **Gradual Rollout**
   - Start with non-critical resource types
   - Begin with longer age thresholds
   - Reduce thresholds gradually

4. **Cost Tracking**
   - Monitor AWS Cost Explorer before/after
   - Track savings over time
   - Adjust strategy based on results

## 🚨 Emergency Stop

To immediately stop all cleanup operations:

```bash
# Disable the EventBridge rule
aws events disable-rule \
  --name aws-cleanup-cleanup-schedule \
  --region ap-northeast-2

# Or destroy the entire module
terraform destroy
```

## 📄 License

MIT License - Use at your own risk

## ⚠️ Disclaimer

This module can permanently delete AWS resources. Always test thoroughly in dry run mode before enabling actual deletion. The authors are not responsible for any data loss or service disruption caused by this module.

---

**Remember**: The best way to reduce AWS costs is to delete what you don't need! 🎯