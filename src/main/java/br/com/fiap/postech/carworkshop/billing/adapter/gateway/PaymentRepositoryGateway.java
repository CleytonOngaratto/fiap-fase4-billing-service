package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.PaymentPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaymentRepositoryGateway implements PaymentRepositoryPort {

    @Inject
    PaymentPanacheRepository repository;

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = repository.findById(payment.getId());
        if (entity == null) {
            entity = new PaymentJpaEntity();
            PaymentMapper.copyInto(payment, entity);
            repository.persist(entity);
        } else {
            PaymentMapper.copyInto(payment, entity);
        }
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(repository.findById(id)).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByBudgetId(UUID budgetId) {
        return Optional.ofNullable(repository.findByBudgetId(budgetId)).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByMpPaymentId(String mpPaymentId) {
        if (mpPaymentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(repository.findByMpPaymentId(mpPaymentId))
                .map(PaymentMapper::toDomain);
    }
}
