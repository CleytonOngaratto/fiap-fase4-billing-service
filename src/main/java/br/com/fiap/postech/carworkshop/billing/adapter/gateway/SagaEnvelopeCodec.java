package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RefundPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RequestPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SagaEnvelopeCodec {

    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    public static final String FIELD_SAGA_ID = "sagaId";
    public static final String FIELD_WORK_ORDER_ID = "workOrderId";
    public static final String FIELD_BRANCH_ID = "branchId";
    public static final String FIELD_CORRELATION_ID = "correlationId";
    public static final String FIELD_CAUSATION_ID = "causationId";
    public static final String FIELD_PAYLOAD = "payload";

    @Inject
    ObjectMapper objectMapper;

    public JsonNode decode(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Saga message body is not valid JSON.", e);
        }
    }

    public String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event payload could not be serialized.", e);
        }
    }

    public String encode(SagaOutboxJpaEntity row) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put(FIELD_MESSAGE_ID, row.messageId.toString());
        envelope.put(FIELD_TYPE, row.type);
        envelope.put(FIELD_VERSION, row.version);
        envelope.put(FIELD_OCCURRED_AT, row.occurredAt.toString());
        envelope.put(FIELD_SAGA_ID, row.sagaId.toString());
        envelope.put(FIELD_WORK_ORDER_ID, row.workOrderId);
        putIfPresent(envelope, FIELD_BRANCH_ID, row.branchId);
        putIfPresent(envelope, FIELD_CORRELATION_ID, row.correlationId);
        putIfPresent(envelope, FIELD_CAUSATION_ID, row.causationId == null ? null
                : row.causationId.toString());
        envelope.set(FIELD_PAYLOAD, decode(row.payload));
        return envelope.toString();
    }

    public SagaEnvelope envelopeOf(JsonNode node) {
        return new SagaEnvelope(
                uuid(node, FIELD_MESSAGE_ID),
                text(node, FIELD_TYPE),
                node.path(FIELD_VERSION).asInt(SagaEnvelope.CURRENT_VERSION),
                instant(node, FIELD_OCCURRED_AT),
                uuid(node, FIELD_SAGA_ID),
                node.path(FIELD_WORK_ORDER_ID).asLong(),
                text(node, FIELD_BRANCH_ID),
                text(node, FIELD_CORRELATION_ID),
                uuid(node, FIELD_CAUSATION_ID));
    }

    public CreateBudgetCommand toCreateBudget(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new CreateBudgetCommand(envelopeOf(node), text(payload, "customerDocument"),
                items(payload.path("items")), decimal(payload, "total"),
                instant(payload, "approvalDeadline"));
    }

    public RequestPaymentCommand toRequestPayment(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new RequestPaymentCommand(envelopeOf(node), text(payload, "budgetId"),
                decimal(payload, "amount"), instant(payload, "paymentDeadline"));
    }

    public CancelBudgetCommand toCancelBudget(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new CancelBudgetCommand(envelopeOf(node), text(payload, "budgetId"),
                text(payload, "reason"));
    }

    public CancelPaymentCommand toCancelPayment(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new CancelPaymentCommand(envelopeOf(node), text(payload, "budgetId"),
                text(payload, "reason"));
    }

    public RefundPaymentCommand toRefundPayment(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new RefundPaymentCommand(envelopeOf(node), text(payload, "paymentId"),
                text(payload, "reason"));
    }

    private static List<BudgetItem> items(JsonNode node) {
        List<BudgetItem> items = new ArrayList<>();
        if (!node.isArray()) {
            return items;
        }
        for (JsonNode item : node) {
            items.add(new BudgetItem(text(item, "type"), item.path("catalogId").asLong(),
                    text(item, "description"), item.path("quantity").asInt(1),
                    decimal(item, "unitPrice")));
        }
        return items;
    }

    private static void putIfPresent(ObjectNode envelope, String field, String value) {
        if (value != null && !value.isBlank()) {
            envelope.put(field, value);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
