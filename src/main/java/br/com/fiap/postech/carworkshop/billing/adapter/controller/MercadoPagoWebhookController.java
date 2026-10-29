package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.MercadoPagoSignatureVerifier;
import br.com.fiap.postech.carworkshop.billing.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentNotificationUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/webhooks/mercadopago")
@Tag(name = "Webhooks")
@Consumes(MediaType.APPLICATION_JSON)
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private static final String PAYMENT_TOPIC = "payment";

    @Inject
    PaymentNotificationUseCase useCase;

    @Inject
    MercadoPagoSignatureVerifier verifier;

    // Publico por desenho: nao existe authorizer no Gateway e o Mercado Pago nao manda JWT. TODA a protecao
    // desta rota e a assinatura x-signature, comparada em tempo constante e negando quando falta o segredo.
    @POST
    @PermitAll
    @Transactional
    @Operation(summary = "Payment notification from Mercado Pago")
    public Response notify(JsonNode body,
                           @QueryParam("type") String queryType,
                           @QueryParam("topic") String queryTopic,
                           @QueryParam("data.id") String queryDataId,
                           @HeaderParam("x-signature") String signature,
                           @HeaderParam("x-request-id") String requestId) {
        String topic = first(queryType, queryTopic, text(body, "type"));
        String dataId = first(queryDataId, dataIdOf(body));

        if (!verifier.isValid(signature, dataId, requestId)) {
            log.warn("Notification {} arrived with an invalid signature: refused.", dataId);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Topico que nao e de pagamento sai com 200: qualquer outra coisa faz o Mercado Pago reentregar
        // para sempre, e o painel manda merchant_order em alguns fluxos.
        if (!PAYMENT_TOPIC.equals(topic)) {
            log.info("Notification topic {} is not handled by this service: acknowledged.", topic);
            return Response.ok().build();
        }

        try (LogContext ignored = LogContext.of(dataId, null)) {
            useCase.onNotification(dataId);
        }
        return Response.ok().build();
    }

    private static String dataIdOf(JsonNode body) {
        return body == null ? null : text(body.path("data"), "id");
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
