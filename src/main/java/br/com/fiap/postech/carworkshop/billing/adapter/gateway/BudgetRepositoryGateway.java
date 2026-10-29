package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BudgetRepositoryGateway implements BudgetRepositoryPort {

    @Inject
    BudgetPanacheRepository repository;

    // MANDATORY: a gravacao do orcamento e a do outbox tem de cair na MESMA transacao, senao a resposta ao
    // orquestrador pode sair sem o estado ter sido gravado.
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public Budget save(Budget budget) {
        BudgetJpaEntity entity = repository.findById(budget.getId());
        if (entity == null) {
            entity = new BudgetJpaEntity();
            BudgetMapper.copyInto(budget, entity);
            repository.persist(entity);
        } else {
            BudgetMapper.copyInto(budget, entity);
        }
        return budget;
    }

    @Override
    public Optional<Budget> findById(UUID id) {
        return Optional.ofNullable(repository.findById(id)).map(BudgetMapper::toDomain);
    }

    @Override
    public Optional<Budget> findByWorkOrderId(Long workOrderId) {
        return Optional.ofNullable(repository.findByWorkOrderId(workOrderId))
                .map(BudgetMapper::toDomain);
    }
}
