locals {
  service       = "billing"
  path_prefix   = "/billing/v1"
  rule_priority = 200

  commands_queue_name = "billing-commands.fifo"
  commands_dlq_name   = "billing-commands-dlq.fifo"

  visibility_timeout_seconds = 30
  receive_wait_time_seconds  = 20
  max_receive_count          = 3
  dlq_retention_seconds      = 1209600

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-billing-service"
  }

  vpc_id       = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  listener_arn = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)
}
