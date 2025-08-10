variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "use_single_nat_gateway" {
  description = "Use single NAT gateway for all AZs to save costs"
  type        = bool
  default     = true
}

variable "enable_vpc_endpoints" {
  description = "Enable VPC endpoints to reduce data transfer costs"
  type        = bool
  default     = true
}

variable "enable_interface_endpoints" {
  description = "Enable interface endpoints (have hourly charges)"
  type        = bool
  default     = false
}

variable "enable_flow_logs" {
  description = "Enable VPC flow logs (can be expensive)"
  type        = bool
  default     = false
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}