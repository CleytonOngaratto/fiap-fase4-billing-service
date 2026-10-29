package br.com.fiap.postech.carworkshop.billing.domain.message;

public record PaymentRequestedPayload(String budgetId, String paymentLink,
                                     String preferenceId) implements EventPayload {
}
