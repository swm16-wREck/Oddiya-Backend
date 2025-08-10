#!/usr/bin/env python3
"""
Cost-optimized resource scheduler for AWS
Automatically starts and stops resources based on schedule
"""

import json
import os
import boto3
from datetime import datetime

# Initialize AWS clients
ec2 = boto3.client('ec2')
rds = boto3.client('rds')
autoscaling = boto3.client('autoscaling')

def handler(event, context):
    """Lambda handler for resource scheduling"""
    
    # Get action from event
    action = event.get('action', 'stop').lower()
    
    # Get target resources from environment
    ec2_instances = json.loads(os.environ.get('EC2_INSTANCES', '[]'))
    rds_instances = json.loads(os.environ.get('RDS_INSTANCES', '[]'))
    asg_names = json.loads(os.environ.get('ASG_NAMES', '[]'))
    
    results = {
        'timestamp': datetime.utcnow().isoformat(),
        'action': action,
        'results': {
            'ec2': [],
            'rds': [],
            'asg': []
        }
    }
    
    # Handle EC2 instances
    if ec2_instances:
        results['results']['ec2'] = handle_ec2_instances(ec2_instances, action)
    
    # Handle RDS instances
    if rds_instances:
        results['results']['rds'] = handle_rds_instances(rds_instances, action)
    
    # Handle Auto Scaling Groups
    if asg_names:
        results['results']['asg'] = handle_auto_scaling_groups(asg_names, action)
    
    print(json.dumps(results))
    return results

def handle_ec2_instances(instance_ids, action):
    """Start or stop EC2 instances"""
    results = []
    
    if not instance_ids:
        return results
    
    try:
        if action == 'start':
            response = ec2.start_instances(InstanceIds=instance_ids)
            for instance in response.get('StartingInstances', []):
                results.append({
                    'instance_id': instance['InstanceId'],
                    'previous_state': instance['PreviousState']['Name'],
                    'current_state': instance['CurrentState']['Name']
                })
        else:  # stop
            response = ec2.stop_instances(InstanceIds=instance_ids)
            for instance in response.get('StoppingInstances', []):
                results.append({
                    'instance_id': instance['InstanceId'],
                    'previous_state': instance['PreviousState']['Name'],
                    'current_state': instance['CurrentState']['Name']
                })
    except Exception as e:
        results.append({
            'error': str(e),
            'instances': instance_ids
        })
    
    return results

def handle_rds_instances(db_identifiers, action):
    """Start or stop RDS instances and clusters"""
    results = []
    
    for db_id in db_identifiers:
        try:
            # Check if it's a cluster or instance
            is_cluster = False
            try:
                rds.describe_db_clusters(DBClusterIdentifier=db_id)
                is_cluster = True
            except rds.exceptions.DBClusterNotFoundFault:
                pass
            
            if is_cluster:
                if action == 'start':
                    response = rds.start_db_cluster(DBClusterIdentifier=db_id)
                    results.append({
                        'db_cluster': db_id,
                        'status': 'starting'
                    })
                else:
                    response = rds.stop_db_cluster(DBClusterIdentifier=db_id)
                    results.append({
                        'db_cluster': db_id,
                        'status': 'stopping'
                    })
            else:
                if action == 'start':
                    response = rds.start_db_instance(DBInstanceIdentifier=db_id)
                    results.append({
                        'db_instance': db_id,
                        'status': 'starting'
                    })
                else:
                    response = rds.stop_db_instance(DBInstanceIdentifier=db_id)
                    results.append({
                        'db_instance': db_id,
                        'status': 'stopping'
                    })
        except Exception as e:
            results.append({
                'error': str(e),
                'db_identifier': db_id
            })
    
    return results

def handle_auto_scaling_groups(asg_names, action):
    """Scale Auto Scaling Groups up or down"""
    results = []
    
    for asg_name in asg_names:
        try:
            # Get current ASG configuration
            response = autoscaling.describe_auto_scaling_groups(
                AutoScalingGroupNames=[asg_name]
            )
            
            if not response['AutoScalingGroups']:
                results.append({
                    'error': 'ASG not found',
                    'asg_name': asg_name
                })
                continue
            
            asg = response['AutoScalingGroups'][0]
            
            if action == 'start':
                # Restore to desired capacity (stored in tag)
                desired = 2  # Default desired capacity
                for tag in asg.get('Tags', []):
                    if tag['Key'] == 'ScheduledDesiredCapacity':
                        desired = int(tag['Value'])
                        break
                
                autoscaling.update_auto_scaling_group(
                    AutoScalingGroupName=asg_name,
                    MinSize=1,
                    DesiredCapacity=desired
                )
                results.append({
                    'asg_name': asg_name,
                    'action': 'started',
                    'desired_capacity': desired
                })
            else:  # stop
                # Save current desired capacity as tag
                current_desired = asg['DesiredCapacity']
                
                # Tag the ASG with current desired capacity for restoration
                autoscaling.create_or_update_tags(
                    Tags=[
                        {
                            'ResourceId': asg_name,
                            'ResourceType': 'auto-scaling-group',
                            'Key': 'ScheduledDesiredCapacity',
                            'Value': str(current_desired),
                            'PropagateAtLaunch': False
                        }
                    ]
                )
                
                # Scale down to 0
                autoscaling.update_auto_scaling_group(
                    AutoScalingGroupName=asg_name,
                    MinSize=0,
                    DesiredCapacity=0
                )
                results.append({
                    'asg_name': asg_name,
                    'action': 'stopped',
                    'previous_capacity': current_desired
                })
        except Exception as e:
            results.append({
                'error': str(e),
                'asg_name': asg_name
            })
    
    return results