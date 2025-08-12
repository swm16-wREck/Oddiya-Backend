#!/bin/bash
# Setup CloudWatch Alarms for Oddiya Production Monitoring

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Initialize script
init_script "setup-cloudwatch-alarms.sh" true

# Configuration
EMAIL="${1:-team@oddiya.com}"  # Can override with command line argument

print_header "CLOUDWATCH ALARMS SETUP"

# Function to check if SNS topic exists
check_sns_topic() {
    local topic_arn=$(aws sns list-topics \
        --region $REGION \
        --query "Topics[?contains(TopicArn, '$SNS_TOPIC')].TopicArn | [0]" \
        --output text 2>/dev/null || echo "")
    
    if [ -z "$topic_arn" ] || [ "$topic_arn" = "None" ]; then
        echo ""
    else
        echo "$topic_arn"
    fi
}

# Create or get SNS topic
print_section "Setting up SNS Topic"
SNS_TOPIC_ARN=$(check_sns_topic)

if [ -z "$SNS_TOPIC_ARN" ]; then
    log_info "Creating SNS topic: $SNS_TOPIC"
    SNS_TOPIC_ARN=$(aws sns create-topic \
        --name $SNS_TOPIC \
        --region $REGION \
        --query 'TopicArn' \
        --output text)
    log_success "SNS topic created: $SNS_TOPIC_ARN"
else
    log_success "Using existing SNS topic: $SNS_TOPIC_ARN"
fi

# Subscribe email to SNS topic
print_section "Setting up Email Notifications"
SUBSCRIPTION_EXISTS=$(aws sns list-subscriptions-by-topic \
    --topic-arn $SNS_TOPIC_ARN \
    --region $REGION \
    --query "Subscriptions[?Endpoint=='$EMAIL'].SubscriptionArn | [0]" \
    --output text 2>/dev/null || echo "")

if [ -z "$SUBSCRIPTION_EXISTS" ] || [ "$SUBSCRIPTION_EXISTS" = "None" ]; then
    log_info "Subscribing $EMAIL to alerts..."
    aws sns subscribe \
        --topic-arn $SNS_TOPIC_ARN \
        --protocol email \
        --notification-endpoint $EMAIL \
        --region $REGION
    log_warning "Please check your email and confirm the subscription"
else
    log_success "Email already subscribed: $EMAIL"
fi

# Function to create or update alarm
create_alarm() {
    local alarm_name=$1
    local description=$2
    local namespace=$3
    local metric_name=$4
    local dimensions=$5
    local statistic=$6
    local period=$7
    local threshold=$8
    local comparison=$9
    local evaluation_periods=${10}
    
    log_info "Creating alarm: $alarm_name"
    
    if aws cloudwatch put-metric-alarm \
        --alarm-name "$alarm_name" \
        --alarm-description "$description" \
        --actions-enabled \
        --alarm-actions $SNS_TOPIC_ARN \
        --metric-name "$metric_name" \
        --namespace "$namespace" \
        --statistic "$statistic" \
        --dimensions $dimensions \
        --period $period \
        --threshold $threshold \
        --comparison-operator $comparison \
        --evaluation-periods $evaluation_periods \
        --region $REGION \
        2>/dev/null; then
        log_success "Alarm configured: $alarm_name"
    else
        log_warning "Failed to create alarm: $alarm_name"
        return 1
    fi
}

print_section "Creating CloudWatch Alarms"

# 1. ECS Service - No Running Tasks
create_alarm \
    "Oddiya-ECS-No-Running-Tasks" \
    "Alert when ECS service has no running tasks" \
    "AWS/ECS" \
    "CPUUtilization" \
    "Name=ServiceName,Value=oddiya-backend-service Name=ClusterName,Value=oddiya-cluster" \
    "SampleCount" \
    "60" \
    "1" \
    "LessThanThreshold" \
    "2"

# 2. ECS High CPU Utilization
create_alarm \
    "Oddiya-ECS-High-CPU" \
    "Alert when ECS CPU utilization exceeds 80%" \
    "AWS/ECS" \
    "CPUUtilization" \
    "Name=ServiceName,Value=oddiya-backend-service Name=ClusterName,Value=oddiya-cluster" \
    "Average" \
    "300" \
    "80" \
    "GreaterThanThreshold" \
    "2"

# 3. ECS High Memory Utilization
create_alarm \
    "Oddiya-ECS-High-Memory" \
    "Alert when ECS memory utilization exceeds 85%" \
    "AWS/ECS" \
    "MemoryUtilization" \
    "Name=ServiceName,Value=oddiya-backend-service Name=ClusterName,Value=oddiya-cluster" \
    "Average" \
    "300" \
    "85" \
    "GreaterThanThreshold" \
    "2"

