package br.com.fiap.postech.carworkshop.billing.domain.message;

public record RefundPaymentCommand(SagaEnvelope envelope, String paymentId, String reason) {
}
