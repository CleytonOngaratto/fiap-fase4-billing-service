package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.CancelReason;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentStatus;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentAttemptRejectedPayload;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentNotificationUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentAttemptPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentNotificationInteractorTest {

    private static final String MP_ID = "mp-1";

    private BudgetRepositoryPort budgets;
    private PaymentRepositoryPort payments;
    private PaymentAttemptPort attempts;
    private PaymentGatewayPort gateway;
    private SagaOutboxPort outbox;
    private PaymentNotificationUseCase useCase;
    private Budget budget;
    private Payment payment;

    @BeforeEach
    void setUp() {
        budgets = mock(BudgetRepositoryPort.class);
        payments = mock(PaymentRepositoryPort.class);
        attempts = mock(PaymentAttemptPort.class);
        gateway = mock(PaymentGatewayPort.class);
        outbox = mock(SagaOutboxPort.class);
        budget = BillingFixtures.budget();
        payment = BillingFixtures.payment(budget.getId());
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attempts.recordIfNew(any())).thenReturn(true);
        useCase = new PaymentNotificationInteractor(budgets, payments, attempts, gateway, outbox,
                Clock.fixed(BillingFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmsTheApprovedPaymentAndAnswersPaymentConfirmed() {
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("approved", "accredited"));

        useCase.onNotification(MP_ID);

        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals(BillingMessageType.PAYMENT_CONFIRMED, captureEvent().envelope().type());
    }

    // Pagamento aprovado que chega depois do cancelamento: estorna com a mesma chave e NAO publica evento
    // nenhum. A saga ja esta compensada e nao tem passo pendente; um PaymentRefunded avulso seria resposta
    // para quem nao perguntou.
    @Test
    void refundsTheApprovedPaymentOfACanceledBudgetWithoutPublishingAnyEvent() {
        budget.cancel("PAYMENT_EXPIRED", BillingFixtures.NOW);
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("approved", "accredited"));
        when(gateway.refundPayment(MP_ID, payment.getRefundIdempotencyKey()))
                .thenReturn(new RefundCreated("refund-1"));

        useCase.onNotification(MP_ID);

        assertTrue(payment.isRefunded());
        assertEquals(CancelReason.PAID_AFTER_CANCELLATION, payment.getCancelReason());
        verifyNoInteractions(outbox);
    }

    @Test
    void doesNotRefundTwiceWhenTheNotificationIsRedelivered() {
        budget.cancel("PAYMENT_EXPIRED", BillingFixtures.NOW);
        payment.refund("refund-1", CancelReason.PAID_AFTER_CANCELLATION, BillingFixtures.NOW);
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("approved", "accredited"));

        useCase.onNotification(MP_ID);

        verify(gateway, never()).refundPayment(any(), any());
        verifyNoInteractions(outbox);
    }

    // Reentrega do webhook aprovado: a transicao devolve false e nenhum segundo evento sai. E aqui, e so
    // aqui, que vale a regra "publica so se a transicao mudou algo" - o webhook nao tem messageId.
    @Test
    void doesNotAnswerTwiceWhenTheApprovedNotificationIsRedelivered() {
        payment.confirm(MP_ID, BillingFixtures.NOW);
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("approved", "accredited"));

        useCase.onNotification(MP_ID);

        verifyNoInteractions(outbox);
    }

    @Test
    void recordsTheRejectedAttemptAndAnswersWithStatusDetailAndReason() {
        when(gateway.getPayment(MP_ID))
                .thenReturn(snapshot("rejected", "cc_rejected_insufficient_amount"));

        useCase.onNotification(MP_ID);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.PAYMENT_ATTEMPT_REJECTED, event.envelope().type());
        PaymentAttemptRejectedPayload payload = (PaymentAttemptRejectedPayload) event.payload();
        assertEquals("cc_rejected_insufficient_amount", payload.statusDetail());
        assertEquals("cc_rejected_insufficient_amount", payload.reason());
    }

    // Reentrega da recusa: o check-then-insert devolve false, nenhuma segunda linha em payment_attempts e
    // nenhum segundo evento. O UNIQUE do banco e rede de seguranca, nao o caminho normal.
    @Test
    void ignoresARedeliveredRejectedNotification() {
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("rejected", "cc_rejected_other_reason"));
        when(attempts.recordIfNew(any())).thenReturn(false);

        useCase.onNotification(MP_ID);

        verify(payments, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void failsWhenTheNotificationHasNoUsableExternalReference() {
        when(gateway.getPayment(MP_ID))
                .thenReturn(new PaymentSnapshot(MP_ID, "approved", "accredited", BigDecimal.ONE, "x"));

        assertThrows(BudgetNotFoundException.class, () -> useCase.onNotification(MP_ID));
    }

    @Test
    void failsWhenThereIsNoPaymentForTheReferencedBudget() {
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.empty());
        when(gateway.getPayment(MP_ID)).thenReturn(snapshot("approved", "accredited"));

        assertThrows(BudgetNotFoundException.class, () -> useCase.onNotification(MP_ID));
    }

    private PaymentSnapshot snapshot(String status, String statusDetail) {
        return new PaymentSnapshot(MP_ID, status, statusDetail, BillingFixtures.total(),
                budget.getId().toString());
    }

    private BillingEvent captureEvent() {
        ArgumentCaptor<BillingEvent> captor = ArgumentCaptor.forClass(BillingEvent.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }
}
