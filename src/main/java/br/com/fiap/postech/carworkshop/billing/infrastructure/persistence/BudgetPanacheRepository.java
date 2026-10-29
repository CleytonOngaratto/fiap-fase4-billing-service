package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class BudgetPanacheRepository implements PanacheRepositoryBase<BudgetJpaEntity, UUID> {

    public BudgetJpaEntity findByWorkOrderId(Long workOrderId) {
        return find("workOrderId", workOrderId).firstResult();
    }
}
