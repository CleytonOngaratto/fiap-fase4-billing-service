package br.com.fiap.postech.carworkshop.billing.domain.message;

import java.math.BigDecimal;

public record PaymentConfirmedPayload(String paymentId, BigDecimal amount) implements EventPayload {
}
