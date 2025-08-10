#!/usr/bin/env python3
"""
AWS Compute Resources Cost Analyzer
Provides detailed cost breakdown of all computing resources
"""

import boto3
import json
from datetime import datetime, timedelta
from tabulate import tabulate
import argparse

# AWS Region
REGION = 'ap-northeast-2'

# Approximate monthly costs (USD) - Seoul region
INSTANCE_COSTS = {
    # T4g (Graviton - ARM)
    't4g.nano': 3.07,
    't4g.micro': 6.13,
    't4g.small': 12.26,
    't4g.medium': 24.53,
    't4g.large': 49.06,
    't4g.xlarge': 98.11,
    't4g.2xlarge': 196.22,
    
    # T3 (Intel)
    't3.nano': 3.80,
    't3.micro': 7.59,
    't3.small': 15.18,
    't3.medium': 30.37,
    't3.large': 60.74,
    't3.xlarge': 121.47,
    't3.2xlarge': 242.95,
    
    # M5 (General Purpose)
    'm5.large': 70.08,
    'm5.xlarge': 140.16,
    'm5.2xlarge': 280.32,
    'm5.4xlarge': 560.64,
    
    # C5 (Compute Optimized)
    'c5.large': 62.05,
    'c5.xlarge': 124.10,
    'c5.2xlarge': 248.20,
    
    # R5 (Memory Optimized)
    'r5.large': 91.98,
    'r5.xlarge': 183.96,
    'r5.2xlarge': 367.92,
}

RDS_COSTS = {
    'db.t4g.micro': 11.68,
    'db.t4g.small': 23.36,
    'db.t4g.medium': 46.72,
    'db.t3.micro': 14.60,
    'db.t3.small': 29.20,
    'db.t3.medium': 58.40,
    'db.m5.large': 124.10,
    'db.m5.xlarge': 248.20,
}

ELASTICACHE_COSTS = {
    'cache.t4g.micro': 11.68,
    'cache.t4g.small': 23.36,
    'cache.t3.micro': 12.41,
    'cache.t3.small': 24.82,
    'cache.m5.large': 105.12,
}

