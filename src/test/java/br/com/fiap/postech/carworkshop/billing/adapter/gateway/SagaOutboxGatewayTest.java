package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.BudgetCreatedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SagaOutboxGatewayTest {

    private SagaOutboxPanacheRepository repository;
    private SagaOutboxGateway gateway;

    @BeforeEach
    void setUp() {
        repository = mock(SagaOutboxPanacheRepository.class);
        SagaEnvelopeCodec codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
        gateway = new SagaOutboxGateway();
        gateway.repository = repository;
        gateway.codec = codec;
    }

    @Test
    void appendsTheEventWithTheWholeEnvelopeAndTheSerializedPayload() {
        SagaEnvelope cause = BillingFixtures.envelope(BillingMessageType.CREATE_BUDGET);
        BillingEvent event = BillingEvent.replyTo(cause, BillingMessageType.BUDGET_CREATED,
                new BudgetCreatedPayload("b-1", BillingFixtures.total()));

        gateway.append(event);

        ArgumentCaptor<SagaOutboxJpaEntity> captor = ArgumentCaptor.forClass(SagaOutboxJpaEntity.class);
        verify(repository).persist(captor.capture());
        SagaOutboxJpaEntity row = captor.getValue();
        assertEquals(event.envelope().messageId(), row.messageId);
        assertEquals(BillingMessageType.BUDGET_CREATED, row.type);
        assertEquals(SagaEnvelope.CURRENT_VERSION, row.version);
        assertEquals(event.envelope().occurredAt(), row.occurredAt);
        assertEquals(cause.sagaId(), row.sagaId);
        assertEquals(BillingFixtures.WORK_ORDER_ID, row.workOrderId);
        assertEquals(BillingFixtures.BRANCH, row.branchId);
        assertEquals("EVIDENCIA-B5-001", row.correlationId);
        assertEquals(cause.messageId(), row.causationId);
        assertEquals("{\"budgetId\":\"b-1\",\"total\":257.70}", row.payload);
    }
}
