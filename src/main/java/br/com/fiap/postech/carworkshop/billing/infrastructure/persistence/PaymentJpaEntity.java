package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "payments_budget_id_key",
                columnNames = "budget_id"))
public class PaymentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "budget_id", nullable = false)
    public UUID budgetId;

    @Column(name = "work_order_id", nullable = false)
    public Long workOrderId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "preference_id", length = 64)
    public String preferenceId;

    @Column(name = "payment_link", length = 512)
    public String paymentLink;

    @Column(name = "mp_payment_id", length = 64)
    public String mpPaymentId;

    @Column(name = "refund_id", length = 64)
    public String refundId;

    @Column(name = "refund_idempotency_key", nullable = false)
    public UUID refundIdempotencyKey;

    @Column(name = "payment_deadline", nullable = false)
    public Instant paymentDeadline;

    @Column(name = "cancel_reason", length = 40)
    public String cancelReason;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
