package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.CancelReason;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentStatus;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.PaymentGatewayException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentRefundedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentRequestedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.RefundPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RequestPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.when;

class PaymentInteractorTest {

    private BudgetRepositoryPort budgets;
    private PaymentRepositoryPort payments;
    private PaymentGatewayPort gateway;
    private SagaOutboxPort outbox;
    private PaymentUseCase useCase;
    private Budget budget;

    @BeforeEach
    void setUp() {
        budgets = mock(BudgetRepositoryPort.class);
        payments = mock(PaymentRepositoryPort.class);
        gateway = mock(PaymentGatewayPort.class);
        outbox = mock(SagaOutboxPort.class);
        budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new PaymentInteractor(budgets, payments, gateway, outbox,
                Clock.fixed(BillingFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void createsThePreferenceAndAnswersPaymentRequested() {
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.empty());
        when(gateway.createPreference(any(), any()))
                .thenReturn(new PreferenceCreated("pref-1", "https://mp/checkout"));

        useCase.request(requestCommand());

        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.PAYMENT_REQUESTED, event.envelope().type());
        PaymentRequestedPayload payload = (PaymentRequestedPayload) event.payload();
        assertEquals("https://mp/checkout", payload.paymentLink());
        assertEquals("pref-1", payload.preferenceId());
    }

    @Test
    void doesNotCreateASecondPreferenceForTheSamePayment() {
        Payment payment = BillingFixtures.payment(budget.getId());
        payment.linkCreated("pref-1", "https://mp/checkout", BillingFixtures.NOW);
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.of(payment));

        useCase.request(requestCommand());

        verify(gateway, never()).createPreference(any(), any());
        assertEquals(BillingMessageType.PAYMENT_REQUESTED, captureEvent().envelope().type());
    }

    // 5xx esgotado no Mercado Pago e resposta de falha de negocio para a saga, nao excecao que sobe: a
    // mensagem nao pode bater na DLQ, porque o orquestrador tem de compensar.
    @Test
    void answersPaymentRequestFailedWhenMercadoPagoGivesUp() {
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.empty());
        when(gateway.createPreference(any(), any()))
                .thenThrow(new PaymentGatewayException("Mercado Pago answered HTTP 500."));

