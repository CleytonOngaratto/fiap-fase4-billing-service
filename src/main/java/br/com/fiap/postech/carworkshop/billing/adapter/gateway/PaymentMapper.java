package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentStatus;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentJpaEntity;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(entity.id, entity.budgetId, entity.workOrderId, entity.amount,
                entity.refundIdempotencyKey, entity.paymentDeadline,
                PaymentStatus.valueOf(entity.status), entity.preferenceId, entity.paymentLink,
                entity.mpPaymentId, entity.refundId, entity.cancelReason, entity.createdAt,
                entity.updatedAt);
    }

    public static void copyInto(Payment payment, PaymentJpaEntity entity) {
        entity.id = payment.getId();
        entity.budgetId = payment.getBudgetId();
        entity.workOrderId = payment.getWorkOrderId();
        entity.amount = payment.getAmount();
        entity.status = payment.getStatus().name();
        entity.preferenceId = payment.getPreferenceId();
        entity.paymentLink = payment.getPaymentLink();
        entity.mpPaymentId = payment.getMpPaymentId();
        entity.refundId = payment.getRefundId();
        entity.refundIdempotencyKey = payment.getRefundIdempotencyKey();
        entity.paymentDeadline = payment.getPaymentDeadline();
        entity.cancelReason = payment.getCancelReason();
        entity.createdAt = payment.getCreatedAt();
        entity.updatedAt = payment.getUpdatedAt();
    }
}
