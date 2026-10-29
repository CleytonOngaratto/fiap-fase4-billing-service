package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SagaOutboxGateway implements SagaOutboxPort {

    @Inject
    SagaOutboxPanacheRepository repository;

    @Inject
    SagaEnvelopeCodec codec;

    // MANDATORY: sem transacao ativa isto LANCA em vez de abrir uma propria, que e o que torna
    // "gravar o estado e publicar o evento" atomico de verdade.
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void append(BillingEvent event) {
        SagaEnvelope envelope = event.envelope();
        SagaOutboxJpaEntity row = new SagaOutboxJpaEntity();
        row.messageId = envelope.messageId();
        row.type = envelope.type();
        row.version = envelope.version();
        row.occurredAt = envelope.occurredAt();
        row.sagaId = envelope.sagaId();
        row.workOrderId = envelope.workOrderId();
        row.branchId = envelope.branchId();
        row.correlationId = envelope.correlationId();
        row.causationId = envelope.causationId();
        row.payload = codec.writePayload(event.payload());
        repository.persist(row);
    }
}
