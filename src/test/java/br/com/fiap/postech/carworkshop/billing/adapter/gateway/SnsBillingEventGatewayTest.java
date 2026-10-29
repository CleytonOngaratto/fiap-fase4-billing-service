package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

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
                BillingFixtures.WORK_ORDER_ID, sagaId, "EVIDENCIA-B5-001", "{}"));

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

    @Test
    void omitsTheCorrelationAttributeWhenThereIsNoTraceId() {
        gateway.publish(new OutboundMessage(UUID.randomUUID(), "BudgetCanceled", 1,
                BillingFixtures.WORK_ORDER_ID, UUID.randomUUID(), "  ", "{}"));

        Map<String, MessageAttributeValue> attributes = capture().messageAttributes();
        assertEquals(3, attributes.size());
        assertFalse(attributes.containsKey("correlationId"));
    }

    private PublishRequest capture() {
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        return captor.getValue();
    }
}
