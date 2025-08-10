#!/usr/bin/env python3
"""
AWS Resource Cleanup Lambda
Identifies and removes unused AWS resources to reduce costs
"""

import json
import os
import boto3
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Any

# Environment variables
DRY_RUN = os.environ.get('DRY_RUN', 'true').lower() == 'true'
SNS_TOPIC_ARN = os.environ.get('SNS_TOPIC_ARN')
REGION = os.environ.get('REGION', 'ap-northeast-2')
AGE_THRESHOLD_DAYS = int(os.environ.get('AGE_THRESHOLD_DAYS', '30'))

# Feature flags
CLEAN_EBS_VOLUMES = os.environ.get('CLEAN_EBS_VOLUMES', 'true').lower() == 'true'
CLEAN_EBS_SNAPSHOTS = os.environ.get('CLEAN_EBS_SNAPSHOTS', 'true').lower() == 'true'
CLEAN_ELASTIC_IPS = os.environ.get('CLEAN_ELASTIC_IPS', 'true').lower() == 'true'
CLEAN_LOAD_BALANCERS = os.environ.get('CLEAN_LOAD_BALANCERS', 'true').lower() == 'true'
CLEAN_NAT_GATEWAYS = os.environ.get('CLEAN_NAT_GATEWAYS', 'true').lower() == 'true'
CLEAN_RDS_SNAPSHOTS = os.environ.get('CLEAN_RDS_SNAPSHOTS', 'true').lower() == 'true'
CLEAN_AMI_IMAGES = os.environ.get('CLEAN_AMI_IMAGES', 'true').lower() == 'true'
CLEAN_ECR_IMAGES = os.environ.get('CLEAN_ECR_IMAGES', 'true').lower() == 'true'
CLEAN_CLOUDWATCH_LOGS = os.environ.get('CLEAN_CLOUDWATCH_LOGS', 'true').lower() == 'true'
CLEAN_S3_BUCKETS = os.environ.get('CLEAN_S3_BUCKETS', 'true').lower() == 'true'
CLEAN_SECURITY_GROUPS = os.environ.get('CLEAN_SECURITY_GROUPS', 'true').lower() == 'true'
CLEAN_LAMBDA_FUNCTIONS = os.environ.get('CLEAN_LAMBDA_FUNCTIONS', 'true').lower() == 'true'

# Whitelist configuration
WHITELIST_TAGS = os.environ.get('WHITELIST_TAGS', '').split(',')
WHITELIST_PREFIXES = os.environ.get('WHITELIST_PREFIXES', '').split(',')

# Initialize AWS clients
ec2 = boto3.client('ec2', region_name=REGION)
elb = boto3.client('elbv2', region_name=REGION)
rds = boto3.client('rds', region_name=REGION)
s3 = boto3.client('s3', region_name=REGION)
ecr = boto3.client('ecr', region_name=REGION)
lambda_client = boto3.client('lambda', region_name=REGION)
logs = boto3.client('logs', region_name=REGION)
sns = boto3.client('sns', region_name=REGION)

