package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogContext;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class BillingCommandsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingCommandsConsumer.class);

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

    void consume(Message message) {
        JsonNode envelope;
        try {
            envelope = codec.decode(message.body());
        } catch (RuntimeException e) {
            // Sem apagar: a mensagem volta, esgota o maxReceiveCount e para na DLQ, onde da para investigar.
            log.error("Command could not be read; leaving it for the redrive policy.", e);
            return;
        }

        String type = envelope.path(SagaEnvelopeCodec.FIELD_TYPE).asText(null);
        if (!KNOWN.contains(type)) {
            log.warn("Unknown command type {}: ignored.", type);
            delete(message);
            return;
        }

        String correlationId = envelope.path(SagaEnvelopeCodec.FIELD_CORRELATION_ID).asText(null);
        try (LogContext ignored = LogContext.of(correlationId, sagaIdOf(envelope))) {
            handler.apply(envelope);
            delete(message);
        } catch (RuntimeException e) {
            log.error("Command failed; leaving it for the redrive policy.", e);
        }
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
