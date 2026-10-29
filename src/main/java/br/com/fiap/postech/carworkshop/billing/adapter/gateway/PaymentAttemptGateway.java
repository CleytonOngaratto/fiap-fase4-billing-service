package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentAttempt;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentAttemptJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentAttemptPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentAttemptPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PaymentAttemptGateway implements PaymentAttemptPort {

    private static final String UNKNOWN_DETAIL = "unknown";

    @Inject
    PaymentAttemptPanacheRepository repository;

    // O coalesce existe porque NULL e distinto de NULL numa constraint UNIQUE do PostgreSQL e porque
    // "status_detail = NULL" nunca e verdadeiro: sem ele a deduplicacao furaria nos dois caminhos.
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean recordIfNew(PaymentAttempt attempt) {
        String statusDetail = attempt.statusDetail() == null ? UNKNOWN_DETAIL : attempt.statusDetail();
        if (repository.findNotification(attempt.paymentId(), attempt.mpPaymentId(), statusDetail) != null) {
            return false;
        }
        PaymentAttemptJpaEntity entity = new PaymentAttemptJpaEntity();
        entity.paymentId = attempt.paymentId();
        entity.mpPaymentId = attempt.mpPaymentId();
        entity.statusDetail = statusDetail;
        entity.occurredAt = attempt.occurredAt();
        repository.persist(entity);
        return true;
    }
}
