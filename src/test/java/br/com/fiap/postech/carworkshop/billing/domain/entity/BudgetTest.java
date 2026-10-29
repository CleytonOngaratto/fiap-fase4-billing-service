package br.com.fiap.postech.carworkshop.billing.domain.entity;

import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotApprovableException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetTest {

    @Test
    void startsPendingApproval() {
        Budget budget = BillingFixtures.budget();

        assertEquals(BudgetStatus.PENDING_APPROVAL, budget.getStatus());
        assertEquals(2, budget.getItems().size());
        assertTrue(budget.isOwnedBy(BillingFixtures.OWNER));
        assertFalse(budget.isOwnedBy(BillingFixtures.INTRUDER));
    }

    @Test
    void approvesForTheOwner() {
        Budget budget = BillingFixtures.budget();

        budget.approve(BillingFixtures.OWNER, BillingFixtures.NOW);

        assertEquals(BudgetStatus.APPROVED, budget.getStatus());
        assertEquals(BillingFixtures.OWNER, budget.getApprovedBy());
    }

    @Test
    void refusesApprovalFromAnotherCustomer() {
        Budget budget = BillingFixtures.budget();

        assertThrows(NotBudgetOwnerException.class,
                () -> budget.approve(BillingFixtures.INTRUDER, BillingFixtures.NOW));
        assertEquals(BudgetStatus.PENDING_APPROVAL, budget.getStatus());
    }

    @Test
    void refusesApprovalAfterTheDeadline() {
        Budget budget = BillingFixtures.budget();

        assertThrows(BudgetNotApprovableException.class,
                () -> budget.approve(BillingFixtures.OWNER, budget.getApprovalDeadline().plusSeconds(1)));
        assertEquals(BudgetStatus.PENDING_APPROVAL, budget.getStatus());
    }

    @Test
    void refusesApprovalOfACanceledBudget() {
        Budget budget = BillingFixtures.budget();
        budget.cancel("APPROVAL_EXPIRED", BillingFixtures.NOW);

        assertThrows(BudgetNotApprovableException.class,
                () -> budget.approve(BillingFixtures.OWNER, BillingFixtures.NOW));
    }

    @Test
    void rejectsForTheOwner() {
        Budget budget = BillingFixtures.budget();

        budget.reject(BillingFixtures.OWNER, BillingFixtures.NOW);

        assertEquals(BudgetStatus.REJECTED, budget.getStatus());
        assertEquals(CancelReason.REJECTED_BY_CUSTOMER, budget.getCancelReason());
    }

    @Test
    void refusesRejectionFromAnotherCustomer() {
        Budget budget = BillingFixtures.budget();

        assertThrows(NotBudgetOwnerException.class,
                () -> budget.reject(BillingFixtures.INTRUDER, BillingFixtures.NOW));
    }

    @Test
    void cancelsOnlyOnce() {
        Budget budget = BillingFixtures.budget();

        assertTrue(budget.cancel("OUT_OF_STOCK", BillingFixtures.NOW));
        assertTrue(budget.isCanceled());
        assertEquals("OUT_OF_STOCK", budget.getCancelReason());
        assertFalse(budget.cancel("OUT_OF_STOCK", BillingFixtures.NOW));
    }
}
