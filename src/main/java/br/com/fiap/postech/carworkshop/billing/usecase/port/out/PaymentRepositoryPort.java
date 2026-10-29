package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByBudgetId(UUID budgetId);

    Optional<Payment> findByMpPaymentId(String mpPaymentId);
}
