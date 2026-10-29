package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetStatus;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetJpaEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Round-trip tocando TODO campo: e o teste que pega o save que copia campo por campo e esquece a coluna
// nova, que foi a causa de tres bugs do bloco anterior, sem nenhum sintoma no log.
class BudgetMapperTest {

    @Test
    void copiesEveryFieldBothWays() {
        UUID id = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        List<BudgetItem> items = List.of(
                new BudgetItem("SERVICE", 1L, "Troca de oleo", 1, new BigDecimal("150.00")),
                new BudgetItem("PART", 7L, "Filtro", 3, new BigDecimal("35.90")));
        Budget original = Budget.restore(id, 42L, sagaId, "SP-01", "11144477735", items,
                new BigDecimal("257.70"), Instant.parse("2026-10-26T12:00:00Z"),
                BudgetStatus.CANCELED, "11144477735", "OUT_OF_STOCK",
                Instant.parse("2026-10-25T10:00:00Z"), Instant.parse("2026-10-25T11:00:00Z"));

        BudgetJpaEntity entity = new BudgetJpaEntity();
        BudgetMapper.copyInto(original, entity);
        Budget restored = BudgetMapper.toDomain(entity);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getWorkOrderId(), restored.getWorkOrderId());
        assertEquals(original.getSagaId(), restored.getSagaId());
        assertEquals(original.getBranchId(), restored.getBranchId());
        assertEquals(original.getCustomerDocument(), restored.getCustomerDocument());
        assertEquals(original.getTotal(), restored.getTotal());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getApprovalDeadline(), restored.getApprovalDeadline());
        assertEquals(original.getApprovedBy(), restored.getApprovedBy());
        assertEquals(original.getCancelReason(), restored.getCancelReason());
        assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        assertEquals(original.getUpdatedAt(), restored.getUpdatedAt());
        assertEquals(original.getItems(), restored.getItems());
    }

    @Test
    void replacesTheItemCollectionInsteadOfAppending() {
        Budget original = Budget.restore(UUID.randomUUID(), 42L, UUID.randomUUID(), "SP-01",
                "11144477735", List.of(new BudgetItem("PART", 7L, "Filtro", 1, BigDecimal.ONE)),
                BigDecimal.ONE, Instant.EPOCH, BudgetStatus.PENDING_APPROVAL, null, null,
                Instant.EPOCH, Instant.EPOCH);
        BudgetJpaEntity entity = new BudgetJpaEntity();

        BudgetMapper.copyInto(original, entity);
        BudgetMapper.copyInto(original, entity);

        assertEquals(1, entity.items.size());
    }
}