# 4. ALB Target Response Time
create_alarm \
    "Oddiya-ALB-High-Response-Time" \
    "Alert when response time exceeds 2 seconds" \
    "AWS/ApplicationELB" \
    "TargetResponseTime" \
    "Name=LoadBalancer,Value=app/oddiya-alb/*" \
    "Average" \
    "300" \
    "2.0" \
    "GreaterThanThreshold" \
    "2"

# 5. ALB 5XX Errors
create_alarm \
    "Oddiya-ALB-5XX-Errors" \
    "Alert on high 5XX error rate" \
    "AWS/ApplicationELB" \
    "HTTPCode_Target_5XX_Count" \
    "Name=LoadBalancer,Value=app/oddiya-alb/*" \
    "Sum" \
    "60" \
    "10" \
    "GreaterThanThreshold" \
    "1"

# 6. ALB Unhealthy Hosts
create_alarm \
    "Oddiya-ALB-Unhealthy-Targets" \
    "Alert when targets become unhealthy" \
    "AWS/ApplicationELB" \
    "UnHealthyHostCount" \
    "Name=TargetGroup,Value=targetgroup/oddiya-targets/*" \
    "Average" \
    "60" \
    "0" \
    "GreaterThanThreshold" \
    "2"

# 7. RDS CPU Utilization
create_alarm \
    "Oddiya-RDS-High-CPU" \
    "Alert when RDS CPU exceeds 75%" \
    "AWS/RDS" \
    "CPUUtilization" \
    "Name=DBInstanceIdentifier,Value=oddiya-db" \
    "Average" \
    "300" \
    "75" \
    "GreaterThanThreshold" \
    "2"

# 8. RDS Database Connections
create_alarm \
    "Oddiya-RDS-Connection-Limit" \
    "Alert when database connections near limit" \
    "AWS/RDS" \
    "DatabaseConnections" \
    "Name=DBInstanceIdentifier,Value=oddiya-db" \
    "Average" \
    "300" \
    "45" \
    "GreaterThanThreshold" \
    "2"

# 9. RDS Free Storage Space
create_alarm \
    "Oddiya-RDS-Low-Storage" \
    "Alert when free storage drops below 1GB" \
    "AWS/RDS" \
    "FreeStorageSpace" \
    "Name=DBInstanceIdentifier,Value=oddiya-db" \
    "Average" \
    "300" \
    "1073741824" \
    "LessThanThreshold" \
    "1"

# 10. RDS Read Latency
create_alarm \
    "Oddiya-RDS-High-Read-Latency" \
    "Alert when read latency exceeds 200ms" \
    "AWS/RDS" \
    "ReadLatency" \
    "Name=DBInstanceIdentifier,Value=oddiya-db" \
    "Average" \
    "300" \
    "0.2" \
    "GreaterThanThreshold" \
    "2"

# Create composite alarm for service health
print_section "Creating Composite Alarm"

if aws cloudwatch put-composite-alarm \
    --alarm-name "Oddiya-Service-Critical" \
    --alarm-description "Critical: Multiple service components are failing" \
    --actions-enabled \
    --alarm-actions $SNS_TOPIC_ARN \
    --alarm-rule "(ALARM('Oddiya-ECS-No-Running-Tasks') OR ALARM('Oddiya-ALB-Unhealthy-Targets')) AND ALARM('Oddiya-ALB-5XX-Errors')" \
    --region $REGION \
    2>/dev/null; then
    log_success "Composite alarm configured"
else
    log_warning "Composite alarm may already exist"
fi

# Summary
print_header "SETUP COMPLETE"
log_success "CloudWatch Alarms Setup Complete!"

print_section "Configured Alarms"
aws cloudwatch describe-alarms \
    --alarm-name-prefix "Oddiya-" \
    --region $REGION \
    --query 'MetricAlarms[].{Name:AlarmName,State:StateValue}' \
    --output table

print_section "Next Steps"
echo -e "1. ${YELLOW}Confirm email subscription${NC} at $EMAIL"
echo -e "2. Test alarms: ${CYAN}aws cloudwatch set-alarm-state --alarm-name Oddiya-ECS-High-CPU --state-value ALARM --state-reason 'Testing'${NC}"
echo -e "3. View alarms: ${CYAN}https://console.aws.amazon.com/cloudwatch/home?region=$REGION#alarmsV2:${NC}"
echo -e "4. Monitor dashboard: ${CYAN}./scripts/monitor-aws-production.sh${NC}"

log_success "Monitoring is now active!"