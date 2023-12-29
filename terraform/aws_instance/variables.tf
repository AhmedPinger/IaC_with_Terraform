variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "ami_id" {
  description = "AMI ID for the EC2 instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
}

variable "key_name" {
  description = "Key pair name for the EC2 instance"
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID for the EC2 instance"
  type = string
}

variable "instance_name" {
  description = "Name tag for the EC2 instance"
  type        = string
}

variable "vpc_security_group_ids" {
  description = "vpc securiy group id for port 22, 3000"
  
}

