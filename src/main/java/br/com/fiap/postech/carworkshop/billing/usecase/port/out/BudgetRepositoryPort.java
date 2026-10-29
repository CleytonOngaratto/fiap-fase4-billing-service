package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepositoryPort {

    Budget save(Budget budget);

    Optional<Budget> findById(UUID id);

    Optional<Budget> findByWorkOrderId(Long workOrderId);
}
