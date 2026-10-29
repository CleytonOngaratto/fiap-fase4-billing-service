package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SagaOutboxPanacheRepository implements PanacheRepositoryBase<SagaOutboxJpaEntity, UUID> {

    private static final String CLAIM_PENDING = """
            SELECT * FROM saga_outbox
            WHERE sent_at IS NULL
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    // SKIP LOCKED permite varias replicas drenarem o outbox sem publicar a mesma mensagem duas vezes.
    public List<SagaOutboxJpaEntity> claimPending(int limit) {
        return getEntityManager().unwrap(Session.class)
                .createNativeQuery(CLAIM_PENDING, SagaOutboxJpaEntity.class)
                .setParameter("limit", limit)
                .getResultList();
    }
}
