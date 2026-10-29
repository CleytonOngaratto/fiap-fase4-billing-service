package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaInboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaInboxPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaInboxPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SagaInboxGateway implements SagaInboxPort {

    @Inject
    SagaInboxPanacheRepository repository;

    @Inject
    Clock clock;

    // MANDATORY: a marca de "processada" so vale se for gravada na mesma transacao do efeito da mensagem.
    // Check-then-insert em vez de deixar a PK estourar: no PostgreSQL um INSERT que viola constraint aborta a
    // transacao inteira, e dai nao ha como seguir nem compensar.
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean recordIfNew(UUID messageId, Long workOrderId, String type) {
        if (repository.findById(messageId) != null) {
            return false;
        }
        SagaInboxJpaEntity entity = new SagaInboxJpaEntity();
        entity.messageId = messageId;
        entity.workOrderId = workOrderId;
        entity.type = type;
        entity.processedAt = Instant.now(clock);
        repository.persist(entity);
        return true;
    }
}
