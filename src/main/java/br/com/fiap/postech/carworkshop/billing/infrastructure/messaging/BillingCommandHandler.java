package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaInboxPort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BillingCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingCommandHandler.class);

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    SagaInboxPort inbox;

    @Inject
    BudgetUseCase budgets;

    @Inject
    PaymentUseCase payments;

    // Bean separado do consumidor de proposito: autoinvocacao nao passa pelo interceptador, e o
    // @Transactional seria ignorado em silencio. Lancar aqui e o que impede o consumidor de apagar a mensagem.
    @Transactional
    public void apply(JsonNode node) {
        SagaEnvelope envelope = codec.envelopeOf(node);
        if (!inbox.recordIfNew(envelope.messageId(), envelope.workOrderId(), envelope.type())) {
            log.info("Command {} of type {} was already processed: nothing to do.",
                    envelope.messageId(), envelope.type());
            return;
        }
        dispatch(envelope.type(), node);
    }

    private void dispatch(String type, JsonNode node) {
        switch (type) {
            case BillingMessageType.CREATE_BUDGET -> budgets.create(codec.toCreateBudget(node));
            case BillingMessageType.CANCEL_BUDGET -> budgets.cancel(codec.toCancelBudget(node));
            case BillingMessageType.REQUEST_PAYMENT -> payments.request(codec.toRequestPayment(node));
            case BillingMessageType.CANCEL_PAYMENT -> payments.cancel(codec.toCancelPayment(node));
            case BillingMessageType.REFUND_PAYMENT -> payments.refund(codec.toRefundPayment(node));
            default -> throw new IllegalStateException("Command type " + type + " has no handler.");
        }
    }
}
