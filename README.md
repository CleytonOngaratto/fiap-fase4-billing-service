# fiap-fase4-billing-service

Billing Service da Fase 4 do Tech Challenge (FIAP SOAT): orçamento, aprovação pelo cliente e pagamento com
Mercado Pago. Quarkus.

> Em construção: por enquanto só o `GET /billing/v1/info`. Arquitetura em
> [fiap-fase4-os-service/docs/arquitetura](https://github.com/CleytonOngaratto/fiap-fase4-os-service/tree/main/docs/arquitetura).

## Deploy

| Workflow | Gatilho | O que faz |
|---|---|---|
| [`maven-ci.yml`](.github/workflows/maven-ci.yml) | PR e `push` na `main` | build, testes, JaCoCo, análise no SonarCloud (`build` e `SonarCloud Code Analysis` são checks obrigatórios) e Trivy |
| [`terraform.yml`](.github/workflows/terraform.yml) | PR → `fmt-validate` (check obrigatório) + `plan` · `push` na `main` com mudança em `terraform/` → `apply` · `workflow_dispatch` → `plan`/`apply`/`destroy` | a infraestrutura do serviço, em [`terraform/`](terraform/): target group `ip`, a regra `/billing/v1/*` no ALB interno da plataforma e a **fila de comandos** que o orquestrador usa para falar com este serviço |
| [`cd.yml`](.github/workflows/cd.yml) | `push` na `main` (fora `*.md`, `docs/` e `terraform/`) e `workflow_dispatch` | testes → imagem no ECR (tag = SHA do commit) → manifestos no EKS → smoke: alvos `healthy` no target group e, com a borda de pé, `GET /billing/v1/info` → **200** pelo API Gateway |

**O merge aplica.** Um merge que mude `terraform/` cria ou altera recursos na AWS — só com a sessão do laboratório
ligada e com o teardown na mesma sessão. Num merge com código e `terraform/` os dois workflows rodam juntos: o
`cd.yml` espera o target group no SSM (até 25 min) e falha dizendo quem publica.

## Contrato no SSM

| Lê | De quem |
|---|---|
| `/fase4/vpc/id`, `/fase4/alb/listener-arn` | plataforma ([fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)), no `terraform/` |
| `/fase4/ecr/billing/repo-url`, `/fase4/eks/cluster-name`, `/fase4/alb/sg-id` | plataforma, no `cd.yml` |
| `/fase4/newrelic/license-key` | bootstrap manual (sobrevive ao teardown) |

| Publica | Conteúdo |
|---|---|
| `/fase4/billing/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |
| `/fase4/billing/sqs/commands-queue-url` | **fila de comandos deste serviço**, lida pelo `cd.yml` do [fiap-fase4-os-service](https://github.com/CleytonOngaratto/fiap-fase4-os-service): é para lá que o orquestrador da Saga envia `CreateBudget`, `RequestPayment`, `CancelBudget`, `CancelPayment` e `RefundPayment` |

⚠️ **A fila existe antes do consumidor.** Ela é provisionada aqui porque a fila pertence a quem a consome, e o
OS Service não sobe sem conhecer a URL dela. O código que consome os comandos entra no bloco de domínio deste
serviço; até então a fila fica vazia, com a DLQ configurada. Sem política de recurso: quem escreve são os pods
do OS, na mesma conta, autorizados por IAM.

| Publica | Conteúdo |
|---|---|
| `/fase4/billing/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |

## Kubernetes

Tudo no namespace **`billing`** ([`k8s/application/`](k8s/application/)): ServiceAccount própria sem token montado,
ConfigMap, o Secret do New Relic gerado pelo `cd.yml` (só neste namespace), Deployment com HPA, Service `ClusterIP` e
o `TargetGroupBinding` que registra os pods no target group do ALB interno.

O namespace tem a label `elbv2.k8s.aws/pod-readiness-gate-inject: enabled`: o pod só fica pronto quando o alvo dele
está `healthy` no target group, e o `preStop` segura o encerramento enquanto o ALB drena o alvo.

A entrada é sempre pelo API Gateway; `/q/*` (health, métricas, OpenAPI) só dentro da VPC.
