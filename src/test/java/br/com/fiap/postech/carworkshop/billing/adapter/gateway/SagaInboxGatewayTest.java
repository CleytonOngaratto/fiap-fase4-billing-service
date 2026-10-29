package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaInboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaInboxPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaInboxGatewayTest {

    private SagaInboxPanacheRepository repository;
    private SagaInboxGateway gateway;

    @BeforeEach
    void setUp() {
        repository = mock(SagaInboxPanacheRepository.class);
        gateway = new SagaInboxGateway();
        gateway.repository = repository;
        gateway.clock = Clock.fixed(BillingFixtures.NOW, ZoneOffset.UTC);
    }

    @Test
    void recordsAMessageSeenForTheFirstTime() {
        UUID messageId = UUID.randomUUID();
        when(repository.findById(messageId)).thenReturn(null);

        assertTrue(gateway.recordIfNew(messageId, BillingFixtures.WORK_ORDER_ID, "CreateBudget"));

        ArgumentCaptor<SagaInboxJpaEntity> captor = ArgumentCaptor.forClass(SagaInboxJpaEntity.class);
        verify(repository).persist(captor.capture());
        SagaInboxJpaEntity entity = captor.getValue();
        assertEquals(messageId, entity.messageId);
        assertEquals(BillingFixtures.WORK_ORDER_ID, entity.workOrderId);
        assertEquals("CreateBudget", entity.type);
        assertEquals(BillingFixtures.NOW, entity.processedAt);
    }

    // Check-then-insert em vez de deixar a PK estourar: no PostgreSQL a violacao aborta a transacao inteira
    // e nao haveria como seguir nem responder.
    @Test
    void answersFalseForAMessageAlreadyRecorded() {
        UUID messageId = UUID.randomUUID();
        when(repository.findById(messageId)).thenReturn(new SagaInboxJpaEntity());

        assertFalse(gateway.recordIfNew(messageId, BillingFixtures.WORK_ORDER_ID, "CreateBudget"));

        verify(repository, never()).persist(any(SagaInboxJpaEntity.class));
    }
}
