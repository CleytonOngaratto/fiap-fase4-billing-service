package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.TestCodec;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

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
    private BillingCommandsConsumer consumer;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(BillingCommandHandler.class);
        SagaEnvelopeCodec codec = TestCodec.codec();
        consumer = new BillingCommandsConsumer();
        consumer.sqs = sqs;
        consumer.codec = codec;
        consumer.handler = handler;
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

    private static Message message(String type, String receiptHandle) {
        return Message.builder()
                .body("{\"messageId\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"" + type
                        + "\",\"sagaId\":\"22222222-2222-2222-2222-222222222222\","
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
