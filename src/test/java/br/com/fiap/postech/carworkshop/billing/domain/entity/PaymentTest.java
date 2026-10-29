package br.com.fiap.postech.carworkshop.billing.domain.entity;

import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTest {

    @Test
    void startsPendingWithAnIdempotencyKey() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertTrue(payment.isPending());
        assertNotNull(payment.getRefundIdempotencyKey());
    }

    @Test
    void recordsThePreferenceLink() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());

        payment.linkCreated("pref-1", "https://mp/checkout", BillingFixtures.NOW);

        assertEquals("pref-1", payment.getPreferenceId());
        assertEquals("https://mp/checkout", payment.getPaymentLink());
    }

    @Test
    void confirmsOnlyOnce() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());

        assertTrue(payment.confirm("mp-1", BillingFixtures.NOW));
        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals("mp-1", payment.getMpPaymentId());
        assertFalse(payment.confirm("mp-1", BillingFixtures.NOW));
    }

    @Test
    void cancelsOnlyWhileNotFinished() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());

        assertTrue(payment.cancel("PAYMENT_EXPIRED", BillingFixtures.NOW));
        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        assertFalse(payment.cancel("PAYMENT_EXPIRED", BillingFixtures.NOW));
    }

    @Test
    void refundsOnlyOnceAndWinsOverCancellation() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());
        payment.cancel("PAYMENT_EXPIRED", BillingFixtures.NOW);

        assertTrue(payment.refund("refund-1", CancelReason.PAID_AFTER_CANCELLATION,
                BillingFixtures.NOW));
        assertTrue(payment.isRefunded());
        assertEquals("refund-1", payment.getRefundId());
        assertEquals(CancelReason.PAID_AFTER_CANCELLATION, payment.getCancelReason());
        assertFalse(payment.refund("refund-2", "other", BillingFixtures.NOW));
        assertEquals("refund-1", payment.getRefundId());
    }

    @Test
    void remembersARejectedAttemptWithoutChangingTheStatus() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());

        payment.rememberAttempt("mp-9", BillingFixtures.NOW);

        assertEquals("mp-9", payment.getMpPaymentId());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }
}
