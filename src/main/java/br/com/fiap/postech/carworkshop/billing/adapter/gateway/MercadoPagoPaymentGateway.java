package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.exception.PaymentGatewayException;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class MercadoPagoPaymentGateway implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentGateway.class);

    private static final DateTimeFormatter MP_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");
    private static final int NOT_CANCELABLE_CODE = 2018;
    private static final String CURRENCY = "BRL";

    private final String baseUrl;
    private final String accessToken;
    private final Duration timeout;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MercadoPagoPaymentGateway(String baseUrl, String accessToken, Duration timeout,
                                     int maxAttempts, Duration retryBackoff, HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PreferenceCreated createPreference(Budget budget, Payment payment) {
        JsonNode body = parse(send(request("/checkout/preferences")
                .POST(HttpRequest.BodyPublishers.ofString(preferenceBody(budget, payment)))
                .header("Content-Type", "application/json")));
        String preferenceId = text(body, "id");
        String initPoint = text(body, "init_point");
        if (preferenceId == null || initPoint == null) {
            throw new PaymentGatewayException(
                    "Mercado Pago returned a preference without id or init_point.");
        }
        return new PreferenceCreated(preferenceId, initPoint);
    }

    @Override
    public PaymentSnapshot getPayment(String mpPaymentId) {
        JsonNode body = parse(send(request("/v1/payments/" + mpPaymentId).GET()));
        return new PaymentSnapshot(
                text(body, "id"),
                text(body, "status"),
                text(body, "status_detail"),
                decimal(body, "transaction_amount"),
                text(body, "external_reference"));
    }

    @Override
    public CancellationOutcome cancelPayment(String mpPaymentId) {
        if (mpPaymentId == null || mpPaymentId.isBlank()) {
            return CancellationOutcome.NO_PAYMENT;
        }
        HttpResponse<String> response = sendAllowingRefusal(request("/v1/payments/" + mpPaymentId)
                .method("PUT", HttpRequest.BodyPublishers.ofString("{\"status\":\"cancelled\"}"))
                .header("Content-Type", "application/json"));
        if (isSuccessful(response)) {
            return CancellationOutcome.CANCELED;
        }
        if (hasCause(response, NOT_CANCELABLE_CODE)) {
            log.info("Payment {} is no longer cancelable at Mercado Pago (code {}).", mpPaymentId,
                    NOT_CANCELABLE_CODE);
            return CancellationOutcome.NOT_CANCELABLE;
        }
        log.warn("Mercado Pago refused to cancel payment {} with HTTP {}: compensation continues.",
                mpPaymentId, response.statusCode());
        return CancellationOutcome.REFUSED;
    }

    @Override
    public RefundCreated refundPayment(String mpPaymentId, UUID idempotencyKey) {
        JsonNode body = parse(send(request("/v1/payments/" + mpPaymentId + "/refunds")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", idempotencyKey.toString())));
        String refundId = text(body, "id");
        if (refundId == null) {
            throw new PaymentGatewayException("Mercado Pago returned a refund without id.");
        }
        return new RefundCreated(refundId);
    }

    private String preferenceBody(Budget budget, Payment payment) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode items = root.putArray("items");
        for (BudgetItem item : budget.getItems()) {
            ObjectNode node = items.addObject();
            node.put("id", item.type() + "-" + item.catalogId());
            node.put("title", item.description());
            node.put("quantity", item.quantity());
            node.put("currency_id", CURRENCY);
            node.put("unit_price", item.unitPrice());
        }
        root.put("external_reference", budget.getId().toString());
        root.put("expires", true);
        root.put("expiration_date_from",
                MP_DATE.format(OffsetDateTime.ofInstant(payment.getCreatedAt(), ZoneOffset.UTC)));
        root.put("expiration_date_to",
                MP_DATE.format(OffsetDateTime.ofInstant(payment.getPaymentDeadline(), ZoneOffset.UTC)));
        return root.toString();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + accessToken);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        HttpResponse<String> response = sendAllowingRefusal(builder);
        if (!isSuccessful(response)) {
            throw new PaymentGatewayException("Mercado Pago answered HTTP " + response.statusCode()
                    + " for " + response.uri().getPath() + ".");
        }
        return response;
    }

    // Retry so para 5xx e falha de transporte: um 4xx e resposta do Mercado Pago, nao instabilidade, e
    // retentar nao muda o resultado.
    private HttpResponse<String> sendAllowingRefusal(HttpRequest.Builder builder) {
        HttpRequest httpRequest = builder.build();
        PaymentGatewayException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 5) {
                    return response;
                }
                last = new PaymentGatewayException("Mercado Pago answered HTTP "
                        + response.statusCode() + " for " + httpRequest.uri().getPath() + ".");
            } catch (IOException e) {
                last = new PaymentGatewayException("Mercado Pago is unreachable: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentGatewayException("Mercado Pago call was interrupted.", e);
            }
            backoff(attempt);
        }
        throw last;
    }

    private void backoff(int attempt) {
        if (retryBackoff.isZero() || retryBackoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff.toMillis() * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException | RuntimeException e) {
            throw new PaymentGatewayException("Mercado Pago returned a malformed body.", e);
        }
    }

    private boolean hasCause(HttpResponse<String> response, int code) {
        try {
            JsonNode causes = objectMapper.readTree(response.body()).path("cause");
            for (JsonNode cause : causes) {
                if (cause.path("code").asInt() == code || code == asInt(cause.path("code"))) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Mercado Pago error body could not be parsed: {}", e.getMessage());
        }
        return false;
    }

    private static boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() / 100 == 2;
    }

    private static int asInt(JsonNode node) {
        return node.isTextual() ? safeParse(node.asText()) : node.asInt();
    }

    private static int safeParse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String asText = value.asText();
        return asText.isBlank() ? null : asText;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }
}
