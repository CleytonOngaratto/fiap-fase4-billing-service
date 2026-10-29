package br.com.fiap.postech.carworkshop.billing.domain.message;

public record PaymentCanceledPayload(String budgetId) implements EventPayload {
}
