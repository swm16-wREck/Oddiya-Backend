variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "lambda_security_group_id" {
  type = string
}

variable "s3_input_bucket" {
  type = string
}

variable "s3_output_bucket" {
  type = string
}
