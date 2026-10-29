package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetStatus;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetView;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetInteractorTest {

    private BudgetRepositoryPort budgets;
    private PaymentRepositoryPort payments;
    private SagaOutboxPort outbox;
    private BudgetUseCase useCase;

    @BeforeEach
    void setUp() {
        budgets = mock(BudgetRepositoryPort.class);
        payments = mock(PaymentRepositoryPort.class);
        outbox = mock(SagaOutboxPort.class);
        when(budgets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new BudgetInteractor(budgets, payments, outbox,
                Clock.fixed(BillingFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTheBudgetAndAnswersBudgetCreated() {
        when(budgets.findByWorkOrderId(BillingFixtures.WORK_ORDER_ID)).thenReturn(Optional.empty());

        useCase.create(createCommand(BillingFixtures.total()));

        verify(budgets).save(any());
        assertEquals(BillingMessageType.BUDGET_CREATED, captureEvent().envelope().type());
    }

    // Idempotente pela chave de negocio: o mesmo workOrderId nao cria um segundo orcamento, mas a resposta
    // sai de novo, porque quem decide se a mensagem ja foi tratada e a inbox, nao este caso de uso.
    @Test
    void reusesTheBudgetOfTheSameWorkOrderAndStillAnswers() {
        when(budgets.findByWorkOrderId(BillingFixtures.WORK_ORDER_ID))
                .thenReturn(Optional.of(BillingFixtures.budget()));

        useCase.create(createCommand(BillingFixtures.total()));

        verify(budgets, never()).save(any());
        assertEquals(BillingMessageType.BUDGET_CREATED, captureEvent().envelope().type());
    }

    @Test
    void refusesToCreateABudgetWithoutItems() {
        useCase.create(new CreateBudgetCommand(
                BillingFixtures.envelope(BillingMessageType.CREATE_BUDGET), BillingFixtures.OWNER,
                List.of(), BillingFixtures.total(), BillingFixtures.NOW.plusSeconds(3600)));

        verify(budgets, never()).save(any());
        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.BUDGET_CREATION_FAILED, event.envelope().type());
        assertEquals("A budget needs at least one item.",
                ((FailurePayload) event.payload()).reason());
    }

    @Test
    void refusesToCreateABudgetWithoutATotal() {
        useCase.create(createCommand(BigDecimal.ZERO));

        assertEquals(BillingMessageType.BUDGET_CREATION_FAILED, captureEvent().envelope().type());
    }

    @Test
    void cancelsTheBudgetAndAnswersBudgetCanceled() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));

        useCase.cancel(cancelCommand(budget.getId().toString()));

        assertEquals(BudgetStatus.CANCELED, budget.getStatus());
        verify(budgets).save(budget);
        assertEquals(BillingMessageType.BUDGET_CANCELED, captureEvent().envelope().type());
    }

    // Comando reentregue com o estado ja final responde de novo: suprimir a resposta aqui deixaria o
    // orquestrador esperando a confirmacao da compensacao para sempre.
    @Test
    void answersBudgetCanceledEvenWhenAlreadyCanceled() {
        Budget budget = BillingFixtures.budget();
        budget.cancel("APPROVAL_EXPIRED", BillingFixtures.NOW);
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));

        useCase.cancel(cancelCommand(budget.getId().toString()));

        verify(budgets, never()).save(any());
        assertEquals(BillingMessageType.BUDGET_CANCELED, captureEvent().envelope().type());
    }

    @Test
    void failsWhenTheBudgetOfTheCommandDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(budgets.findById(unknown)).thenReturn(Optional.empty());
        CancelBudgetCommand command = cancelCommand(unknown.toString());

        assertThrows(BudgetNotFoundException.class, () -> useCase.cancel(command));
    }

    @Test
    void failsWhenTheBudgetIdIsNotAnIdentifier() {
        CancelBudgetCommand command = cancelCommand("not-a-uuid");

        assertThrows(BudgetNotFoundException.class, () -> useCase.cancel(command));
    }

    @Test
    void approvesAndAnswersBudgetApprovedWithTheCorrelationId() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));

        useCase.approve(budget.getId(), BillingFixtures.OWNER, "EVIDENCIA-B5-002");

        assertEquals(BudgetStatus.APPROVED, budget.getStatus());
        BillingEvent event = captureEvent();
        assertEquals(BillingMessageType.BUDGET_APPROVED, event.envelope().type());
        assertEquals("EVIDENCIA-B5-002", event.envelope().correlationId());
    }

    @Test
    void rejectsAndAnswersBudgetRejected() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));

        useCase.reject(budget.getId(), BillingFixtures.OWNER, null);

        assertEquals(BudgetStatus.REJECTED, budget.getStatus());
        assertEquals(BillingMessageType.BUDGET_REJECTED, captureEvent().envelope().type());
    }

    @Test
    void readsTheBudgetForTheOwnerWithItsPayment() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));
        when(payments.findByBudgetId(budget.getId()))
                .thenReturn(Optional.of(BillingFixtures.payment(budget.getId())));

        BudgetView view = useCase.findById(budget.getId(), BillingFixtures.OWNER, false);

        assertSame(budget, view.budget());
        assertEquals(budget.getId(), view.payment().getBudgetId());
    }

    @Test
    void readsTheBudgetForStaffWithoutOwnershipCheck() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));
        when(payments.findByBudgetId(budget.getId())).thenReturn(Optional.empty());

        BudgetView view = useCase.findById(budget.getId(), null, true);

        assertSame(budget, view.budget());
        assertNull(view.payment());
    }

    @Test
    void refusesToReadTheBudgetOfAnotherCustomer() {
        Budget budget = BillingFixtures.budget();
        when(budgets.findById(budget.getId())).thenReturn(Optional.of(budget));
        UUID id = budget.getId();

        assertThrows(NotBudgetOwnerException.class,
                () -> useCase.findById(id, BillingFixtures.INTRUDER, false));
    }

    private CreateBudgetCommand createCommand(BigDecimal total) {
        return new CreateBudgetCommand(BillingFixtures.envelope(BillingMessageType.CREATE_BUDGET),
                BillingFixtures.OWNER, BillingFixtures.items(), total,
                BillingFixtures.NOW.plusSeconds(3600));
    }

    private CancelBudgetCommand cancelCommand(String budgetId) {
        return new CancelBudgetCommand(BillingFixtures.envelope(BillingMessageType.CANCEL_BUDGET),
                budgetId, "APPROVAL_EXPIRED");
    }

    private BillingEvent captureEvent() {
        ArgumentCaptor<BillingEvent> captor = ArgumentCaptor.forClass(BillingEvent.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }
}
