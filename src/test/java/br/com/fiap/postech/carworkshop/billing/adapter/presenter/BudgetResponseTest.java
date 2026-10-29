package br.com.fiap.postech.carworkshop.billing.adapter.presenter;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetStatus;
import br.com.fiap.postech.carworkshop.billing.domain.entity.CancelReason;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BudgetResponseTest {

    @Test
    void showsTheBudgetWithItsItemsAndThePaymentLink() {
        Budget budget = BillingFixtures.budget();
        Payment payment = BillingFixtures.payment(budget.getId());
        payment.linkCreated("pref-1", "https://mp/checkout", BillingFixtures.NOW);

        BudgetResponse response = BudgetResponse.from(new BudgetView(budget, payment));

        assertEquals(budget.getId().toString(), response.id());
        assertEquals(BillingFixtures.WORK_ORDER_ID, response.workOrderId());
        assertEquals(BudgetStatus.PENDING_APPROVAL.name(), response.status());
        assertEquals(0, BillingFixtures.total().compareTo(response.total()));
        assertEquals(budget.getApprovalDeadline(), response.approvalDeadline());
        assertNull(response.cancelReason());
        assertEquals(2, response.items().size());
        BudgetResponse.Item item = response.items().get(0);
        assertEquals(BudgetItem.SERVICE, item.type());
        assertEquals(1L, item.catalogId());
        assertEquals("Troca de oleo", item.description());
        assertEquals(1, item.quantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(item.unitPrice()));
        assertEquals("PENDING", response.paymentStatus());
        assertEquals("https://mp/checkout", response.paymentLink());
        assertEquals(payment.getPaymentDeadline(), response.paymentDeadline());
    }

    // O orcamento existe antes da cobranca: o GET tem de responder com os campos de pagamento ausentes em
    // vez de estourar.
    @Test
    void showsTheBudgetBeforeAnyPaymentExists() {
        Budget budget = BillingFixtures.budget();
        budget.cancel(CancelReason.REJECTED_BY_CUSTOMER, BillingFixtures.NOW);

        BudgetResponse response = BudgetResponse.from(new BudgetView(budget, null));

        assertEquals(BudgetStatus.CANCELED.name(), response.status());
        assertEquals(CancelReason.REJECTED_BY_CUSTOMER, response.cancelReason());
        assertNull(response.paymentStatus());
        assertNull(response.paymentLink());
        assertNull(response.paymentDeadline());
    }
}