        useCase.request(requestCommand());

        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.PAYMENT_REQUEST_FAILED, event.envelope().type());
        assertEquals("Mercado Pago answered HTTP 500.", ((FailurePayload) event.payload()).reason());
    }

    @Test
    void answersPaymentCanceledWhenThereIsNoChargeYet() {
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.empty());

        useCase.cancel(cancelCommand());

        verify(gateway, never()).cancelPayment(any());
        assertEquals(BillingMessageType.PAYMENT_CANCELED, captureEvent().envelope().type());
    }

    @Test
    void cancelsAtMercadoPagoAndAnswersPaymentCanceled() {
        Payment payment = pendingPaymentWithMercadoPagoId();
        when(gateway.cancelPayment("mp-1")).thenReturn(CancellationOutcome.CANCELED);

        useCase.cancel(cancelCommand());

        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        assertEquals(BillingMessageType.PAYMENT_CANCELED, captureEvent().envelope().type());
    }

    // Ramo do erro 2018: o pagamento ja esta aprovado e nao e cancelavel. Nao e falha, e mudanca de
    // caminho - estorna com a mesma chave de idempotencia e ainda confirma a compensacao.
    @Test
    void refundsWhenMercadoPagoSaysThePaymentIsNoLongerCancelable() {
        Payment payment = pendingPaymentWithMercadoPagoId();
        when(gateway.cancelPayment("mp-1")).thenReturn(CancellationOutcome.NOT_CANCELABLE);
        when(gateway.refundPayment("mp-1", payment.getRefundIdempotencyKey()))
                .thenReturn(new RefundCreated("refund-1"));

        useCase.cancel(cancelCommand());

        assertTrue(payment.isRefunded());
        assertEquals(CancelReason.PAID_AFTER_CANCELLATION, payment.getCancelReason());
        assertEquals(BillingMessageType.PAYMENT_CANCELED, captureEvent().envelope().type());
    }

    // Best-effort: o Mercado Pago recusou por outro motivo e a compensacao segue. Suprimir o
    // PaymentCanceled aqui deixaria a saga presa em COMPENSATING para sempre.
    @Test
    void answersPaymentCanceledEvenWhenMercadoPagoRefuses() {
        Payment payment = pendingPaymentWithMercadoPagoId();
        when(gateway.cancelPayment("mp-1")).thenReturn(CancellationOutcome.REFUSED);

        useCase.cancel(cancelCommand());

        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        verify(gateway, never()).refundPayment(any(), any());
        assertEquals(BillingMessageType.PAYMENT_CANCELED, captureEvent().envelope().type());
    }

    @Test
    void refundsAndAnswersPaymentRefunded() {
        Payment payment = confirmedPayment();
        when(gateway.refundPayment("mp-1", payment.getRefundIdempotencyKey()))
                .thenReturn(new RefundCreated("refund-1"));

        useCase.refund(refundCommand());

        assertTrue(payment.isRefunded());
        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.PAYMENT_REFUNDED, event.envelope().type());
        assertEquals("refund-1", ((PaymentRefundedPayload) event.payload()).refundId());
    }

    // O caso que travaria a compensacao para sempre: o Billing ja estornou sozinho pelo webhook, e dai
    // chega o RefundPayment do orquestrador. Sem publicar o PaymentRefunded, a saga nunca fecha.
    @Test
    void answersPaymentRefundedWhenThePaymentWasAlreadyRefunded() {
        Payment payment = confirmedPayment();
        payment.refund("refund-1", CancelReason.PAID_AFTER_CANCELLATION, BillingFixtures.NOW);

        useCase.refund(refundCommand());

        verify(gateway, never()).refundPayment(any(), any());
        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.PAYMENT_REFUNDED, event.envelope().type());
        assertEquals("refund-1", ((PaymentRefundedPayload) event.payload()).refundId());
    }

    @Test
    void failsWhenTheRefundTargetIsUnknown() {
        when(payments.findByMpPaymentId("mp-404")).thenReturn(Optional.empty());
        RefundPaymentCommand command = new RefundPaymentCommand(
                BillingFixtures.envelope(BillingMessageType.REFUND_PAYMENT), "mp-404", "REPAIR_FAILED");

        assertThrows(BudgetNotFoundException.class, () -> useCase.refund(command));
    }

    private Payment pendingPaymentWithMercadoPagoId() {
        Payment payment = BillingFixtures.payment(budget.getId());
        payment.rememberAttempt("mp-1", BillingFixtures.NOW);
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    private Payment confirmedPayment() {
        Payment payment = BillingFixtures.payment(budget.getId());
        payment.confirm("mp-1", BillingFixtures.NOW);
        when(payments.findByMpPaymentId("mp-1")).thenReturn(Optional.of(payment));
        return payment;
    }

    private RefundPaymentCommand refundCommand() {
        return new RefundPaymentCommand(BillingFixtures.envelope(BillingMessageType.REFUND_PAYMENT),
                "mp-1", "REPAIR_FAILED");
    }

    private RequestPaymentCommand requestCommand() {
        return new RequestPaymentCommand(BillingFixtures.envelope(BillingMessageType.REQUEST_PAYMENT),
                budget.getId().toString(), BillingFixtures.total(),
                BillingFixtures.NOW.plusSeconds(1800));
    }

    private CancelPaymentCommand cancelCommand() {
        return new CancelPaymentCommand(BillingFixtures.envelope(BillingMessageType.CANCEL_PAYMENT),
                budget.getId().toString(), "PAYMENT_EXPIRED");
    }

    private BillingEvent captureEvent() {
        ArgumentCaptor<BillingEvent> captor = ArgumentCaptor.forClass(BillingEvent.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }
}
