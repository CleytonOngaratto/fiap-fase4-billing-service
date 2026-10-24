resource "aws_sqs_queue" "commands_dlq" {
  name                        = local.commands_dlq_name
  fifo_queue                  = true
  content_based_deduplication = false
  message_retention_seconds   = local.dlq_retention_seconds

  tags = { Name = local.commands_dlq_name }
}

# Sem queue policy: quem escreve sao os pods do OS com a LabRole, na mesma conta, por IAM. A politica de
# recurso so foi necessaria na fila de respostas do OS, onde o remetente e o SNS.
resource "aws_sqs_queue" "commands" {
  name                        = local.commands_queue_name
  fifo_queue                  = true
  content_based_deduplication = false
  visibility_timeout_seconds  = local.visibility_timeout_seconds
  receive_wait_time_seconds   = local.receive_wait_time_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.commands_dlq.arn
    maxReceiveCount     = local.max_receive_count
  })

  tags = { Name = local.commands_queue_name }
}
