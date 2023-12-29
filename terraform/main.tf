provider "aws" {
    region = "eu-north-1"
  
}

module "ec2_instance" {
  source          = "./aws_instance"
  aws_region      = "eu-north-1"
  ami_id          = "ami-0506d6d51f1916a96"
  instance_type   = "t3.micro"
  key_name        = "project_demo"
  subnet_id       = module.vpc.subnet_ids[0]
  instance_name   = "web_server"
  vpc_security_group_ids = module.ec2_security_group.security_group_id
}

module "vpc" {
  source                  = "./aws_vpc"
  aws_region              = "eu-north-1"
  vpc_cidr                = "10.0.0.0/16"
  vpc_name                = "webserver_vpc"
  subnet_cidr             = "10.0.1.0/24"
  availability_zones      = "eu-north-1a"
  map_public_ip_on_launch = true
}

module "ec2_security_group" {
  source  = "./ec2_security_group"  
  aws_region = "eu-north-1"
  vpc_id = module.vpc.vpc_id
}


output "public_ip" {
  description = "Public IP address of the EC2 instance"
  value       = module.ec2_instance.public_ip
}