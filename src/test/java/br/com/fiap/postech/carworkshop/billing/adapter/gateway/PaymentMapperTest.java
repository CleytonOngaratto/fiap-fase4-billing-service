package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentStatus;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentJpaEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMapperTest {

    @Test
    void copiesEveryFieldBothWays() {
        Payment original = Payment.restore(UUID.randomUUID(), UUID.randomUUID(), 42L,
                new BigDecimal("257.70"), UUID.randomUUID(), Instant.parse("2026-10-25T12:30:00Z"),
                PaymentStatus.REFUNDED, "pref-1", "https://mp/checkout", "mp-1", "refund-1",
                "REPAIR_FAILED", Instant.parse("2026-10-25T10:00:00Z"),
                Instant.parse("2026-10-25T11:00:00Z"));

        PaymentJpaEntity entity = new PaymentJpaEntity();
        PaymentMapper.copyInto(original, entity);
        Payment restored = PaymentMapper.toDomain(entity);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getBudgetId(), restored.getBudgetId());
        assertEquals(original.getWorkOrderId(), restored.getWorkOrderId());
        assertEquals(original.getAmount(), restored.getAmount());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getPreferenceId(), restored.getPreferenceId());
        assertEquals(original.getPaymentLink(), restored.getPaymentLink());
        assertEquals(original.getMpPaymentId(), restored.getMpPaymentId());
        assertEquals(original.getRefundId(), restored.getRefundId());
        assertEquals(original.getRefundIdempotencyKey(), restored.getRefundIdempotencyKey());
        assertEquals(original.getPaymentDeadline(), restored.getPaymentDeadline());
        assertEquals(original.getCancelReason(), restored.getCancelReason());
        assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        assertEquals(original.getUpdatedAt(), restored.getUpdatedAt());
    }
}
