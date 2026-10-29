package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggingBillingEventGatewayTest {

    @Test
    void acceptsTheMessageWithoutTouchingTheNetwork() {
        LoggingBillingEventGateway gateway = new LoggingBillingEventGateway();
        OutboundMessage message = new OutboundMessage(UUID.randomUUID(), "BudgetCreated", 1,
                BillingFixtures.WORK_ORDER_ID, UUID.randomUUID(), "EVIDENCIA-B5-001", "{}");

        assertDoesNotThrow(() -> gateway.publish(message));
    }
}
