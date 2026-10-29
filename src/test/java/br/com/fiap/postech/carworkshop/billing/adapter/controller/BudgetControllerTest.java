package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
@TestHTTPEndpoint(BudgetController.class)
class BudgetControllerTest {

    private static final AtomicLong WORK_ORDER_SEQUENCE = new AtomicLong(7000L);

    @Inject
    BudgetUseCase useCase;

    @Inject
    BudgetRepositoryPort budgets;

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.OWNER))
    void theOwnerSeesTheBudgetWithItsItems() {
        UUID id = budgetFor(withinDeadline());

        given().when().get("/{id}", id).then()
                .statusCode(200)
                .body("id", is(id.toString()))
                .body("status", is("PENDING_APPROVAL"))
                .body("items.size()", is(2))
                .body("total", notNullValue());
    }

    @Test
    @TestSecurity(user = "intruder", roles = "CUSTOMER")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.INTRUDER))
    void anotherCustomerIsRefused() {
        UUID id = budgetFor(withinDeadline());

        given().when().get("/{id}", id).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.INTRUDER))
    void anAdminSeesAnyBudget() {
        UUID id = budgetFor(withinDeadline());

        given().when().get("/{id}", id).then().statusCode(200).body("id", is(id.toString()));
    }

    @Test
    void withoutATokenNothingIsVisible() {
        given().when().get("/{id}", UUID.randomUUID()).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.OWNER))
    void approvingAndRejectingAnswerAccepted() {
        UUID approved = budgetFor(withinDeadline());
        UUID rejected = budgetFor(withinDeadline());

        given().header("X-Trace-Id", "EVIDENCIA-B5-001")
                .when().post("/{id}/approve", approved).then().statusCode(202);
        given().header("X-Trace-Id", "EVIDENCIA-B5-002")
                .when().post("/{id}/reject", rejected).then().statusCode(202);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.OWNER))
    void approvingAfterTheDeadlineIsRefused() {
        UUID id = budgetFor(Instant.parse("2020-01-01T00:00:00Z"));

        given().when().post("/{id}/approve", id).then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    @JwtSecurity(claims = @Claim(key = "cpf", value = BillingFixtures.OWNER))
    void aBudgetThatDoesNotExistIsNotFound() {
        given().when().get("/{id}", UUID.randomUUID()).then().statusCode(404);
    }

    private UUID budgetFor(Instant approvalDeadline) {
        long workOrderId = WORK_ORDER_SEQUENCE.incrementAndGet();
        UUID[] id = new UUID[1];
        QuarkusTransaction.requiringNew().run(() -> {
            useCase.create(new CreateBudgetCommand(
                    new SagaEnvelope(UUID.randomUUID(), BillingMessageType.CREATE_BUDGET,
                            SagaEnvelope.CURRENT_VERSION, BillingFixtures.NOW, UUID.randomUUID(),
                            workOrderId, BillingFixtures.BRANCH, "EVIDENCIA-B5-001", null),
                    BillingFixtures.OWNER, items(), BillingFixtures.total(), approvalDeadline));
            id[0] = budgets.findByWorkOrderId(workOrderId).orElseThrow().getId();
        });
        return id[0];
    }

    // O prazo sai do relogio real e nao do NOW do fixture: sob @QuarkusTest o Clock produzido e o
    // do sistema, e um prazo fixo no passado faria o approve devolver 409 pelo motivo certo.
    private static Instant withinDeadline() {
        return Instant.now().plusSeconds(3600);
    }

    private static List<BudgetItem> items() {
        return List.of(
                new BudgetItem(BudgetItem.SERVICE, 1L, "Troca de oleo", 1, new BigDecimal("150.00")),
                new BudgetItem(BudgetItem.PART, 7L, "Filtro de oleo", 3, new BigDecimal("35.90")));
    }
}
