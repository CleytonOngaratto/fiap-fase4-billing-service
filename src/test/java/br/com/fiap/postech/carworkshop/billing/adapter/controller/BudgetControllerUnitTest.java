package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.presenter.BudgetResponse;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetView;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetControllerUnitTest {

    private BudgetUseCase useCase;
    private SecurityIdentity identity;
    private BudgetController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(BudgetUseCase.class);
        identity = mock(SecurityIdentity.class);
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.<String>getClaim("cpf")).thenReturn(BillingFixtures.OWNER);
        controller = new BudgetController();
        controller.useCase = useCase;
        controller.jwt = jwt;
        controller.identity = identity;
    }

    @Test
    void showsTheBudgetOfTheCustomerWhoAsked() {
        Budget budget = BillingFixtures.budget();
        when(identity.hasRole("ADMIN")).thenReturn(false);
        when(useCase.findById(budget.getId(), BillingFixtures.OWNER, false))
                .thenReturn(new BudgetView(budget, null));

        BudgetResponse response = controller.findById(budget.getId());

        assertEquals(budget.getId().toString(), response.id());
    }

    @Test
    void asksAsStaffWhenTheCallerIsAnAdmin() {
        Budget budget = BillingFixtures.budget();
        when(identity.hasRole("ADMIN")).thenReturn(true);
        when(useCase.findById(budget.getId(), BillingFixtures.OWNER, true))
                .thenReturn(new BudgetView(budget, null));

        controller.findById(budget.getId());

        verify(useCase).findById(budget.getId(), BillingFixtures.OWNER, true);
    }

    // 202 e nao 200: a reserva de pecas e a cobranca sao assincronas e o link aparece no GET depois.
    @Test
    void answersAcceptedOnApproveAndOnRejectForwardingTheTraceId() {
        Budget budget = BillingFixtures.budget();

        assertEquals(202, controller.approve(budget.getId(), "EVIDENCIA-B5-001").getStatus());
        assertEquals(202, controller.reject(budget.getId(), "EVIDENCIA-B5-002").getStatus());

        verify(useCase).approve(budget.getId(), BillingFixtures.OWNER, "EVIDENCIA-B5-001");
        verify(useCase).reject(budget.getId(), BillingFixtures.OWNER, "EVIDENCIA-B5-002");
    }
}