class AWSComputeAnalyzer:
    def __init__(self, region=REGION):
        self.region = region
        self.ec2 = boto3.client('ec2', region_name=region)
        self.rds = boto3.client('rds', region_name=region)
        self.elasticache = boto3.client('elasticache', region_name=region)
        self.lambda_client = boto3.client('lambda', region_name=region)
        self.ecs = boto3.client('ecs', region_name=region)
        self.autoscaling = boto3.client('autoscaling', region_name=region)
        self.ce = boto3.client('ce', region_name='us-east-1')  # Cost Explorer is only in us-east-1
        
        self.total_monthly_cost = 0
        self.resources = {
            'ec2': [],
            'rds': [],
            'elasticache': [],
            'lambda': [],
            'ecs': [],
            'total_cost': 0
        }
    
    def analyze_ec2(self):
        """Analyze EC2 instances and costs"""
        print("\n📊 Analyzing EC2 Instances...")
        
        response = self.ec2.describe_instances(
            Filters=[{'Name': 'instance-state-name', 'Values': ['running', 'stopped']}]
        )
        
        instances = []
        total_cost = 0
        
        for reservation in response['Reservations']:
            for instance in reservation['Instances']:
                instance_id = instance['InstanceId']
                instance_type = instance['InstanceType']
                state = instance['State']['Name']
                
                # Get name tag
                name = 'unnamed'
                for tag in instance.get('Tags', []):
                    if tag['Key'] == 'Name':
                        name = tag['Value']
                        break
                
                # Calculate cost
                monthly_cost = INSTANCE_COSTS.get(instance_type, 50)  # Default $50 if unknown
                if state == 'stopped':
                    monthly_cost = 0  # Stopped instances don't incur compute costs
                
                instances.append({
                    'ID': instance_id,
                    'Name': name,
                    'Type': instance_type,
                    'State': state,
                    'Monthly Cost': f'${monthly_cost:.2f}'
                })
                
                total_cost += monthly_cost
        
        self.resources['ec2'] = instances
        self.total_monthly_cost += total_cost
        
        if instances:
            print(tabulate(instances, headers='keys', tablefmt='grid'))
            print(f"\nTotal EC2 Monthly Cost: ${total_cost:.2f}")
        else:
            print("No EC2 instances found.")
        
        return total_cost
    
    def analyze_rds(self):
        """Analyze RDS instances and costs"""
        print("\n📊 Analyzing RDS Instances...")
        
        response = self.rds.describe_db_instances()
        
        databases = []
        total_cost = 0
        
        for db in response['DBInstances']:
            db_id = db['DBInstanceIdentifier']
            db_class = db['DBInstanceClass']
            engine = db['Engine']
            status = db['DBInstanceStatus']
            multi_az = db['MultiAZ']
            storage = db['AllocatedStorage']
            
            # Calculate cost
            monthly_cost = RDS_COSTS.get(db_class, 100)  # Default $100 if unknown
            if multi_az:
                monthly_cost *= 2  # Multi-AZ doubles the cost
            
            # Add storage cost (~$0.115 per GB)
            storage_cost = storage * 0.115
            monthly_cost += storage_cost
            
            databases.append({
                'ID': db_id,
                'Class': db_class,
                'Engine': engine,
                'Status': status,
                'Multi-AZ': multi_az,
                'Storage GB': storage,
                'Monthly Cost': f'${monthly_cost:.2f}'
            })
            
            total_cost += monthly_cost
        
        # Check for Aurora Serverless
        clusters = self.rds.describe_db_clusters()
        for cluster in clusters['DBClusters']:
            if cluster.get('ServerlessV2ScalingConfiguration'):
                min_acu = cluster['ServerlessV2ScalingConfiguration']['MinCapacity']
                max_acu = cluster['ServerlessV2ScalingConfiguration']['MaxCapacity']
                # Estimate based on average ACU usage
                avg_acu = (min_acu + max_acu) / 2
                monthly_cost = avg_acu * 0.12 * 730  # $0.12 per ACU-hour
                
                databases.append({
                    'ID': cluster['DBClusterIdentifier'],
                    'Class': f'Serverless ({min_acu}-{max_acu} ACU)',
                    'Engine': cluster['Engine'],
                    'Status': cluster['Status'],
                    'Multi-AZ': True,
                    'Storage GB': 'Auto',
                    'Monthly Cost': f'${monthly_cost:.2f}'
                })
                
                total_cost += monthly_cost
        
        self.resources['rds'] = databases
        self.total_monthly_cost += total_cost
        
        if databases:
            print(tabulate(databases, headers='keys', tablefmt='grid'))
            print(f"\nTotal RDS Monthly Cost: ${total_cost:.2f}")
        else:
            print("No RDS instances found.")
        
        return total_cost
    
    def analyze_elasticache(self):
        """Analyze ElastiCache clusters and costs"""
        print("\n📊 Analyzing ElastiCache Clusters...")
        
        response = self.elasticache.describe_cache_clusters()
        
        clusters = []
        total_cost = 0
        
        for cluster in response['CacheClusters']:
            cluster_id = cluster['CacheClusterId']
            node_type = cluster['CacheNodeType']
            engine = cluster['Engine']
            status = cluster['CacheClusterStatus']
            num_nodes = cluster['NumCacheNodes']
            
            # Calculate cost
            monthly_cost = ELASTICACHE_COSTS.get(node_type, 50) * num_nodes
            
            clusters.append({
                'ID': cluster_id,
                'Node Type': node_type,
                'Engine': engine,
                'Status': status,
                'Nodes': num_nodes,
                'Monthly Cost': f'${monthly_cost:.2f}'
            })
            
            total_cost += monthly_cost
        
        self.resources['elasticache'] = clusters
        self.total_monthly_cost += total_cost
        
        if clusters:
            print(tabulate(clusters, headers='keys', tablefmt='grid'))
            print(f"\nTotal ElastiCache Monthly Cost: ${total_cost:.2f}")
        else:
            print("No ElastiCache clusters found.")
        
        return total_cost
    
    def analyze_lambda(self):
        """Analyze Lambda functions and estimate costs"""
        print("\n📊 Analyzing Lambda Functions...")
        
        response = self.lambda_client.list_functions()
        
        functions = []
        total_cost = 0
        
        for func in response['Functions']:
            func_name = func['FunctionName']
            runtime = func['Runtime']
            memory = func['MemorySize']
            timeout = func['Timeout']
            
            # Get invocation metrics (estimate)
            # Lambda costs: $0.20 per 1M requests + $0.00001667 per GB-second
            # Assuming 100K invocations per month, 100ms average duration
            invocations = 100000
            duration_ms = 100
            gb_seconds = (memory / 1024) * (duration_ms / 1000) * invocations
            
            request_cost = (invocations / 1000000) * 0.20
            compute_cost = gb_seconds * 0.00001667
            monthly_cost = request_cost + compute_cost
            
            functions.append({
                'Name': func_name[:30],
                'Runtime': runtime,
                'Memory MB': memory,
                'Timeout': timeout,
                'Est. Monthly Cost': f'${monthly_cost:.2f}'
            })
            
            total_cost += monthly_cost
        
        self.resources['lambda'] = functions
        self.total_monthly_cost += total_cost
        
        if functions:
            print(tabulate(functions[:10], headers='keys', tablefmt='grid'))  # Show first 10
            if len(functions) > 10:
                print(f"... and {len(functions) - 10} more functions")
            print(f"\nTotal Lambda Monthly Cost (estimated): ${total_cost:.2f}")
        else:
            print("No Lambda functions found.")
        
        return total_cost
    
    def analyze_ecs(self):
        """Analyze ECS/Fargate services"""
        print("\n📊 Analyzing ECS/Fargate Services...")
        
        clusters = self.ecs.list_clusters()
        
        services_list = []
        total_cost = 0
        
        for cluster_arn in clusters['clusterArns']:
            cluster_name = cluster_arn.split('/')[-1]
            
            # Get services
            services = self.ecs.list_services(cluster=cluster_arn)
            
            for service_arn in services.get('serviceArns', []):
                service_details = self.ecs.describe_services(
                    cluster=cluster_arn,
                    services=[service_arn]
                )
                
                for service in service_details['services']:
                    service_name = service['serviceName']
                    launch_type = service.get('launchType', 'EC2')
                    desired = service['desiredCount']
                    running = service['runningCount']
                    
                    # Estimate Fargate costs (0.04048 per vCPU hour + 0.004445 per GB hour)
                    if launch_type == 'FARGATE':
                        # Assume 0.25 vCPU, 0.5 GB per task
                        vcpu_cost = 0.25 * 0.04048 * 730 * running
                        memory_cost = 0.5 * 0.004445 * 730 * running
                        monthly_cost = vcpu_cost + memory_cost
                    else:
                        monthly_cost = 0  # EC2 launch type cost included in EC2 instances
                    
                    services_list.append({
                        'Cluster': cluster_name,
                        'Service': service_name[:20],
                        'Type': launch_type,
                        'Desired': desired,
                        'Running': running,
                        'Monthly Cost': f'${monthly_cost:.2f}'
                    })
                    
                    total_cost += monthly_cost
        
        self.resources['ecs'] = services_list
        self.total_monthly_cost += total_cost
        
        if services_list:
            print(tabulate(services_list, headers='keys', tablefmt='grid'))
            print(f"\nTotal ECS/Fargate Monthly Cost: ${total_cost:.2f}")
        else:
            print("No ECS services found.")
        
        return total_cost
    
    def get_actual_costs(self):
        """Get actual costs from AWS Cost Explorer"""
        print("\n📊 Getting Actual Costs from AWS Cost Explorer...")
        
        end_date = datetime.now().date()
        start_date = end_date - timedelta(days=30)
        
        try:
            response = self.ce.get_cost_and_usage(
                TimePeriod={
                    'Start': start_date.isoformat(),
                    'End': end_date.isoformat()
                },
                Granularity='MONTHLY',
                Metrics=['UnblendedCost'],
                GroupBy=[
                    {'Type': 'DIMENSION', 'Key': 'SERVICE'}
                ]
            )
            
            print("\nActual Costs (Last 30 Days):")
            print("-" * 50)
            
            total_actual = 0
            for result in response['ResultsByTime']:
                for group in result['Groups']:
                    service = group['Keys'][0]
                    cost = float(group['Metrics']['UnblendedCost']['Amount'])
                    
                    if cost > 0.01:  # Only show services with costs > $0.01
                        print(f"{service[:40]:<40} ${cost:>10.2f}")
                        total_actual += cost
            
            print("-" * 50)
            print(f"{'Total Actual Cost':<40} ${total_actual:>10.2f}")
            
        except Exception as e:
            print(f"Could not retrieve actual costs: {e}")
    
    def generate_summary(self):
        """Generate cost summary and recommendations"""
        print("\n" + "=" * 60)
        print("💰 COST SUMMARY")
        print("=" * 60)
        
        summary = [
            ['Service', 'Resources', 'Estimated Monthly Cost'],
            ['EC2 Instances', len(self.resources['ec2']), f"${sum(float(i['Monthly Cost'][1:]) for i in self.resources['ec2']):.2f}"],
            ['RDS Databases', len(self.resources['rds']), f"${sum(float(i['Monthly Cost'][1:]) for i in self.resources['rds']):.2f}"],
            ['ElastiCache', len(self.resources['elasticache']), f"${sum(float(i['Monthly Cost'][1:]) for i in self.resources['elasticache']):.2f}"],
            ['Lambda Functions', len(self.resources['lambda']), f"${sum(float(i['Est. Monthly Cost'][1:]) for i in self.resources['lambda']):.2f}"],
            ['ECS/Fargate', len(self.resources['ecs']), f"${sum(float(i['Monthly Cost'][1:]) for i in self.resources['ecs']):.2f}"],
        ]
        
        print(tabulate(summary, headers='firstrow', tablefmt='grid'))
        print(f"\n🎯 Total Estimated Monthly Cost: ${self.total_monthly_cost:.2f}")
        print(f"📅 Estimated Annual Cost: ${self.total_monthly_cost * 12:.2f}")
        
        # Recommendations
        print("\n" + "=" * 60)
        print("💡 COST OPTIMIZATION RECOMMENDATIONS")
        print("=" * 60)
        
        recommendations = []
        
        # Check for stopped instances
        stopped_instances = [i for i in self.resources['ec2'] if 'stopped' in i['State']]
        if stopped_instances:
            recommendations.append(f"• You have {len(stopped_instances)} stopped EC2 instances. Consider terminating them.")
        
        # Check for expensive instance types
        expensive_instances = [i for i in self.resources['ec2'] if float(i['Monthly Cost'][1:]) > 100]
        if expensive_instances:
            recommendations.append(f"• {len(expensive_instances)} EC2 instances cost over $100/month. Consider using smaller instances or spot instances.")
        
        # Check for Multi-AZ RDS
        multi_az_dbs = [d for d in self.resources['rds'] if d.get('Multi-AZ')]
        if multi_az_dbs:
            recommendations.append(f"• {len(multi_az_dbs)} RDS instances use Multi-AZ. Consider Single-AZ for non-production.")
        
        # Lambda recommendations
        if len(self.resources['lambda']) > 20:
            recommendations.append(f"• You have {len(self.resources['lambda'])} Lambda functions. Review and remove unused ones.")
        
        # General recommendations
        recommendations.extend([
            "• Consider using Graviton (ARM) instances for 20% cost savings",
            "• Implement auto-scaling to reduce costs during low-traffic periods",
            "• Use Reserved Instances or Savings Plans for predictable workloads",
            "• Enable S3 Intelligent-Tiering for automatic storage optimization",
            "• Set up AWS Budgets to monitor and control costs"
        ])
        
        for rec in recommendations:
            print(rec)
        
        print("\n" + "=" * 60)

def main():
    parser = argparse.ArgumentParser(description='AWS Compute Resources Cost Analyzer')
    parser.add_argument('--region', default=REGION, help='AWS region')
    parser.add_argument('--export', action='store_true', help='Export report to JSON')
    args = parser.parse_args()
    
    print("=" * 60)
    print("AWS COMPUTE RESOURCES COST ANALYZER")
    print(f"Region: {args.region}")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    
    analyzer = AWSComputeAnalyzer(region=args.region)
    
    # Analyze all services
    analyzer.analyze_ec2()
    analyzer.analyze_rds()
    analyzer.analyze_elasticache()
    analyzer.analyze_lambda()
    analyzer.analyze_ecs()
    
    # Get actual costs if available
    analyzer.get_actual_costs()
    
    # Generate summary
    analyzer.generate_summary()
    
    # Export if requested
    if args.export:
        filename = f"aws-cost-report-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
        with open(filename, 'w') as f:
            json.dump({
                'timestamp': datetime.now().isoformat(),
                'region': args.region,
                'resources': analyzer.resources,
                'total_monthly_cost': analyzer.total_monthly_cost
            }, f, indent=2, default=str)
        print(f"\n📁 Report exported to: {filename}")

if __name__ == '__main__':
    main()