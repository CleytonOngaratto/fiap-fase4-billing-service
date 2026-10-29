package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// A unique constraint vive aqui ADEMAIS da migracao: sob %test o Flyway esta desligado e o esquema vem do
// Hibernate, logo sem esta linha o teste da idempotencia passaria sem constraint nenhuma no banco.
@Entity
@Table(name = "budgets",
        uniqueConstraints = @UniqueConstraint(name = "budgets_work_order_id_key",
                columnNames = "work_order_id"))
public class BudgetJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "work_order_id", nullable = false)
    public Long workOrderId;

    @Column(name = "saga_id", nullable = false)
    public UUID sagaId;

    @Column(name = "branch_id", length = 20)
    public String branchId;

    @Column(name = "customer_document", nullable = false, length = 14)
    public String customerDocument;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    public BigDecimal total;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "approval_deadline", nullable = false)
    public Instant approvalDeadline;

    @Column(name = "approved_by", length = 14)
    public String approvedBy;

    @Column(name = "cancel_reason", length = 40)
    public String cancelReason;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_items", joinColumns = @JoinColumn(name = "budget_id"))
    public List<BudgetItemEmbeddable> items = new ArrayList<>();
}