def handler(event, context):
    """Main Lambda handler"""
    print(f"Starting resource cleanup scan (DRY_RUN={DRY_RUN})")
    
    cleanup_summary = {
        'timestamp': datetime.now(timezone.utc).isoformat(),
        'dry_run': DRY_RUN,
        'resources_found': {},
        'resources_deleted': {},
        'estimated_monthly_savings': 0,
        'errors': []
    }
    
    try:
        # EBS Volumes
        if CLEAN_EBS_VOLUMES:
            cleanup_ebs_volumes(cleanup_summary)
        
        # EBS Snapshots
        if CLEAN_EBS_SNAPSHOTS:
            cleanup_ebs_snapshots(cleanup_summary)
        
        # Elastic IPs
        if CLEAN_ELASTIC_IPS:
            cleanup_elastic_ips(cleanup_summary)
        
        # Load Balancers
        if CLEAN_LOAD_BALANCERS:
            cleanup_load_balancers(cleanup_summary)
        
        # NAT Gateways
        if CLEAN_NAT_GATEWAYS:
            cleanup_nat_gateways(cleanup_summary)
        
        # RDS Snapshots
        if CLEAN_RDS_SNAPSHOTS:
            cleanup_rds_snapshots(cleanup_summary)
        
        # AMI Images
        if CLEAN_AMI_IMAGES:
            cleanup_ami_images(cleanup_summary)
        
        # ECR Images
        if CLEAN_ECR_IMAGES:
            cleanup_ecr_images(cleanup_summary)
        
        # CloudWatch Logs
        if CLEAN_CLOUDWATCH_LOGS:
            cleanup_cloudwatch_logs(cleanup_summary)
        
        # S3 Buckets
        if CLEAN_S3_BUCKETS:
            cleanup_empty_s3_buckets(cleanup_summary)
        
        # Security Groups
        if CLEAN_SECURITY_GROUPS:
            cleanup_security_groups(cleanup_summary)
        
        # Lambda Functions
        if CLEAN_LAMBDA_FUNCTIONS:
            cleanup_old_lambda_versions(cleanup_summary)
        
    except Exception as e:
        cleanup_summary['errors'].append(str(e))
        print(f"Error during cleanup: {e}")
    
    # Send notification
    send_notification(cleanup_summary)
    
    # Log summary
    print(f"CLEANUP_SUMMARY: {json.dumps(cleanup_summary)}")
    
    return cleanup_summary

def is_whitelisted(resource_name: str, tags: Dict = None) -> bool:
    """Check if resource is whitelisted"""
    # Check name prefixes
    for prefix in WHITELIST_PREFIXES:
        if prefix and resource_name.startswith(prefix):
            return True
    
    # Check tags
    if tags:
        for tag_key in WHITELIST_TAGS:
            if tag_key and tag_key in tags:
                return True
    
    return False

def is_old_enough(create_time) -> bool:
    """Check if resource is old enough to be cleaned"""
    if not create_time:
        return True
    
    if isinstance(create_time, str):
        create_time = datetime.fromisoformat(create_time.replace('Z', '+00:00'))
    
    age = datetime.now(timezone.utc) - create_time
    return age.days >= AGE_THRESHOLD_DAYS

def cleanup_ebs_volumes(summary: Dict):
    """Clean up unattached EBS volumes"""
    try:
        response = ec2.describe_volumes(
            Filters=[
                {'Name': 'status', 'Values': ['available']}
            ]
        )
        
        volumes_to_delete = []
        total_size = 0
        
        for volume in response['Volumes']:
            volume_id = volume['VolumeId']
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in volume.get('Tags', [])}
            if is_whitelisted(volume_id, tags):
                continue
            
            # Check age
            if not is_old_enough(volume['CreateTime']):
                continue
            
            volumes_to_delete.append(volume_id)
            total_size += volume['Size']
            
            if not DRY_RUN:
                try:
                    ec2.delete_volume(VolumeId=volume_id)
                    print(f"Deleted EBS volume: {volume_id} ({volume['Size']} GB)")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete volume {volume_id}: {e}")
        
        summary['resources_found']['ebs_volumes'] = len(volumes_to_delete)
        summary['resources_deleted']['ebs_volumes'] = len(volumes_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += total_size * 0.10  # ~$0.10 per GB
        
    except Exception as e:
        summary['errors'].append(f"EBS volume cleanup error: {e}")

def cleanup_ebs_snapshots(summary: Dict):
    """Clean up old EBS snapshots"""
    try:
        response = ec2.describe_snapshots(OwnerIds=['self'])
        
        snapshots_to_delete = []
        total_size = 0
        
        for snapshot in response['Snapshots']:
            snapshot_id = snapshot['SnapshotId']
            
            # Skip if AMI snapshot
            if snapshot.get('Description', '').startswith('Created by CreateImage'):
                continue
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in snapshot.get('Tags', [])}
            if is_whitelisted(snapshot_id, tags):
                continue
            
            # Check age
            if not is_old_enough(snapshot['StartTime']):
                continue
            
            snapshots_to_delete.append(snapshot_id)
            total_size += snapshot.get('VolumeSize', 0)
            
            if not DRY_RUN:
                try:
                    ec2.delete_snapshot(SnapshotId=snapshot_id)
                    print(f"Deleted EBS snapshot: {snapshot_id}")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete snapshot {snapshot_id}: {e}")
        
        summary['resources_found']['ebs_snapshots'] = len(snapshots_to_delete)
        summary['resources_deleted']['ebs_snapshots'] = len(snapshots_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += total_size * 0.05  # ~$0.05 per GB
        
    except Exception as e:
        summary['errors'].append(f"EBS snapshot cleanup error: {e}")

def cleanup_elastic_ips(summary: Dict):
    """Clean up unassociated Elastic IPs"""
    try:
        response = ec2.describe_addresses()
        
        eips_to_release = []
        
        for eip in response['Addresses']:
            # Check if associated
            if 'InstanceId' in eip or 'NetworkInterfaceId' in eip:
                continue
            
            allocation_id = eip.get('AllocationId')
            public_ip = eip.get('PublicIp')
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in eip.get('Tags', [])}
            if is_whitelisted(public_ip or allocation_id, tags):
                continue
            
            eips_to_release.append(allocation_id or public_ip)
            
            if not DRY_RUN:
                try:
                    if allocation_id:
                        ec2.release_address(AllocationId=allocation_id)
                    else:
                        ec2.release_address(PublicIp=public_ip)
                    print(f"Released Elastic IP: {public_ip}")
                except Exception as e:
                    summary['errors'].append(f"Failed to release EIP {public_ip}: {e}")
        
        summary['resources_found']['elastic_ips'] = len(eips_to_release)
        summary['resources_deleted']['elastic_ips'] = len(eips_to_release) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += len(eips_to_release) * 3.60  # $3.60 per EIP
        
    except Exception as e:
        summary['errors'].append(f"Elastic IP cleanup error: {e}")

