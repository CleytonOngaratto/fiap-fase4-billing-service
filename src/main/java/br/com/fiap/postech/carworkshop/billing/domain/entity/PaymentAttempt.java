package br.com.fiap.postech.carworkshop.billing.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttempt(UUID paymentId, String mpPaymentId, String statusDetail,
                             Instant occurredAt) {
}
