package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

// A unique constraint vive aqui ADEMAIS da migracao: sob %test o Flyway esta desligado (ver BudgetJpaEntity).
@Entity
@Table(name = "payment_attempts",
        uniqueConstraints = @UniqueConstraint(name = "payment_attempts_notification_key",
                columnNames = {"payment_id", "mp_payment_id", "status_detail"}))
public class PaymentAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_attempts_id_seq")
    @SequenceGenerator(name = "payment_attempts_id_seq", sequenceName = "payment_attempts_id_seq",
            allocationSize = 1)
    @Column(name = "id", nullable = false)
    public Long id;

    @Column(name = "payment_id", nullable = false)
    public UUID paymentId;

    @Column(name = "mp_payment_id", nullable = false, length = 64)
    public String mpPaymentId;

    @Column(name = "status_detail", nullable = false, length = 80)
    public String statusDetail;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
