package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();

    @Test
    void createsAPreferenceDerivedFromTheBudget() {
        Budget budget = BillingFixtures.budget();
        Payment payment = BillingFixtures.payment(budget.getId());

        PreferenceCreated preference = gateway.createPreference(budget, payment);

        assertEquals("fake-preference-" + budget.getId(), preference.preferenceId());
        assertTrue(preference.initPoint().endsWith(preference.preferenceId()));
    }

    @Test
    void answersAnApprovedPayment() {
        PaymentSnapshot snapshot = gateway.getPayment("mp-1");

        assertEquals("mp-1", snapshot.mpPaymentId());
        assertTrue(snapshot.isApproved());
        assertEquals(FakePaymentGateway.APPROVED_DETAIL, snapshot.statusDetail());
    }

    @Test
    void cancelsOnlyWhenThereIsAPaymentAtTheGateway() {
        assertEquals(CancellationOutcome.CANCELED, gateway.cancelPayment("mp-1"));
        assertEquals(CancellationOutcome.NO_PAYMENT, gateway.cancelPayment(null));
        assertEquals(CancellationOutcome.NO_PAYMENT, gateway.cancelPayment("   "));
    }

    @Test
    void refundsWithTheKeyItReceived() {
        UUID key = UUID.randomUUID();

        assertEquals("fake-refund-" + key, gateway.refundPayment("mp-1", key).refundId());
    }
}
