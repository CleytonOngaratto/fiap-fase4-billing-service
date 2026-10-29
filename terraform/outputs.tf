output "target_group_arn" {
  description = "Target group dos pods, ligado a eles pelo TargetGroupBinding."
  value       = aws_lb_target_group.service.arn
}

output "listener_rule_path" {
  description = "Caminho que a regra do ALB interno encaminha para este serviço."
  value       = "${local.path_prefix}/*"
}

output "db_identifier" {
  description = "Identificador da instância RDS."
  value       = aws_db_instance.main.identifier
}

output "db_endpoint" {
  description = "Hostname da instância, sem a porta."
  value       = aws_db_instance.main.address
}

output "password_ssm_parameter" {
  description = "Onde buscar a senha: aws ssm get-parameter --name <isto> --with-decryption"
  value       = aws_ssm_parameter.rds_password.name
}
