package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class SagaInboxPanacheRepository implements PanacheRepositoryBase<SagaInboxJpaEntity, UUID> {
}
