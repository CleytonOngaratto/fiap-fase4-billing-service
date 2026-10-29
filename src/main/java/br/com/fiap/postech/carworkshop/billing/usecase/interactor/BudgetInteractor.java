package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.BudgetAnsweredPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.BudgetCanceledPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.BudgetCreatedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.EventPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetView;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class BudgetInteractor implements BudgetUseCase {

    private final BudgetRepositoryPort budgets;
    private final PaymentRepositoryPort payments;
    private final SagaOutboxPort outbox;
    private final Clock clock;

    public BudgetInteractor(BudgetRepositoryPort budgets, PaymentRepositoryPort payments,
                            SagaOutboxPort outbox, Clock clock) {
        this.budgets = budgets;
        this.payments = payments;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void create(CreateBudgetCommand command) {
        String refusal = refusalFor(command);
        if (refusal != null) {
            outbox.append(BillingEvent.replyTo(command.envelope(),
                    BillingMessageType.BUDGET_CREATION_FAILED, new FailurePayload(refusal)));
            return;
        }
        SagaEnvelope envelope = command.envelope();
        Budget budget = budgets.findByWorkOrderId(envelope.workOrderId())
                .orElseGet(() -> budgets.save(Budget.create(envelope.workOrderId(), envelope.sagaId(),
                        envelope.branchId(), command.customerDocument(), command.items(),
                        command.total(), command.approvalDeadline(), Instant.now(clock))));
        outbox.append(BillingEvent.replyTo(envelope, BillingMessageType.BUDGET_CREATED,
                new BudgetCreatedPayload(budget.getId().toString(), budget.getTotal())));
    }

    @Override
    public void cancel(CancelBudgetCommand command) {
        Budget budget = require(command.budgetId());
        if (budget.cancel(command.reason(), Instant.now(clock))) {
            budgets.save(budget);
        }
        outbox.append(BillingEvent.replyTo(command.envelope(), BillingMessageType.BUDGET_CANCELED,
                new BudgetCanceledPayload(budget.getId().toString())));
    }

    @Override
    public void approve(UUID budgetId, String customerDocument, String correlationId) {
        Budget budget = require(budgetId);
        budget.approve(customerDocument, Instant.now(clock));
        budgets.save(budget);
        outbox.append(event(budget, BillingMessageType.BUDGET_APPROVED, correlationId,
                new BudgetAnsweredPayload(budget.getId().toString(), customerDocument)));
    }

    @Override
    public void reject(UUID budgetId, String customerDocument, String correlationId) {
        Budget budget = require(budgetId);
        budget.reject(customerDocument, Instant.now(clock));
        budgets.save(budget);
        outbox.append(event(budget, BillingMessageType.BUDGET_REJECTED, correlationId,
                new BudgetAnsweredPayload(budget.getId().toString(), null)));
    }

    @Override
    public BudgetView findById(UUID budgetId, String customerDocument, boolean staff) {
        Budget budget = require(budgetId);
        if (!staff && !budget.isOwnedBy(customerDocument)) {
            throw new NotBudgetOwnerException("This budget belongs to another customer.");
        }
        Payment payment = payments.findByBudgetId(budgetId).orElse(null);
        return new BudgetView(budget, payment);
    }

    private String refusalFor(CreateBudgetCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            return "A budget needs at least one item.";
        }
        if (command.total() == null || command.total().compareTo(BigDecimal.ZERO) <= 0) {
            return "A budget needs a total greater than zero.";
        }
        if (command.approvalDeadline() == null) {
            return "A budget needs an approval deadline.";
        }
        return null;
    }

    private Budget require(String budgetId) {
        return require(parse(budgetId));
    }

    private Budget require(UUID budgetId) {
        return budgets.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("No budget with id " + budgetId + "."));
    }

    private static UUID parse(String budgetId) {
        try {
            return UUID.fromString(budgetId);
        } catch (RuntimeException e) {
            throw new BudgetNotFoundException("Budget id " + budgetId + " is not a valid identifier.");
        }
    }

    private BillingEvent event(Budget budget, String type, String correlationId,
                               EventPayload payload) {
        return new BillingEvent(SagaEnvelope.event(type, budget.getWorkOrderId(), budget.getSagaId(),
                budget.getBranchId(), correlationId, null, Instant.now(clock)), payload);
    }
}
