package br.com.fiap.postech.carworkshop.billing.domain.message;

public record CancelBudgetCommand(SagaEnvelope envelope, String budgetId, String reason) {
}
