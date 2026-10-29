package br.com.fiap.postech.carworkshop.billing.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Payment {

    private final UUID id;
    private final UUID budgetId;
    private final Long workOrderId;
    private final BigDecimal amount;
    private final UUID refundIdempotencyKey;
    private final Instant paymentDeadline;
    private final Instant createdAt;
    private PaymentStatus status;
    private String preferenceId;
    private String paymentLink;
    private String mpPaymentId;
    private String refundId;
    private String cancelReason;
    private Instant updatedAt;

    private Payment(UUID id, UUID budgetId, Long workOrderId, BigDecimal amount,
                    UUID refundIdempotencyKey, Instant paymentDeadline, PaymentStatus status,
                    String preferenceId, String paymentLink, String mpPaymentId, String refundId,
                    String cancelReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.budgetId = budgetId;
        this.workOrderId = workOrderId;
        this.amount = amount;
        this.refundIdempotencyKey = refundIdempotencyKey;
        this.paymentDeadline = paymentDeadline;
        this.status = status;
        this.preferenceId = preferenceId;
        this.paymentLink = paymentLink;
        this.mpPaymentId = mpPaymentId;
        this.refundId = refundId;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(UUID budgetId, Long workOrderId, BigDecimal amount,
                                 Instant paymentDeadline, Instant now) {
        return new Payment(UUID.randomUUID(), budgetId, workOrderId, amount, UUID.randomUUID(),
                paymentDeadline, PaymentStatus.PENDING, null, null, null, null, null, now, now);
    }

    public static Payment restore(UUID id, UUID budgetId, Long workOrderId, BigDecimal amount,
                                  UUID refundIdempotencyKey, Instant paymentDeadline,
                                  PaymentStatus status, String preferenceId, String paymentLink,
                                  String mpPaymentId, String refundId, String cancelReason,
                                  Instant createdAt, Instant updatedAt) {
        return new Payment(id, budgetId, workOrderId, amount, refundIdempotencyKey, paymentDeadline,
                status, preferenceId, paymentLink, mpPaymentId, refundId, cancelReason, createdAt,
                updatedAt);
    }

    public void linkCreated(String preferenceId, String paymentLink, Instant now) {
        this.preferenceId = preferenceId;
        this.paymentLink = paymentLink;
        this.updatedAt = now;
    }

    public boolean confirm(String mpPaymentId, Instant now) {
        if (status != PaymentStatus.PENDING) {
            return false;
        }
        this.status = PaymentStatus.CONFIRMED;
        this.mpPaymentId = mpPaymentId;
        this.updatedAt = now;
        return true;
    }

    public boolean cancel(String reason, Instant now) {
        if (status == PaymentStatus.CANCELED || status == PaymentStatus.REFUNDED) {
            return false;
        }
        this.status = PaymentStatus.CANCELED;
        this.cancelReason = reason;
        this.updatedAt = now;
        return true;
    }

    public boolean refund(String refundId, String reason, Instant now) {
        if (status == PaymentStatus.REFUNDED) {
            return false;
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundId = refundId;
        this.cancelReason = reason;
        this.updatedAt = now;
        return true;
    }

    public void rememberAttempt(String mpPaymentId, Instant now) {
        this.mpPaymentId = mpPaymentId;
        this.updatedAt = now;
    }

    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getRefundIdempotencyKey() {
        return refundIdempotencyKey;
    }

    public Instant getPaymentDeadline() {
        return paymentDeadline;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPreferenceId() {
        return preferenceId;
    }

    public String getPaymentLink() {
        return paymentLink;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public String getRefundId() {
        return refundId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
