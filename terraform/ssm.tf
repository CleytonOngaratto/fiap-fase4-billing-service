resource "aws_ssm_parameter" "target_group_arn" {
  name  = "/fase4/${local.service}/alb/target-group-arn"
  type  = "String"
  value = aws_lb_target_group.service.arn
}

resource "aws_ssm_parameter" "commands_queue_url" {
  name  = "/fase4/${local.service}/sqs/commands-queue-url"
  type  = "String"
  value = aws_sqs_queue.commands.url
}

# `.address`, não `.endpoint`: o endpoint já vem "host:5432" e as URLs dos consumidores sairiam "host:5432:5432".
resource "aws_ssm_parameter" "rds_endpoint" {
  name  = "/fase4/${local.service}/rds/endpoint"
  type  = "String"
  value = aws_db_instance.main.address
}

resource "aws_ssm_parameter" "rds_port" {
  name  = "/fase4/${local.service}/rds/port"
  type  = "String"
  value = tostring(aws_db_instance.main.port)
}

resource "aws_ssm_parameter" "rds_db_name" {
  name  = "/fase4/${local.service}/rds/db-name"
  type  = "String"
  value = aws_db_instance.main.db_name
}

resource "aws_ssm_parameter" "rds_username" {
  name  = "/fase4/${local.service}/rds/username"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "rds_password" {
  name  = "/fase4/${local.service}/rds/password"
  type  = "SecureString"
  value = random_password.master.result
}
