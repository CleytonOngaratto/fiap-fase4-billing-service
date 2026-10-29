package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentAttempt;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentAttemptJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentAttemptPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAttemptGatewayTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private PaymentAttemptPanacheRepository repository;
    private PaymentAttemptGateway gateway;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentAttemptPanacheRepository.class);
        gateway = new PaymentAttemptGateway();
        gateway.repository = repository;
    }

    @Test
    void recordsANotificationSeenForTheFirstTime() {
        when(repository.findNotification(PAYMENT_ID, "mp-1", "cc_rejected_insufficient_amount"))
                .thenReturn(null);

        assertTrue(gateway.recordIfNew(new PaymentAttempt(PAYMENT_ID, "mp-1",
                "cc_rejected_insufficient_amount", BillingFixtures.NOW)));

        PaymentAttemptJpaEntity entity = capture();
        assertEquals(PAYMENT_ID, entity.paymentId);
        assertEquals("mp-1", entity.mpPaymentId);
        assertEquals("cc_rejected_insufficient_amount", entity.statusDetail);
        assertEquals(BillingFixtures.NOW, entity.occurredAt);
    }

    @Test
    void answersFalseForANotificationAlreadyRecorded() {
        when(repository.findNotification(PAYMENT_ID, "mp-1", "cc_rejected_insufficient_amount"))
                .thenReturn(new PaymentAttemptJpaEntity());

        assertFalse(gateway.recordIfNew(new PaymentAttempt(PAYMENT_ID, "mp-1",
                "cc_rejected_insufficient_amount", BillingFixtures.NOW)));

        verify(repository, never()).persist(any(PaymentAttemptJpaEntity.class));
    }

    // Sem o coalesce a deduplicacao furaria nos dois caminhos: NULL e distinto de NULL numa constraint
    // UNIQUE do PostgreSQL, e "status_detail = NULL" nunca e verdadeiro.
    @Test
    void usesThePlaceholderDetailOnBothSidesWhenTheGatewaySentNone() {
        when(repository.findNotification(PAYMENT_ID, "mp-1", "unknown")).thenReturn(null);

        assertTrue(gateway.recordIfNew(
                new PaymentAttempt(PAYMENT_ID, "mp-1", null, BillingFixtures.NOW)));

        verify(repository).findNotification(PAYMENT_ID, "mp-1", "unknown");
        assertEquals("unknown", capture().statusDetail);
    }

    private PaymentAttemptJpaEntity capture() {
        ArgumentCaptor<PaymentAttemptJpaEntity> captor =
                ArgumentCaptor.forClass(PaymentAttemptJpaEntity.class);
        verify(repository).persist(captor.capture());
        return captor.getValue();
    }
}
