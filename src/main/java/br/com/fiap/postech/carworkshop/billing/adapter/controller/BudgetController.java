package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.presenter.BudgetResponse;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/budgets")
@Tag(name = "Budgets")
@Produces(MediaType.APPLICATION_JSON)
public class BudgetController {

    private static final String ADMIN = "ADMIN";
    private static final String CUSTOMER = "CUSTOMER";

    @Inject
    BudgetUseCase useCase;

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/{id}")
    @RolesAllowed({CUSTOMER, ADMIN})
    @Transactional
    @Operation(summary = "Budget with its items and, once it exists, the payment link")
    public BudgetResponse findById(@PathParam("id") UUID id) {
        return BudgetResponse.from(useCase.findById(id, document(), identity.hasRole(ADMIN)));
    }

    // 202: a reserva de pecas e a cobranca sao assincronas, e o link de pagamento aparece no GET segundos
    // depois. Responder 200 com o link aqui exigiria cobrar antes de reservar.
    @POST
    @Path("/{id}/approve")
    @RolesAllowed(CUSTOMER)
    @Transactional
    @Operation(summary = "Customer approves the budget")
    public Response approve(@PathParam("id") UUID id,
                            @HeaderParam("X-Trace-Id") String correlationId) {
        useCase.approve(id, document(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/reject")
    @RolesAllowed(CUSTOMER)
    @Transactional
    @Operation(summary = "Customer rejects the budget")
    public Response reject(@PathParam("id") UUID id,
                           @HeaderParam("X-Trace-Id") String correlationId) {
        useCase.reject(id, document(), correlationId);
        return Response.accepted().build();
    }

    private String document() {
        return jwt.getClaim("cpf");
    }
}
