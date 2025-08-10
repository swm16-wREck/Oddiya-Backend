output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "IDs of public subnets"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of private subnets"
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  description = "IDs of database subnets"
  value       = aws_subnet.database[*].id
}

output "nat_gateway_ids" {
  description = "IDs of NAT gateways"
  value       = aws_nat_gateway.main[*].id
}

output "cost_optimizations" {
  description = "Network cost optimizations applied"
  value = {
    single_nat_gateway    = var.use_single_nat_gateway
    vpc_endpoints_enabled = var.enable_vpc_endpoints
    flow_logs_enabled     = var.enable_flow_logs
    nat_gateway_count     = length(aws_nat_gateway.main)
  }
}

output "monthly_savings_estimate" {
  description = "Estimated monthly savings from network optimizations"
  value = {
    nat_gateway_savings = var.use_single_nat_gateway ? "$90/month per avoided NAT gateway" : "Using multiple NAT gateways"
    vpc_endpoint_savings = var.enable_vpc_endpoints ? "Reduced data transfer charges" : "Standard data transfer rates apply"
  }
}