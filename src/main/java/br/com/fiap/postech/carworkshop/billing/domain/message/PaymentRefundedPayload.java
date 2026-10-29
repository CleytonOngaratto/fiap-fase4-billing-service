package br.com.fiap.postech.carworkshop.billing.domain.message;

public record PaymentRefundedPayload(String paymentId, String refundId) implements EventPayload {
}
