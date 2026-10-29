package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.BillingEventPublisher;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.OutboundMessage;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTest {

    private SagaOutboxPanacheRepository repository;
    private BillingEventPublisher publisher;
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        repository = mock(SagaOutboxPanacheRepository.class);
        publisher = mock(BillingEventPublisher.class);
        SagaEnvelopeCodec codec = TestCodec.codec();
        relay = new OutboxRelayService();
        relay.repository = repository;
        relay.codec = codec;
        relay.publisher = publisher;
        relay.clock = Clock.fixed(BillingFixtures.NOW, ZoneOffset.UTC);
        relay.batchSize = 10;
    }

    // O scheduler esta desligado no perfil de teste, entao o metodo e chamado direto e o teste afirma o
    // efeito: envelope publicado e sent_at marcado.
    @Test
    void publishesThePendingRowAndMarksItAsSent() {
        SagaOutboxJpaEntity row = row();
        when(repository.claimPending(10)).thenReturn(List.of(row));

        relay.drain();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(publisher).publish(captor.capture());
        OutboundMessage message = captor.getValue();
        assertEquals(row.messageId, message.messageId());
        assertEquals("BudgetCreated", message.type());
        assertEquals(1, message.version());
        assertEquals(BillingFixtures.WORK_ORDER_ID, message.workOrderId());
        assertEquals(row.sagaId, message.sagaId());
        assertEquals("EVIDENCIA-B5-001", message.correlationId());
        assertEquals("b-1", codecNode(message.body()));
        assertEquals(BillingFixtures.NOW, row.sentAt);
    }

    @Test
    void publishesNothingWhenTheOutboxIsEmpty() {
        when(repository.claimPending(10)).thenReturn(List.of());

        relay.drain();

        verifyNoInteractions(publisher);
    }

    // Publicacao que falha nao pode deixar sent_at marcado: a excecao sobe e a transacao do relay
    // desfaz a marca, senao a mensagem fica como enviada sem nunca ter saido.
    @Test
    void leavesTheRowPendingWhenThePublishFails() {
        SagaOutboxJpaEntity row = row();
        when(repository.claimPending(10)).thenReturn(List.of(row));
        doThrow(new IllegalStateException("sns down")).when(publisher).publish(any());

        assertThrows(IllegalStateException.class, () -> relay.drain());

        assertNull(row.sentAt);
    }

    private String codecNode(String body) {
        return TestCodec.codec().decode(body).path("payload").path("budgetId").asText();
    }

    private static SagaOutboxJpaEntity row() {
        SagaOutboxJpaEntity row = new SagaOutboxJpaEntity();
        row.messageId = UUID.randomUUID();
        row.type = "BudgetCreated";
        row.version = 1;
        row.occurredAt = Instant.parse("2026-10-25T11:59:00Z");
        row.sagaId = UUID.randomUUID();
        row.workOrderId = BillingFixtures.WORK_ORDER_ID;
        row.branchId = BillingFixtures.BRANCH;
        row.correlationId = "EVIDENCIA-B5-001";
        row.payload = "{\"budgetId\":\"b-1\"}";
        return row;
    }
}
