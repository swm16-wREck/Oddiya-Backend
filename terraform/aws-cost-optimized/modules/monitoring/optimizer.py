#!/usr/bin/env python3
"""
Cost optimization analyzer for AWS resources
Provides weekly recommendations for cost savings
"""

import json
import os
import boto3
from datetime import datetime, timedelta

# Initialize AWS clients
ce = boto3.client('ce')
compute_optimizer = boto3.client('compute-optimizer')
sns = boto3.client('sns')

SNS_TOPIC_ARN = os.environ.get('SNS_TOPIC_ARN')
REGION = os.environ.get('REGION', 'ap-northeast-2')

def handler(event, context):
    """Lambda handler for cost optimization analysis"""
    
    recommendations = {
        'timestamp': datetime.utcnow().isoformat(),
        'savings_opportunities': [],
        'total_potential_savings': 0
    }
    
    # Get cost and usage data
    end_date = datetime.now().date()
    start_date = end_date - timedelta(days=30)
    
    try:
        # Get current month's cost
        cost_response = ce.get_cost_and_usage(
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
        
        # Analyze rightsizing recommendations
        rightsizing = get_rightsizing_recommendations()
        if rightsizing:
            recommendations['savings_opportunities'].append(rightsizing)
            recommendations['total_potential_savings'] += rightsizing['monthly_savings']
        
        # Check for unused resources
        unused = check_unused_resources()
        if unused:
            recommendations['savings_opportunities'].append(unused)
            recommendations['total_potential_savings'] += unused['monthly_savings']
        
        # Analyze Reserved Instance utilization
        ri_recommendations = get_ri_recommendations()
        if ri_recommendations:
            recommendations['savings_opportunities'].append(ri_recommendations)
            recommendations['total_potential_savings'] += ri_recommendations['monthly_savings']
        
        # Analyze Savings Plans opportunities
        sp_recommendations = get_savings_plans_recommendations()
        if sp_recommendations:
            recommendations['savings_opportunities'].append(sp_recommendations)
            recommendations['total_potential_savings'] += sp_recommendations['monthly_savings']
        
        # Send recommendations via SNS
        send_recommendations(recommendations)
        
    except Exception as e:
        print(f"Error analyzing costs: {e}")
        recommendations['error'] = str(e)
    
    return recommendations

def get_rightsizing_recommendations():
    """Get EC2 rightsizing recommendations"""
    try:
        response = ce.get_rightsizing_recommendation(
            Service='EC2',
            Configuration={
                'BenefitsConsidered': True,
                'RecommendationTarget': 'SAME_INSTANCE_FAMILY'
            }
        )
        
        total_savings = 0
        instances_to_rightsize = []
        
        for rec in response.get('RightsizingRecommendations', []):
            if rec['RightsizingType'] == 'Modify':
                savings = float(rec.get('EstimatedMonthlySavingsAmount', 0))
                total_savings += savings
                instances_to_rightsize.append({
                    'instance_id': rec['ResourceDetails']['EC2ResourceDetails']['InstanceId'],
                    'current_type': rec['ResourceDetails']['EC2ResourceDetails']['InstanceType'],
                    'recommended_type': rec['ModifyRecommendationDetail']['TargetInstances'][0]['InstanceType'],
                    'monthly_savings': savings
                })
        
        if instances_to_rightsize:
            return {
                'type': 'EC2 Rightsizing',
                'description': f'Rightsize {len(instances_to_rightsize)} EC2 instances',
                'monthly_savings': total_savings,
                'details': instances_to_rightsize[:5]  # Top 5 recommendations
            }
    except Exception as e:
        print(f"Error getting rightsizing recommendations: {e}")
    
    return None

def check_unused_resources():
    """Check for unused or underutilized resources"""
    unused_resources = {
        'type': 'Unused Resources',
        'description': 'Resources that can be terminated',
        'monthly_savings': 0,
        'details': []
    }
    
    try:
        # Check for unattached EBS volumes
        ec2 = boto3.client('ec2', region_name=REGION)
        volumes = ec2.describe_volumes(
            Filters=[
                {'Name': 'status', 'Values': ['available']}
            ]
        )
        
        for volume in volumes.get('Volumes', []):
            size_gb = volume['Size']
            # Estimate cost (roughly $0.10 per GB for gp3)
            monthly_cost = size_gb * 0.10
            unused_resources['monthly_savings'] += monthly_cost
            unused_resources['details'].append({
                'resource': 'EBS Volume',
                'id': volume['VolumeId'],
                'size': f'{size_gb} GB',
                'monthly_cost': monthly_cost
            })
        
        # Check for unused Elastic IPs
        eips = ec2.describe_addresses()
        for eip in eips.get('Addresses', []):
            if 'InstanceId' not in eip:
                # Unattached EIP costs ~$3.60/month
                monthly_cost = 3.60
                unused_resources['monthly_savings'] += monthly_cost
                unused_resources['details'].append({
                    'resource': 'Elastic IP',
                    'id': eip.get('AllocationId', eip.get('PublicIp')),
                    'monthly_cost': monthly_cost
                })
        
    except Exception as e:
        print(f"Error checking unused resources: {e}")
    
    return unused_resources if unused_resources['monthly_savings'] > 0 else None

def get_ri_recommendations():
    """Get Reserved Instance purchase recommendations"""
    try:
        response = ce.get_reservation_purchase_recommendation(
            Service='EC2',
            PaymentOption='PARTIAL_UPFRONT',
            TermInYears='ONE_YEAR',
            LookbackPeriodInDays='THIRTY_DAYS'
        )
        
        if response.get('Recommendations'):
            rec = response['Recommendations'][0]
            return {
                'type': 'Reserved Instances',
                'description': 'Purchase Reserved Instances for consistent workloads',
                'monthly_savings': float(rec.get('EstimatedMonthlySavingsAmount', 0)),
                'details': {
                    'instance_family': rec['InstanceDetails']['InstanceFamily'],
                    'recommended_count': rec['RecommendedInstanceCount'],
                    'upfront_cost': rec['UpfrontCost']
                }
            }
    except Exception as e:
        print(f"Error getting RI recommendations: {e}")
    
    return None

def get_savings_plans_recommendations():
    """Get Savings Plans purchase recommendations"""
    try:
        response = ce.get_savings_plans_purchase_recommendation(
            SavingsPlansType='COMPUTE_SP',
            TermInYears='ONE_YEAR',
            PaymentOption='PARTIAL_UPFRONT',
            LookbackPeriodInDays='THIRTY_DAYS'
        )
        
        if response.get('SavingsPlansPurchaseRecommendation'):
            rec = response['SavingsPlansPurchaseRecommendation']
            return {
                'type': 'Savings Plans',
                'description': 'Purchase Compute Savings Plans for flexible compute usage',
                'monthly_savings': float(rec.get('EstimatedMonthlySavingsAmount', 0)),
                'details': {
                    'hourly_commitment': rec.get('HourlyCommitmentToPurchase'),
                    'upfront_cost': rec.get('UpfrontCost'),
                    'savings_percentage': rec.get('EstimatedSavingsPercentage')
                }
            }
    except Exception as e:
        print(f"Error getting Savings Plans recommendations: {e}")
    
    return None

def send_recommendations(recommendations):
    """Send recommendations via SNS"""
    if not SNS_TOPIC_ARN:
        print("No SNS topic configured")
        return
    
    # Format message
    message = f"""
    Weekly Cost Optimization Report
    ================================
    
    Date: {recommendations['timestamp']}
    Total Potential Monthly Savings: ${recommendations['total_potential_savings']:.2f}
    
    Savings Opportunities:
    ----------------------
    """
    
    for opportunity in recommendations['savings_opportunities']:
        message += f"\n{opportunity['type']}: ${opportunity['monthly_savings']:.2f}/month"
        message += f"\n  {opportunity['description']}"
        if isinstance(opportunity.get('details'), list):
            for detail in opportunity['details'][:3]:  # Top 3 items
                message += f"\n  - {detail}"
        message += "\n"
    
    message += """
    
    Action Required:
    ---------------
    Review these recommendations and implement where appropriate.
    For detailed analysis, check the AWS Cost Explorer console.
    """
    
    try:
        sns.publish(
            TopicArn=SNS_TOPIC_ARN,
            Subject='AWS Cost Optimization Report',
            Message=message
        )
        print("Recommendations sent successfully")
    except Exception as e:
        print(f"Error sending recommendations: {e}")