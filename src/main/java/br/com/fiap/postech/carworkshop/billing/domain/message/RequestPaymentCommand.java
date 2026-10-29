package br.com.fiap.postech.carworkshop.billing.domain.message;

import java.math.BigDecimal;
import java.time.Instant;

public record RequestPaymentCommand(SagaEnvelope envelope, String budgetId, BigDecimal amount,
                                    Instant paymentDeadline) {
}
