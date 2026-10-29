package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_outbox")
public class SagaOutboxJpaEntity {

    @Id
    @Column(name = "message_id", nullable = false)
    public UUID messageId;

    @Column(name = "type", nullable = false, length = 60)
    public String type;

    @Column(name = "version", nullable = false)
    public int version;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "saga_id", nullable = false)
    public UUID sagaId;

    @Column(name = "work_order_id", nullable = false)
    public Long workOrderId;

    @Column(name = "branch_id", length = 20)
    public String branchId;

    @Column(name = "correlation_id", length = 120)
    public String correlationId;

    @Column(name = "causation_id")
    public UUID causationId;

    @Column(name = "payload", nullable = false)
    public String payload;

    @Column(name = "sent_at")
    public Instant sentAt;
}
