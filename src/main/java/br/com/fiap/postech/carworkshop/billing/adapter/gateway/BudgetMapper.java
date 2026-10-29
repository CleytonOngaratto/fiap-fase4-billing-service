package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetStatus;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetItemEmbeddable;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetJpaEntity;

import java.util.ArrayList;
import java.util.List;

public final class BudgetMapper {

    private BudgetMapper() {
    }

    public static Budget toDomain(BudgetJpaEntity entity) {
        List<BudgetItem> items = new ArrayList<>();
        for (BudgetItemEmbeddable item : entity.items) {
            items.add(new BudgetItem(item.type, item.catalogId, item.description, item.quantity,
                    item.unitPrice));
        }
        return Budget.restore(entity.id, entity.workOrderId, entity.sagaId, entity.branchId,
                entity.customerDocument, items, entity.total, entity.approvalDeadline,
                BudgetStatus.valueOf(entity.status), entity.approvedBy, entity.cancelReason,
                entity.createdAt, entity.updatedAt);
    }

    // Copia TODO campo, inclusive a colecao de itens: foi o save campo a campo que perdeu as colecoes e as
    // colunas novas em tres bugs do bloco anterior, sem nenhum sintoma no log.
    public static void copyInto(Budget budget, BudgetJpaEntity entity) {
        entity.id = budget.getId();
        entity.workOrderId = budget.getWorkOrderId();
        entity.sagaId = budget.getSagaId();
        entity.branchId = budget.getBranchId();
        entity.customerDocument = budget.getCustomerDocument();
        entity.total = budget.getTotal();
        entity.status = budget.getStatus().name();
        entity.approvalDeadline = budget.getApprovalDeadline();
        entity.approvedBy = budget.getApprovedBy();
        entity.cancelReason = budget.getCancelReason();
        entity.createdAt = budget.getCreatedAt();
        entity.updatedAt = budget.getUpdatedAt();
        entity.items.clear();
        for (BudgetItem item : budget.getItems()) {
            entity.items.add(toEmbeddable(item));
        }
    }

    private static BudgetItemEmbeddable toEmbeddable(BudgetItem item) {
        BudgetItemEmbeddable embeddable = new BudgetItemEmbeddable();
        embeddable.type = item.type();
        embeddable.catalogId = item.catalogId();
        embeddable.description = item.description();
        embeddable.quantity = item.quantity();
        embeddable.unitPrice = item.unitPrice();
        return embeddable;
    }
}
