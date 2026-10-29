package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PaymentPanacheRepository implements PanacheRepositoryBase<PaymentJpaEntity, UUID> {

    public PaymentJpaEntity findByBudgetId(UUID budgetId) {
        return find("budgetId", budgetId).firstResult();
    }

    public PaymentJpaEntity findByMpPaymentId(String mpPaymentId) {
        return find("mpPaymentId", mpPaymentId).firstResult();
    }
}
