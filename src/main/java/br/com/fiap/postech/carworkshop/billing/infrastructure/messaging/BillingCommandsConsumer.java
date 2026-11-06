package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.newrelic.api.agent.Trace;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class BillingCommandsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingCommandsConsumer.class);

    private static final String TRANSACTION_CATEGORY = "Message";
    private static final String QUEUE = "billing-commands";

    private static final Set<String> KNOWN = Set.of(
            BillingMessageType.CREATE_BUDGET,
            BillingMessageType.CANCEL_BUDGET,
            BillingMessageType.REQUEST_PAYMENT,
            BillingMessageType.CANCEL_PAYMENT,
            BillingMessageType.REFUND_PAYMENT);

    @Inject
    SqsClient sqs;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    BillingCommandHandler handler;

    @Inject
    TraceContext trace;

    @ConfigProperty(name = "billing.commands.queue-url")
    String commandsQueueUrl;

    @ConfigProperty(name = "billing.commands.batch-size")
    int batchSize;

    @Scheduled(every = "2s", identity = "billing-commands-consumer",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void poll() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .maxNumberOfMessages(batchSize)
                .messageAttributeNames("All")
                .build()).messages();

        for (Message message : messages) {
            consume(message);
        }
    }

    @Trace(dispatcher = true)
    void consume(Message message) {
        trace.accept(traceHeadersOf(message));
        JsonNode envelope;
        try {
            envelope = codec.decode(message.body());
        } catch (RuntimeException e) {
            trace.nameTransaction(TRANSACTION_CATEGORY, QUEUE + "/unreadable");
            // Sem apagar: a mensagem volta, esgota o maxReceiveCount e para na DLQ, onde da para investigar.
            log.error("Command could not be read; leaving it for the redrive policy.", e);
            trace.noticeError(e);
            return;
        }

        String type = envelope.path(SagaEnvelopeCodec.FIELD_TYPE).asText(null);
        describe(envelope, type);
        if (!KNOWN.contains(type)) {
            log.warn("Unknown command type {}: ignored.", type);
            delete(message);
            return;
        }

        try (LogContext ignored = LogContext.of(textOf(envelope, SagaEnvelopeCodec.FIELD_CORRELATION_ID),
                sagaIdOf(envelope), textOf(envelope, SagaEnvelopeCodec.FIELD_BRANCH_ID))) {
            apply(message, envelope);
        }
    }

    private void apply(Message message, JsonNode envelope) {
        try {
            handler.apply(envelope);
            delete(message);
        } catch (RuntimeException e) {
            log.error("Command failed; leaving it for the redrive policy.", e);
            trace.noticeError(e);
        }
    }

    private void describe(JsonNode envelope, String type) {
        trace.nameTransaction(TRANSACTION_CATEGORY, QUEUE + "/" + type);
        trace.addAttribute("messageType", type);
        trace.addAttribute(LogContext.SAGA_ID, textOf(envelope, SagaEnvelopeCodec.FIELD_SAGA_ID));
        trace.addAttribute("workOrderId", textOf(envelope, SagaEnvelopeCodec.FIELD_WORK_ORDER_ID));
        trace.addAttribute(LogContext.BRANCH_ID, textOf(envelope, SagaEnvelopeCodec.FIELD_BRANCH_ID));
    }

    private static Map<String, String> traceHeadersOf(Message message) {
        Map<String, String> headers = new HashMap<>();
        message.messageAttributes().forEach((name, value) -> headers.put(name, value.stringValue()));
        return headers;
    }

    private static String textOf(JsonNode envelope, String field) {
        return envelope.path(field).asText(null);
    }

    private void delete(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    // O sagaId so alimenta o log: um valor malformado nao pode derrubar o processamento da mensagem.
    private static UUID sagaIdOf(JsonNode envelope) {
        String value = envelope.path(SagaEnvelopeCodec.FIELD_SAGA_ID).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
