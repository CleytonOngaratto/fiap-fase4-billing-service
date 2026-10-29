package br.com.fiap.postech.carworkshop.billing.support;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingFixtures {

    public static final String OWNER = "11144477735";
    public static final String INTRUDER = "52998224725";
    public static final Long WORK_ORDER_ID = 42L;
    public static final String BRANCH = "SP-01";
    public static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private BillingFixtures() {
    }

    public static List<BudgetItem> items() {
        return List.of(
                new BudgetItem(BudgetItem.SERVICE, 1L, "Troca de oleo", 1, new BigDecimal("150.00")),
                new BudgetItem(BudgetItem.PART, 7L, "Filtro de oleo", 3, new BigDecimal("35.90")));
    }

    public static BigDecimal total() {
        return new BigDecimal("257.70");
    }

    public static Budget budget() {
        return Budget.create(WORK_ORDER_ID, UUID.randomUUID(), BRANCH, OWNER, items(), total(),
                NOW.plusSeconds(3600), NOW);
    }

    public static Payment payment(UUID budgetId) {
        return Payment.create(budgetId, WORK_ORDER_ID, total(), NOW.plusSeconds(1800), NOW);
    }

    public static SagaEnvelope envelope(String type) {
        return new SagaEnvelope(UUID.randomUUID(), type, SagaEnvelope.CURRENT_VERSION, NOW,
                UUID.randomUUID(), WORK_ORDER_ID, BRANCH, "EVIDENCIA-B5-001", null);
    }
}
