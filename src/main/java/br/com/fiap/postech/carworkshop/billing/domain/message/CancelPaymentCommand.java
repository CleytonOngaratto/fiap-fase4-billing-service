package br.com.fiap.postech.carworkshop.billing.domain.message;

public record CancelPaymentCommand(SagaEnvelope envelope, String budgetId, String reason) {
}