def cleanup_load_balancers(summary: Dict):
    """Clean up unused load balancers"""
    try:
        response = elb.describe_load_balancers()
        
        lbs_to_delete = []
        
        for lb in response['LoadBalancers']:
            lb_arn = lb['LoadBalancerArn']
            lb_name = lb['LoadBalancerName']
            
            # Check if whitelisted
            if is_whitelisted(lb_name):
                continue
            
            # Check if it has any targets
            try:
                target_groups = elb.describe_target_groups(
                    LoadBalancerArn=lb_arn
                )
                
                has_targets = False
                for tg in target_groups['TargetGroups']:
                    health = elb.describe_target_health(
                        TargetGroupArn=tg['TargetGroupArn']
                    )
                    if health['TargetHealthDescriptions']:
                        has_targets = True
                        break
                
                if has_targets:
                    continue
                
            except:
                continue
            
            # Check age
            if not is_old_enough(lb['CreatedTime']):
                continue
            
            lbs_to_delete.append(lb_arn)
            
            if not DRY_RUN:
                try:
                    elb.delete_load_balancer(LoadBalancerArn=lb_arn)
                    print(f"Deleted Load Balancer: {lb_name}")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete LB {lb_name}: {e}")
        
        summary['resources_found']['load_balancers'] = len(lbs_to_delete)
        summary['resources_deleted']['load_balancers'] = len(lbs_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += len(lbs_to_delete) * 22.50  # ~$22.50 per ALB
        
    except Exception as e:
        summary['errors'].append(f"Load balancer cleanup error: {e}")

def cleanup_nat_gateways(summary: Dict):
    """Clean up unused NAT gateways"""
    try:
        response = ec2.describe_nat_gateways(
            Filter=[
                {'Name': 'state', 'Values': ['available']}
            ]
        )
        
        nats_to_delete = []
        
        for nat in response['NatGateways']:
            nat_id = nat['NatGatewayId']
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in nat.get('Tags', [])}
            if is_whitelisted(nat_id, tags):
                continue
            
            # Check age
            if not is_old_enough(nat['CreateTime']):
                continue
            
            nats_to_delete.append(nat_id)
            
            if not DRY_RUN:
                try:
                    ec2.delete_nat_gateway(NatGatewayId=nat_id)
                    print(f"Deleted NAT Gateway: {nat_id}")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete NAT {nat_id}: {e}")
        
        summary['resources_found']['nat_gateways'] = len(nats_to_delete)
        summary['resources_deleted']['nat_gateways'] = len(nats_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += len(nats_to_delete) * 45  # $45 per NAT
        
    except Exception as e:
        summary['errors'].append(f"NAT gateway cleanup error: {e}")

def cleanup_rds_snapshots(summary: Dict):
    """Clean up old RDS snapshots"""
    try:
        # DB snapshots
        response = rds.describe_db_snapshots(SnapshotType='manual')
        
        snapshots_to_delete = []
        
        for snapshot in response['DBSnapshots']:
            snapshot_id = snapshot['DBSnapshotIdentifier']
            
            # Check if whitelisted
            if is_whitelisted(snapshot_id):
                continue
            
            # Check age
            if not is_old_enough(snapshot['SnapshotCreateTime']):
                continue
            
            snapshots_to_delete.append(snapshot_id)
            
            if not DRY_RUN:
                try:
                    rds.delete_db_snapshot(DBSnapshotIdentifier=snapshot_id)
                    print(f"Deleted RDS snapshot: {snapshot_id}")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete RDS snapshot {snapshot_id}: {e}")
        
        # Cluster snapshots
        response = rds.describe_db_cluster_snapshots(SnapshotType='manual')
        
        for snapshot in response['DBClusterSnapshots']:
            snapshot_id = snapshot['DBClusterSnapshotIdentifier']
            
            if is_whitelisted(snapshot_id):
                continue
            
            if not is_old_enough(snapshot['SnapshotCreateTime']):
                continue
            
            snapshots_to_delete.append(snapshot_id)
            
            if not DRY_RUN:
                try:
                    rds.delete_db_cluster_snapshot(DBClusterSnapshotIdentifier=snapshot_id)
                    print(f"Deleted RDS cluster snapshot: {snapshot_id}")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete cluster snapshot {snapshot_id}: {e}")
        
        summary['resources_found']['rds_snapshots'] = len(snapshots_to_delete)
        summary['resources_deleted']['rds_snapshots'] = len(snapshots_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += len(snapshots_to_delete) * 5  # Estimate
        
    except Exception as e:
        summary['errors'].append(f"RDS snapshot cleanup error: {e}")

def cleanup_ami_images(summary: Dict):
    """Clean up old AMI images"""
    try:
        response = ec2.describe_images(Owners=['self'])
        
        amis_to_delete = []
        
        for image in response['Images']:
            ami_id = image['ImageId']
            ami_name = image.get('Name', '')
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in image.get('Tags', [])}
            if is_whitelisted(ami_name, tags):
                continue
            
            # Check age
            creation_date = datetime.fromisoformat(image['CreationDate'].replace('Z', '+00:00'))
            if not is_old_enough(creation_date):
                continue
            
            amis_to_delete.append(ami_id)
            
            if not DRY_RUN:
                try:
                    ec2.deregister_image(ImageId=ami_id)
                    print(f"Deregistered AMI: {ami_id} ({ami_name})")
                    
                    # Delete associated snapshots
                    for device in image.get('BlockDeviceMappings', []):
                        if 'Ebs' in device and 'SnapshotId' in device['Ebs']:
                            try:
                                ec2.delete_snapshot(SnapshotId=device['Ebs']['SnapshotId'])
                            except:
                                pass
                                
                except Exception as e:
                    summary['errors'].append(f"Failed to deregister AMI {ami_id}: {e}")
        
        summary['resources_found']['ami_images'] = len(amis_to_delete)
        summary['resources_deleted']['ami_images'] = len(amis_to_delete) if not DRY_RUN else 0
        summary['estimated_monthly_savings'] += len(amis_to_delete) * 2  # Storage estimate
        
    except Exception as e:
        summary['errors'].append(f"AMI cleanup error: {e}")

def cleanup_ecr_images(summary: Dict):
    """Clean up old ECR images"""
    try:
        repositories = ecr.describe_repositories()
        
        total_images_deleted = 0
        
        for repo in repositories['repositories']:
            repo_name = repo['repositoryName']
            
            if is_whitelisted(repo_name):
                continue
            
            try:
                images = ecr.list_images(repositoryName=repo_name)
                
                images_to_delete = []
                for image in images['imageIds']:
                    if 'imageTag' not in image:
                        continue
                    
                    # Keep latest and prod tags
                    if image['imageTag'] in ['latest', 'prod', 'production', 'stable']:
                        continue
                    
                    images_to_delete.append(image)
                
                # Keep only last 10 images
                if len(images_to_delete) > 10:
                    images_to_delete = images_to_delete[:-10]
                    
                    if not DRY_RUN and images_to_delete:
                        ecr.batch_delete_image(
                            repositoryName=repo_name,
                            imageIds=images_to_delete
                        )
                        print(f"Deleted {len(images_to_delete)} old images from {repo_name}")
                    
                    total_images_deleted += len(images_to_delete)
                    
            except Exception as e:
                summary['errors'].append(f"Failed to clean ECR repo {repo_name}: {e}")
        
        summary['resources_found']['ecr_images'] = total_images_deleted
        summary['resources_deleted']['ecr_images'] = total_images_deleted if not DRY_RUN else 0
        
    except Exception as e:
        summary['errors'].append(f"ECR cleanup error: {e}")

def cleanup_cloudwatch_logs(summary: Dict):
    """Clean up old CloudWatch log groups"""
    try:
        response = logs.describe_log_groups()
        
        log_groups_to_delete = []
        
        for log_group in response['logGroups']:
            lg_name = log_group['logGroupName']
            
            if is_whitelisted(lg_name):
                continue
            
            # Check last event time
            if 'lastEventTimestamp' in log_group:
                last_event = datetime.fromtimestamp(log_group['lastEventTimestamp'] / 1000, tz=timezone.utc)
                if not is_old_enough(last_event):
                    continue
            
            # Check if empty
            streams = logs.describe_log_streams(
                logGroupName=lg_name,
                limit=1
            )
            
            if not streams['logStreams']:
                log_groups_to_delete.append(lg_name)
                
                if not DRY_RUN:
                    try:
                        logs.delete_log_group(logGroupName=lg_name)
                        print(f"Deleted log group: {lg_name}")
                    except Exception as e:
                        summary['errors'].append(f"Failed to delete log group {lg_name}: {e}")
        
        summary['resources_found']['cloudwatch_logs'] = len(log_groups_to_delete)
        summary['resources_deleted']['cloudwatch_logs'] = len(log_groups_to_delete) if not DRY_RUN else 0
        
    except Exception as e:
        summary['errors'].append(f"CloudWatch logs cleanup error: {e}")

def cleanup_empty_s3_buckets(summary: Dict):
    """Clean up empty S3 buckets"""
    try:
        response = s3.list_buckets()
        
        buckets_to_delete = []
        
        for bucket in response['Buckets']:
            bucket_name = bucket['Name']
            
            if is_whitelisted(bucket_name):
                continue
            
            try:
                # Check if bucket is empty
                objects = s3.list_objects_v2(Bucket=bucket_name, MaxKeys=1)
                
                if 'Contents' not in objects:
                    # Check age
                    if not is_old_enough(bucket['CreationDate']):
                        continue
                    
                    buckets_to_delete.append(bucket_name)
                    
                    if not DRY_RUN:
                        try:
                            # Delete bucket
                            s3.delete_bucket(Bucket=bucket_name)
                            print(f"Deleted empty S3 bucket: {bucket_name}")
                        except Exception as e:
                            summary['errors'].append(f"Failed to delete bucket {bucket_name}: {e}")
                            
            except Exception as e:
                continue
        
        summary['resources_found']['s3_buckets'] = len(buckets_to_delete)
        summary['resources_deleted']['s3_buckets'] = len(buckets_to_delete) if not DRY_RUN else 0
        
    except Exception as e:
        summary['errors'].append(f"S3 cleanup error: {e}")

def cleanup_security_groups(summary: Dict):
    """Clean up unused security groups"""
    try:
        response = ec2.describe_security_groups()
        
        sgs_to_delete = []
        
        # Get all network interfaces to check SG usage
        interfaces = ec2.describe_network_interfaces()
        used_sgs = set()
        for interface in interfaces['NetworkInterfaces']:
            for group in interface['Groups']:
                used_sgs.add(group['GroupId'])
        
        for sg in response['SecurityGroups']:
            sg_id = sg['GroupId']
            sg_name = sg['GroupName']
            
            # Skip default SG
            if sg_name == 'default':
                continue
            
            # Skip if in use
            if sg_id in used_sgs:
                continue
            
            # Check if whitelisted
            tags = {tag['Key']: tag['Value'] for tag in sg.get('Tags', [])}
            if is_whitelisted(sg_name, tags):
                continue
            
            sgs_to_delete.append(sg_id)
            
            if not DRY_RUN:
                try:
                    ec2.delete_security_group(GroupId=sg_id)
                    print(f"Deleted security group: {sg_id} ({sg_name})")
                except Exception as e:
                    summary['errors'].append(f"Failed to delete SG {sg_id}: {e}")
        
        summary['resources_found']['security_groups'] = len(sgs_to_delete)
        summary['resources_deleted']['security_groups'] = len(sgs_to_delete) if not DRY_RUN else 0
        
    except Exception as e:
        summary['errors'].append(f"Security group cleanup error: {e}")

def cleanup_old_lambda_versions(summary: Dict):
    """Clean up old Lambda function versions"""
    try:
        functions = lambda_client.list_functions()
        
        total_versions_deleted = 0
        
        for function in functions['Functions']:
            function_name = function['FunctionName']
            
            if is_whitelisted(function_name):
                continue
            
            try:
                versions = lambda_client.list_versions_by_function(
                    FunctionName=function_name
                )
                
                # Keep only latest 3 versions
                versions_to_delete = []
                for version in versions['Versions'][:-3]:
                    if version['Version'] != '$LATEST':
                        versions_to_delete.append(version['Version'])
                
                for version in versions_to_delete:
                    if not DRY_RUN:
                        try:
                            lambda_client.delete_function(
                                FunctionName=function_name,
                                Qualifier=version
                            )
                            print(f"Deleted Lambda version: {function_name}:{version}")
                            total_versions_deleted += 1
                        except Exception as e:
                            summary['errors'].append(f"Failed to delete Lambda version {function_name}:{version}: {e}")
                    else:
                        total_versions_deleted += 1
                        
            except Exception as e:
                continue
        
        summary['resources_found']['lambda_versions'] = total_versions_deleted
        summary['resources_deleted']['lambda_versions'] = total_versions_deleted if not DRY_RUN else 0
        
    except Exception as e:
        summary['errors'].append(f"Lambda cleanup error: {e}")

def send_notification(summary: Dict):
    """Send cleanup summary via SNS"""
    if not SNS_TOPIC_ARN:
        return
    
    # Format message
    message = f"""
AWS Resource Cleanup Report
============================

Timestamp: {summary['timestamp']}
Mode: {'DRY RUN' if summary['dry_run'] else 'ACTIVE CLEANUP'}
Region: {REGION}

Resources Found:
----------------"""
    
    for resource_type, count in summary['resources_found'].items():
        if count > 0:
            message += f"\n{resource_type.replace('_', ' ').title()}: {count}"
    
    if not summary['dry_run']:
        message += "\n\nResources Deleted:\n------------------"
        for resource_type, count in summary['resources_deleted'].items():
            if count > 0:
                message += f"\n{resource_type.replace('_', ' ').title()}: {count}"
    
    message += f"""

Estimated Monthly Savings: ${summary['estimated_monthly_savings']:.2f}

"""
    
    if summary['errors']:
        message += "Errors:\n-------\n"
        for error in summary['errors'][:10]:  # Limit to 10 errors
            message += f"- {error}\n"
    
    if summary['dry_run']:
        message += """
Note: This was a DRY RUN. No resources were actually deleted.
To perform actual cleanup, set DRY_RUN environment variable to 'false'.
"""
    
    try:
        sns.publish(
            TopicArn=SNS_TOPIC_ARN,
            Subject=f"AWS Cleanup Report - {'DRY RUN' if summary['dry_run'] else 'COMPLETED'}",
            Message=message
        )
    except Exception as e:
        print(f"Failed to send SNS notification: {e}")