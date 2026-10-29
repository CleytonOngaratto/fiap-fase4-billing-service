package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaInboxPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingCommandHandlerTest {

    private SagaEnvelopeCodec codec;
    private SagaInboxPort inbox;
    private BudgetUseCase budgets;
    private PaymentUseCase payments;
    private BillingCommandHandler handler;

    @BeforeEach
    void setUp() {
        codec = TestCodec.codec();
        inbox = mock(SagaInboxPort.class);
        budgets = mock(BudgetUseCase.class);
        payments = mock(PaymentUseCase.class);
        when(inbox.recordIfNew(any(), anyLong(), anyString())).thenReturn(true);
        handler = new BillingCommandHandler();
        handler.codec = codec;
        handler.inbox = inbox;
        handler.budgets = budgets;
        handler.payments = payments;
    }

    @Test
    void dispatchesEachKnownCommandToItsUseCase() {
        handler.apply(body(BillingMessageType.CREATE_BUDGET));
        handler.apply(body(BillingMessageType.CANCEL_BUDGET));
        handler.apply(body(BillingMessageType.REQUEST_PAYMENT));
        handler.apply(body(BillingMessageType.CANCEL_PAYMENT));
        handler.apply(body(BillingMessageType.REFUND_PAYMENT));

        verify(budgets).create(any());
        verify(budgets).cancel(any());
        verify(payments).request(any());
        verify(payments).cancel(any());
        verify(payments).refund(any());
    }

    // A inbox e quem decide: messageId repetido nao reexecuta o efeito, e e so isso que ela decide — a
    // primeira entrega responde sempre, mesmo quando o estado ja era o final.
    @Test
    void doesNothingWhenTheMessageWasAlreadyProcessed() {
        when(inbox.recordIfNew(any(), anyLong(), anyString())).thenReturn(false);

        handler.apply(body(BillingMessageType.CREATE_BUDGET));

        verifyNoInteractions(budgets);
        verifyNoInteractions(payments);
    }

    @Test
    void refusesACommandTypeWithoutHandler() {
        JsonNode node = body("SomethingElse");

        assertThrows(IllegalStateException.class, () -> handler.apply(node));

        verify(budgets, never()).create(any());
    }

    private JsonNode body(String type) {
        return codec.decode("{\"messageId\":\"11111111-1111-1111-1111-111111111111\",\"type\":\""
                + type + "\",\"workOrderId\":42,\"payload\":{\"budgetId\":\"b-1\"}}");
    }
}
