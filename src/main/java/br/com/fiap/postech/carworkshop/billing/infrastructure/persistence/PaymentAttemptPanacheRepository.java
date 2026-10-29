package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PaymentAttemptPanacheRepository
        implements PanacheRepositoryBase<PaymentAttemptJpaEntity, Long> {

    public PaymentAttemptJpaEntity findNotification(UUID paymentId, String mpPaymentId,
                                                    String statusDetail) {
        return find("paymentId = ?1 and mpPaymentId = ?2 and statusDetail = ?3", paymentId,
                mpPaymentId, statusDetail).firstResult();
    }
}
