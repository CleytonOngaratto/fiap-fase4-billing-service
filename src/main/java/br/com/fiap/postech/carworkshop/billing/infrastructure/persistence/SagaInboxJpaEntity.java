package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_inbox")
public class SagaInboxJpaEntity {

    @Id
    @Column(name = "message_id", nullable = false)
    public UUID messageId;

    @Column(name = "work_order_id", nullable = false)
    public Long workOrderId;

    @Column(name = "type", nullable = false, length = 60)
    public String type;

    @Column(name = "processed_at", nullable = false)
    public Instant processedAt;
}
