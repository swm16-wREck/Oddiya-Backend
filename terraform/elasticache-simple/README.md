# Simple ElastiCache Redis Module

A cost-optimized Terraform module for deploying ElastiCache Redis when you need it. Perfect for small services and development environments.

## 💰 Cost Optimization

| Configuration | Monthly Cost | Use Case |
|--------------|--------------|----------|
| **Single Node t4g.micro** | ~$12 | Development, small cache |
| **Single Node t4g.small** | ~$25 | Staging, medium cache |
| **2-Node Cluster t4g.micro** | ~$25 | Production with HA |
| **2-Node Cluster t4g.small** | ~$50 | Production, larger cache |

## 🚀 Quick Start

### 1. Remove Existing ElastiCache (if any)

First, check and remove any existing ElastiCache resources:

```bash
# Check what ElastiCache resources exist (dry run)
cd scripts
./remove-elasticache.sh

# Actually remove resources
DRY_RUN=false ./remove-elasticache.sh
```

### 2. Deploy When Needed

When you need Redis caching:

```bash
cd terraform/elasticache-simple

# Configure
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your settings

# Deploy
terraform init
terraform plan
terraform apply
```

## 📝 Configuration Examples

### Minimal Development Setup (~$12/month)

```hcl
# terraform.tfvars
deployment_mode = "single"
node_type       = "cache.t4g.micro"
environment     = "dev"

# Disable unnecessary features
enable_encryption     = false
enable_logs          = false
backup_retention_days = 0
```

### Production Setup with HA (~$50/month)

```hcl
# terraform.tfvars
deployment_mode = "cluster"
node_type       = "cache.t4g.small"
num_cache_nodes = 2
environment     = "prod"

# Enable security features
enable_encryption     = true
enable_monitoring     = true
backup_retention_days = 7
```

## 🔧 Features

### Deployment Modes

1. **Single Node** (`deployment_mode = "single"`)
   - Cheapest option
   - Good for development
   - No automatic failover
   - ~$12-50/month

2. **Replication Group** (`deployment_mode = "cluster"`)
   - High availability
   - Automatic failover
   - Read replicas
   - ~$25-100/month

### Cost-Saving Features

- **Graviton Instances** - ARM-based, 20% cheaper
- **Minimal Backups** - 1 day retention by default
- **No Logs** - CloudWatch logs disabled by default
- **Single AZ** - For development environments
- **Connection Pooling** - Reduce connection overhead

## 📊 Monitoring

Basic CloudWatch alarms included:
- CPU utilization > 75%
- Memory usage > 80%

## 🔗 Application Integration

### Node.js Example

```javascript
const redis = require('redis');

const client = redis.createClient({
  host: process.env.REDIS_ENDPOINT,
  port: 6379,
  // If encryption enabled:
  // password: process.env.REDIS_AUTH_TOKEN
});

// Connection pooling
client.on('error', (err) => {
  console.log('Redis error:', err);
});

client.on('connect', () => {
  console.log('Connected to Redis');
});
```

### Python Example

```python
import redis

# Simple connection
r = redis.Redis(
    host=os.environ['REDIS_ENDPOINT'],
    port=6379,
    decode_responses=True
)

# Connection pool (recommended)
pool = redis.ConnectionPool(
    host=os.environ['REDIS_ENDPOINT'],
    port=6379,
    max_connections=10
)
r = redis.Redis(connection_pool=pool)
```

## 🛡️ Security

### Network Security
- Deployed in private subnets
- Security group with restricted access
- VPC-only access (no public endpoint)

### Optional Encryption
- At-rest encryption
- In-transit encryption (TLS)
- Auth token stored in SSM

## 📈 Scaling

### When to Scale Up

**From t4g.micro to t4g.small:**
- Memory usage consistently > 80%
- CPU usage consistently > 75%
- Evictions happening frequently

**From Single to Cluster:**
- Need high availability
- Read-heavy workload
- Business-critical caching

### Memory Optimization

Choose the right eviction policy:
- `allkeys-lru` - General purpose (default)
- `volatile-lru` - Only evict keys with TTL
- `allkeys-lfu` - Least frequently used
- `noeviction` - Never evict (returns errors)

## 🔄 Maintenance

### Backup Strategy

For cost optimization:
- **Dev**: No backups (`backup_retention_days = 0`)
- **Staging**: 1 day retention
- **Production**: 7 days retention

### Updates

ElastiCache handles:
- Automatic minor version updates
- Security patches
- Maintenance windows

## 💡 Cost-Saving Tips

1. **Use Single Node for Dev/Test**
   - Save 50% compared to cluster mode
   - Sufficient for most development needs

2. **Use Graviton (t4g) Instances**
   - 20% cheaper than t3 instances
   - Same or better performance

3. **Disable Unnecessary Features**
   - CloudWatch logs: Save ~$5/month
   - Backups: Save storage costs
   - Multi-AZ: Save 50% for non-production

4. **Right-Size Your Cache**
   - Monitor memory usage
   - Scale down if < 50% utilized
   - Use eviction policies effectively

5. **Use Connection Pooling**
   - Reduce connection overhead
   - Better resource utilization

## 🚨 When NOT to Use ElastiCache

Consider alternatives for:
- **Session storage only** → Use DynamoDB (pay-per-request)
- **Infrequent access** → Use S3 with CloudFront
- **Small datasets** → Use application memory
- **Development only** → Use local Redis container

## 📝 Cleanup

When you no longer need ElastiCache:

```bash
# Remove with Terraform
terraform destroy

# Or use the cleanup script
cd ../../scripts
DRY_RUN=false ./remove-elasticache.sh
```

## 🔍 Troubleshooting

### Connection Issues
- Check security group rules
- Verify subnet connectivity
- Ensure VPC endpoints if needed

### Memory Issues
- Check eviction policy
- Monitor key expiration
- Consider scaling up

### Performance Issues
- Check CPU utilization
- Monitor network throughput
- Consider cluster mode for reads

## 📚 Resources

- [ElastiCache Pricing](https://aws.amazon.com/elasticache/pricing/)
- [Redis Best Practices](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/BestPractices.html)
- [Cost Optimization](https://aws.amazon.com/elasticache/cost-optimization/)

---

**Remember**: For small services, you might not need ElastiCache at all. Consider if application-level caching or other solutions would be more cost-effective! 💰