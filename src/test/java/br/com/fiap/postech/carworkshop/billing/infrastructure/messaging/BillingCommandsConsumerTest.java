package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogLines;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingCommandsConsumerTest {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/billing-commands.fifo";

    private SqsClient sqs;
    private BillingCommandHandler handler;
    private TraceContext trace;
    private BillingCommandsConsumer consumer;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(BillingCommandHandler.class);
        trace = mock(TraceContext.class);
        SagaEnvelopeCodec codec = TestCodec.codec();
        consumer = new BillingCommandsConsumer();
        consumer.sqs = sqs;
        consumer.codec = codec;
        consumer.handler = handler;
        consumer.trace = trace;
        consumer.commandsQueueUrl = QUEUE_URL;
        consumer.batchSize = 10;
    }

    @Test
    void handlesAndDeletesEveryMessageOfTheBatch() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message("CreateBudget", "rh-1"), message("CancelBudget", "rh-2"))
                        .build());

        consumer.poll();

        verify(handler, times(2)).apply(any(JsonNode.class));
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void readsTheBatchSizeAndTheQueueUrlFromTheConfiguration() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().build());

        consumer.poll();

        ReceiveMessageRequest request = captureReceive();
        assertEquals(QUEUE_URL, request.queueUrl());
        assertEquals(10, request.maxNumberOfMessages());
    }

    // Corpo ilegivel fica na fila de proposito: so o redrive leva para a DLQ, que e onde da para investigar.
    @Test
    void leavesAMalformedBodyForTheRedrivePolicy() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(handler, never()).apply(any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deletesAnUnknownCommandTypeWithoutHandlingIt() {
        consumer.consume(message("SomethingElse", "rh-1"));

        verify(handler, never()).apply(any());
        assertEquals("rh-1", captureDelete().receiptHandle());
    }

    @Test
    void leavesAFailedCommandForTheRedrivePolicy() {
        doThrow(new IllegalStateException("boom")).when(handler).apply(any(JsonNode.class));

        consumer.consume(message("CreateBudget", "rh-1"));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void acceptsTheTraceContextThatCameWithTheCommand() {
        consumer.consume(message("CreateBudget", "rh-1").toBuilder()
                .messageAttributes(Map.of(
                        "traceparent", text("00-trace-span-01"),
                        "newrelic", text("payload"),
                        "type", text("CreateBudget")))
                .build());

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(trace).accept(captor.capture());
        assertEquals("00-trace-span-01", captor.getValue().get("traceparent"));
        assertEquals("payload", captor.getValue().get("newrelic"));
    }

    @Test
    void namesTheTransactionAfterTheQueueAndTheCommandType() {
        consumer.consume(message("CreateBudget", "rh-1"));

        verify(trace).nameTransaction("Message", "billing-commands/CreateBudget");
        verify(trace).addAttribute("messageType", "CreateBudget");
        verify(trace).addAttribute(LogContext.SAGA_ID, "22222222-2222-2222-2222-222222222222");
        verify(trace).addAttribute("workOrderId", "42");
        verify(trace).addAttribute(LogContext.BRANCH_ID, "SP-01");
    }

    @Test
    void namesTheTransactionOfAnUnreadableCommand() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(trace).nameTransaction("Message", "billing-commands/unreadable");
    }

    @Test
    void reportsTheFailureToTheTransactionOfTheCommand() {
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(handler).apply(any(JsonNode.class));

        consumer.consume(message("CreateBudget", "rh-1"));

        verify(trace).noticeError(failure);
    }

    @Test
    void reportsAnUnreadableCommandToItsTransaction() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(trace).noticeError(any(RuntimeException.class));
    }

    @Test
    void reportsNoErrorForACommandThatWasHandled() {
        consumer.consume(message("CreateBudget", "rh-1"));

        verify(trace, never()).noticeError(any());
    }

    @Test
    void logsTheFailureWithTheContextOfTheCommand() {
        doThrow(new IllegalStateException("boom")).when(handler).apply(any(JsonNode.class));

        try (LogLines lines = LogLines.of(BillingCommandsConsumer.class)) {
            consumer.consume(message("CreateBudget", "rh-1"));

            assertEquals(List.of(Map.of(
                    LogContext.CORRELATION_ID, "EVIDENCIA-B5-001",
                    LogContext.SAGA_ID, "22222222-2222-2222-2222-222222222222",
                    LogContext.BRANCH_ID, "SP-01")), lines.errors());
        }
    }

    @Test
    void handlesTheCommandEvenWhenTheSagaIdIsMalformed() {
        consumer.consume(Message.builder()
                .body("{\"type\":\"CreateBudget\",\"sagaId\":\"not-a-uuid\"}")
                .receiptHandle("rh-1")
                .build());

        verify(handler).apply(any(JsonNode.class));
        assertEquals("rh-1", captureDelete().receiptHandle());
    }

    @Test
    void handlesTheCommandWhenThereIsNoSagaId() {
        consumer.consume(Message.builder()
                .body("{\"type\":\"CancelPayment\"}")
                .receiptHandle("rh-2")
                .build());

        verify(handler).apply(any(JsonNode.class));
        assertEquals("rh-2", captureDelete().receiptHandle());
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static Message message(String type, String receiptHandle) {
        return Message.builder()
                .body("{\"messageId\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"" + type
                        + "\",\"sagaId\":\"22222222-2222-2222-2222-222222222222\",\"branchId\":\"SP-01\","
                        + "\"correlationId\":\"EVIDENCIA-B5-001\",\"workOrderId\":42,\"payload\":{}}")
                .receiptHandle(receiptHandle)
                .build();
    }

    private ReceiveMessageRequest captureReceive() {
        ArgumentCaptor<ReceiveMessageRequest> captor =
                ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs).receiveMessage(captor.capture());
        return captor.getValue();
    }

    private DeleteMessageRequest captureDelete() {
        ArgumentCaptor<DeleteMessageRequest> captor =
                ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs).deleteMessage(captor.capture());
        return captor.getValue();
    }
}
