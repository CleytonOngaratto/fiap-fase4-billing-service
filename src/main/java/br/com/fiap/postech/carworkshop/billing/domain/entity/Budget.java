package br.com.fiap.postech.carworkshop.billing.domain.entity;

import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotApprovableException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Budget {

    private final UUID id;
    private final Long workOrderId;
    private final UUID sagaId;
    private final String branchId;
    private final String customerDocument;
    private final List<BudgetItem> items;
    private final BigDecimal total;
    private final Instant approvalDeadline;
    private final Instant createdAt;
    private BudgetStatus status;
    private String approvedBy;
    private String cancelReason;
    private Instant updatedAt;

    private Budget(UUID id, Long workOrderId, UUID sagaId, String branchId, String customerDocument,
                   List<BudgetItem> items, BigDecimal total, Instant approvalDeadline,
                   BudgetStatus status, String approvedBy, String cancelReason, Instant createdAt,
                   Instant updatedAt) {
        this.id = id;
        this.workOrderId = workOrderId;
        this.sagaId = sagaId;
        this.branchId = branchId;
        this.customerDocument = customerDocument;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.total = total;
        this.approvalDeadline = approvalDeadline;
        this.status = status;
        this.approvedBy = approvedBy;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Budget create(Long workOrderId, UUID sagaId, String branchId, String customerDocument,
                                List<BudgetItem> items, BigDecimal total, Instant approvalDeadline,
                                Instant now) {
        return new Budget(UUID.randomUUID(), workOrderId, sagaId, branchId, customerDocument, items,
                total, approvalDeadline, BudgetStatus.PENDING_APPROVAL, null, null, now, now);
    }

    public static Budget restore(UUID id, Long workOrderId, UUID sagaId, String branchId,
                                 String customerDocument, List<BudgetItem> items, BigDecimal total,
                                 Instant approvalDeadline, BudgetStatus status, String approvedBy,
                                 String cancelReason, Instant createdAt, Instant updatedAt) {
        return new Budget(id, workOrderId, sagaId, branchId, customerDocument, items, total,
                approvalDeadline, status, approvedBy, cancelReason, createdAt, updatedAt);
    }

    public void approve(String document, Instant now) {
        requireOwner(document);
        requirePendingApproval();
        requireWithinDeadline(now);
        status = BudgetStatus.APPROVED;
        approvedBy = document;
        updatedAt = now;
    }

    public void reject(String document, Instant now) {
        requireOwner(document);
        requirePendingApproval();
        requireWithinDeadline(now);
        status = BudgetStatus.REJECTED;
        cancelReason = CancelReason.REJECTED_BY_CUSTOMER;
        updatedAt = now;
    }

    public boolean cancel(String reason, Instant now) {
        if (status == BudgetStatus.CANCELED) {
            return false;
        }
        status = BudgetStatus.CANCELED;
        cancelReason = reason;
        updatedAt = now;
        return true;
    }

    public boolean isCanceled() {
        return status == BudgetStatus.CANCELED;
    }

    public boolean isOwnedBy(String document) {
        return customerDocument.equals(document);
    }

    private void requireOwner(String document) {
        if (!isOwnedBy(document)) {
            throw new NotBudgetOwnerException("This budget belongs to another customer.");
        }
    }

    private void requirePendingApproval() {
        if (status != BudgetStatus.PENDING_APPROVAL) {
            throw new BudgetNotApprovableException(
                    "This budget is already " + status + ": it can no longer be answered.");
        }
    }

    private void requireWithinDeadline(Instant now) {
        if (now.isAfter(approvalDeadline)) {
            throw new BudgetNotApprovableException(
                    "The approval deadline for this budget has expired.");
        }
    }

    public UUID getId() {
        return id;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getBranchId() {
        return branchId;
    }

    public String getCustomerDocument() {
        return customerDocument;
    }

    public List<BudgetItem> getItems() {
        return items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getApprovalDeadline() {
        return approvalDeadline;
    }

    public BudgetStatus getStatus() {
        return status;
    }

    public String getApprovedBy() {
        return approvedBy;
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
