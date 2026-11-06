package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogLines;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SnsBillingEventGatewayTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:436061918963:billing-events.fifo";

    private SnsClient sns;
    private SnsBillingEventGateway gateway;

    @BeforeEach
    void setUp() {
        sns = mock(SnsClient.class);
        gateway = new SnsBillingEventGateway(sns, TOPIC_ARN);
    }

    @Test
    void publishesWithTheFifoKeysAndTheCatalogAttributes() {
        UUID messageId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();

        gateway.publish(new OutboundMessage(messageId, "BudgetCreated", 1,
                BillingFixtures.WORK_ORDER_ID, sagaId, "EVIDENCIA-B5-001", "{}", Map.of()));

        PublishRequest request = capture();
        assertEquals(TOPIC_ARN, request.topicArn());
        assertEquals("{}", request.message());
        assertEquals("42", request.messageGroupId());
        assertEquals(messageId.toString(), request.messageDeduplicationId());

        Map<String, MessageAttributeValue> attributes = request.messageAttributes();
        assertEquals(4, attributes.size());
        assertEquals("BudgetCreated", attributes.get("type").stringValue());
        assertEquals("String", attributes.get("type").dataType());
        assertEquals("1", attributes.get("version").stringValue());
        assertEquals("Number", attributes.get("version").dataType());
        assertEquals(sagaId.toString(), attributes.get("sagaId").stringValue());
        assertEquals("EVIDENCIA-B5-001", attributes.get("correlationId").stringValue());
    }

    // Os paineis de pagamento contam esta linha no New Relic: mudar o texto zera os paineis sem erro nenhum.
    @Test
    void logsTheLineThePaymentPanelsCount() {
        try (LogLines lines = LogLines.of(SnsBillingEventGateway.class)) {
            gateway.publish(new OutboundMessage(UUID.randomUUID(), BillingMessageType.PAYMENT_CONFIRMED, 1,
                    BillingFixtures.WORK_ORDER_ID, UUID.randomUUID(), null, "{}", Map.of()));

            assertEquals(List.of("PaymentConfirmed published for Work Order " + BillingFixtures.WORK_ORDER_ID + "."),
                    lines.messages());
        }
    }

    @Test
    void omitsTheCorrelationAttributeWhenThereIsNoTraceId() {
        gateway.publish(new OutboundMessage(UUID.randomUUID(), "BudgetCanceled", 1,
                BillingFixtures.WORK_ORDER_ID, UUID.randomUUID(), "  ", "{}", Map.of()));

        Map<String, MessageAttributeValue> attributes = capture().messageAttributes();
        assertEquals(3, attributes.size());
        assertFalse(attributes.containsKey("correlationId"));
    }

    @Test
    void publishesTheTraceHeadersAsStringAttributes() {
        gateway.publish(new OutboundMessage(UUID.randomUUID(), "BudgetCreated", 1,
                BillingFixtures.WORK_ORDER_ID, UUID.randomUUID(), "EVIDENCIA-B8-001", "{}",
                Map.of("traceparent", "00-trace-span-01", "tracestate", "nr=state", "newrelic", "payload")));

        Map<String, MessageAttributeValue> attributes = capture().messageAttributes();
        assertEquals(7, attributes.size());
        assertEquals("00-trace-span-01", attributes.get("traceparent").stringValue());
        assertEquals("String", attributes.get("traceparent").dataType());
        assertEquals("nr=state", attributes.get("tracestate").stringValue());
        assertEquals("payload", attributes.get("newrelic").stringValue());
    }

    private PublishRequest capture() {
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        return captor.getValue();
    }
}
