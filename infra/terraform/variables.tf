variable "aws_region" {
  description = "AWS region to deploy resources into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment prefix used to name resources (e.g. dev, staging, prod)."
  type        = string
  default     = "local"
}

variable "ssh_public_key_path" {
  description = "Path to the local SSH public key uploaded to EC2 as a key pair."
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ami_id" {
  description = "EC2 AMI ID."
  type    = string
  default = "ami-0521cb2d60cfbb1a6"
}
