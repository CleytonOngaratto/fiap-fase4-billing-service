locals {
  service       = "billing"
  path_prefix   = "/billing/v1"
  rule_priority = 200

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-billing-service"
  }

  vpc_id       = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  listener_arn = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)
}
