variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}

variable "vpc_name" {
  description = "Name tag for the VPC"
  type        = string
}

variable "subnet_cidr" {
  description = "List of CIDR blocks for subnets"
  type        = string
}

variable "availability_zones" {
  description = "List of availability zones for subnets"
  type        = string
}

variable "map_public_ip_on_launch" {
  description = "Whether to map public IP on launch for instances in the subnet"
  type        = bool
}