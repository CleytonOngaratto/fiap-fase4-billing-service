data "aws_ssm_parameter" "vpc_id" {
  name = "/fase4/vpc/id"
}

data "aws_ssm_parameter" "private_subnets" {
  name = "/fase4/vpc/private-subnets"
}

data "aws_ssm_parameter" "eks_node_sg_id" {
  name = "/fase4/eks/node-sg-id"
}

data "aws_ssm_parameter" "alb_listener_arn" {
  name = "/fase4/alb/listener-arn"
}
