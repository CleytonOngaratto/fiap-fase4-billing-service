package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetJpaEntity;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.BudgetPanacheRepository;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetRepositoryGatewayTest {

    private BudgetPanacheRepository repository;
    private BudgetRepositoryGateway gateway;

    @BeforeEach
    void setUp() {
        repository = mock(BudgetPanacheRepository.class);
        gateway = new BudgetRepositoryGateway();
        gateway.repository = repository;
    }

    @Test
    void persistsABudgetThatIsNotInTheDatabaseYet() {
        Budget budget = BillingFixtures.budget();
        when(repository.findById(budget.getId())).thenReturn(null);

        assertSame(budget, gateway.save(budget));

        ArgumentCaptor<BudgetJpaEntity> captor = ArgumentCaptor.forClass(BudgetJpaEntity.class);
        verify(repository).persist(captor.capture());
        assertEquals(budget.getId(), captor.getValue().id);
        assertEquals(budget.getWorkOrderId(), captor.getValue().workOrderId);
    }

    // Entidade gerenciada nao se persiste de novo: o copyInto no objeto que o Hibernate ja conhece e o que
    // faz o UPDATE sair no flush.
    @Test
    void updatesTheManagedEntityWithoutPersistingItAgain() {
        Budget budget = BillingFixtures.budget();
        BudgetJpaEntity managed = new BudgetJpaEntity();
        when(repository.findById(budget.getId())).thenReturn(managed);

        gateway.save(budget);

        verify(repository, never()).persist(any(BudgetJpaEntity.class));
        assertEquals(budget.getStatus().name(), managed.status);
    }

    @Test
    void findsTheBudgetByIdAndByWorkOrder() {
        Budget budget = BillingFixtures.budget();
        BudgetJpaEntity entity = new BudgetJpaEntity();
        BudgetMapper.copyInto(budget, entity);
        when(repository.findById(budget.getId())).thenReturn(entity);
        when(repository.findByWorkOrderId(BillingFixtures.WORK_ORDER_ID)).thenReturn(entity);

        assertEquals(budget.getId(), gateway.findById(budget.getId()).orElseThrow().getId());
        assertEquals(budget.getId(),
                gateway.findByWorkOrderId(BillingFixtures.WORK_ORDER_ID).orElseThrow().getId());
    }

    @Test
    void answersEmptyWhenThereIsNoBudget() {
        when(repository.findById(any(UUID.class))).thenReturn(null);
        when(repository.findByWorkOrderId(99L)).thenReturn(null);

        assertTrue(gateway.findById(UUID.randomUUID()).isEmpty());
        assertEquals(Optional.empty(), gateway.findByWorkOrderId(99L));
    }
}
