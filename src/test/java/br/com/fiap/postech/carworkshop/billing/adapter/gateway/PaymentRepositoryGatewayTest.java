package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRepositoryGatewayTest {

    private PaymentPanacheRepository repository;
    private PaymentRepositoryGateway gateway;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentPanacheRepository.class);
        gateway = new PaymentRepositoryGateway();
        gateway.repository = repository;
    }

    @Test
    void persistsAPaymentThatIsNotInTheDatabaseYet() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());
        when(repository.findById(payment.getId())).thenReturn(null);

        assertSame(payment, gateway.save(payment));

        ArgumentCaptor<PaymentJpaEntity> captor = ArgumentCaptor.forClass(PaymentJpaEntity.class);
        verify(repository).persist(captor.capture());
        assertEquals(payment.getId(), captor.getValue().id);
        assertEquals(payment.getRefundIdempotencyKey(), captor.getValue().refundIdempotencyKey);
    }

    @Test
    void updatesTheManagedEntityWithoutPersistingItAgain() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());
        payment.linkCreated("pref-1", "https://mp/checkout", BillingFixtures.NOW);
        PaymentJpaEntity managed = new PaymentJpaEntity();
        when(repository.findById(payment.getId())).thenReturn(managed);

        gateway.save(payment);

        verify(repository, never()).persist(any(PaymentJpaEntity.class));
        assertEquals("https://mp/checkout", managed.paymentLink);
    }

    @Test
    void findsThePaymentByIdByBudgetAndByGatewayId() {
        Payment payment = BillingFixtures.payment(UUID.randomUUID());
        PaymentJpaEntity entity = new PaymentJpaEntity();
        PaymentMapper.copyInto(payment, entity);
        when(repository.findById(payment.getId())).thenReturn(entity);
        when(repository.findByBudgetId(payment.getBudgetId())).thenReturn(entity);
        when(repository.findByMpPaymentId("mp-1")).thenReturn(entity);

        assertEquals(payment.getId(), gateway.findById(payment.getId()).orElseThrow().getId());
        assertEquals(payment.getId(),
                gateway.findByBudgetId(payment.getBudgetId()).orElseThrow().getId());
        assertEquals(payment.getId(), gateway.findByMpPaymentId("mp-1").orElseThrow().getId());
    }

    @Test
    void answersEmptyWhenThereIsNoPayment() {
        when(repository.findById(any(UUID.class))).thenReturn(null);
        when(repository.findByBudgetId(any(UUID.class))).thenReturn(null);

        assertTrue(gateway.findById(UUID.randomUUID()).isEmpty());
        assertTrue(gateway.findByBudgetId(UUID.randomUUID()).isEmpty());
    }

    // O webhook chega com o id do Mercado Pago ausente em alguns topicos: consultar por null traria a
    // primeira linha qualquer da tabela.
    @Test
    void answersEmptyWithoutQueryingWhenThereIsNoGatewayId() {
        assertTrue(gateway.findByMpPaymentId(null).isEmpty());

        verifyNoInteractions(repository);
    }
}
