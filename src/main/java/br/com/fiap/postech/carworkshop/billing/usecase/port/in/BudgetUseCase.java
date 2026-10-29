package br.com.fiap.postech.carworkshop.billing.usecase.port.in;

import br.com.fiap.postech.carworkshop.billing.domain.message.CancelBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;

import java.util.UUID;

public interface BudgetUseCase {

    void create(CreateBudgetCommand command);

    void cancel(CancelBudgetCommand command);

    void approve(UUID budgetId, String customerDocument, String correlationId);

    void reject(UUID budgetId, String customerDocument, String correlationId);

    BudgetView findById(UUID budgetId, String customerDocument, boolean staff);
}
