package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.BillingEventPublisher;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.OutboundMessage;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.TraceContext;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.TraceHeaders;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxPanacheRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private static final String IDENTITY = "billing-outbox-relay";
    private static final String TRANSACTION_CATEGORY = "Scheduled";

    @Inject
    SagaOutboxPanacheRepository repository;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    BillingEventPublisher publisher;

    @Inject
    TraceContext trace;

    @Inject
    Clock clock;

    @ConfigProperty(name = "billing.outbox.relay.batch-size")
    int batchSize;

    // void, nunca int: metodo agendado do Quarkus so aceita void ou CompletionStage, e o erro aponta uma
    // classe sem relacao nenhuma. O teste afirma o efeito (sent_at marcado, mensagem publicada).
    @Scheduled(every = "2s", identity = IDENTITY,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    public void drain() {
        List<SagaOutboxJpaEntity> pending = repository.claimPending(batchSize);
        // O agente (modulo ejb-4.0) abre uma transacao no @Transactional a cada ciclo, com o nome .../proceed.
        if (pending.isEmpty()) {
            trace.ignoreTransaction();
            return;
        }
        trace.nameTransaction(TRANSACTION_CATEGORY, IDENTITY);
        for (SagaOutboxJpaEntity row : pending) {
            try (LogContext ignored = LogContext.of(row.correlationId, row.sagaId, row.branchId)) {
                publisher.publish(messageFor(row));
                row.sentAt = Instant.now(clock);
                log.info("Outbox message {} sent for Work Order {}.", row.type, row.workOrderId);
            }
        }
    }

    private OutboundMessage messageFor(SagaOutboxJpaEntity row) {
        return new OutboundMessage(row.messageId, row.type, row.version, row.workOrderId, row.sagaId,
                row.correlationId, codec.encode(row), TraceHeaders.fromJson(row.traceContext));
    }
}
