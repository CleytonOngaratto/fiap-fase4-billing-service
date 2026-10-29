package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.CreateBudgetCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RefundPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RequestPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.infrastructure.persistence.SagaOutboxJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaEnvelopeCodecTest {

    private static final String MESSAGE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SAGA_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CAUSATION_ID = "33333333-3333-3333-3333-333333333333";

    private SagaEnvelopeCodec codec;

    @BeforeEach
    void setUp() {
        codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
    }

    @Test
    void decodesAJsonBody() {
        assertEquals("CreateBudget", codec.decode("{\"type\":\"CreateBudget\"}").path("type").asText());
    }

    @Test
    void refusesABodyThatIsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not json"));
    }

    @Test
    void writesAPayloadAsJson() {
        assertEquals("[1,2]", codec.writePayload(List.of(1, 2)));
    }

    // java.lang.Object nao tem propriedade nenhuma e o Jackson recusa serializa-lo por padrao: e a forma
    // mais curta de provar que a falha de serializacao nao passa em silencio.
    @Test
    void refusesAPayloadThatCannotBeSerialized() {
        assertThrows(IllegalStateException.class, () -> codec.writePayload(new Object()));
    }

    @Test
    void encodesAnOutboxRowIntoTheEnvelopeShape() {
        SagaOutboxJpaEntity row = row();

        JsonNode envelope = codec.decode(codec.encode(row));

        assertEquals(MESSAGE_ID, envelope.path("messageId").asText());
        assertEquals("BudgetCreated", envelope.path("type").asText());
        assertEquals(1, envelope.path("version").asInt());
        assertEquals("2026-10-25T12:00:00Z", envelope.path("occurredAt").asText());
        assertEquals(SAGA_ID, envelope.path("sagaId").asText());
        assertEquals(42L, envelope.path("workOrderId").asLong());
        assertEquals("SP-01", envelope.path("branchId").asText());
        assertEquals("EVIDENCIA-B5-001", envelope.path("correlationId").asText());
        assertEquals(CAUSATION_ID, envelope.path("causationId").asText());
        assertEquals("b-1", envelope.path("payload").path("budgetId").asText());
    }

    @Test
    void omitsTheOptionalEnvelopeFieldsWhenTheyAreAbsent() {
        SagaOutboxJpaEntity row = row();
        row.branchId = null;
        row.correlationId = "  ";
        row.causationId = null;

        JsonNode envelope = codec.decode(codec.encode(row));

        assertFalse(envelope.has("branchId"));
        assertFalse(envelope.has("correlationId"));
        assertFalse(envelope.has("causationId"));
        assertTrue(envelope.has("payload"));
    }

    @Test
    void readsTheEnvelopeOfACommand() {
        SagaEnvelope envelope = codec.envelopeOf(createBudgetBody());

        assertEquals(UUID.fromString(MESSAGE_ID), envelope.messageId());
        assertEquals("CreateBudget", envelope.type());
        assertEquals(1, envelope.version());
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), envelope.occurredAt());
        assertEquals(UUID.fromString(SAGA_ID), envelope.sagaId());
        assertEquals(42L, envelope.workOrderId());
        assertEquals("SP-01", envelope.branchId());
        assertEquals("EVIDENCIA-B5-001", envelope.correlationId());
        assertEquals(UUID.fromString(CAUSATION_ID), envelope.causationId());
    }

    @Test
    void assumesTheCurrentVersionWhenTheEnvelopeHasNone() {
        JsonNode node = codec.decode("{\"type\":\"CreateBudget\"}");

        assertEquals(SagaEnvelope.CURRENT_VERSION, codec.envelopeOf(node).version());
    }

    @Test
    void readsMalformedIdentifiersAndDatesAsAbsent() {
        JsonNode node = codec.decode("""
                {
                  "messageId": "not-a-uuid",
                  "sagaId": "",
                  "occurredAt": "25/10/2026",
                  "branchId": "   ",
                  "payload": {}
                }
                """);

        SagaEnvelope envelope = codec.envelopeOf(node);

        assertNull(envelope.messageId());
        assertNull(envelope.sagaId());
        assertNull(envelope.occurredAt());
        assertNull(envelope.branchId());
    }

    @Test
    void readsACreateBudgetCommandWithItsItems() {
        CreateBudgetCommand command = codec.toCreateBudget(createBudgetBody());

        assertEquals("11144477735", command.customerDocument());
        assertEquals(0, new BigDecimal("257.70").compareTo(command.total()));
        assertEquals(Instant.parse("2026-10-25T13:00:00Z"), command.approvalDeadline());
        assertEquals(2, command.items().size());
        BudgetItem part = command.items().get(1);
        assertEquals(BudgetItem.PART, part.type());
        assertEquals(7L, part.catalogId());
        assertEquals("Filtro de oleo", part.description());
        assertEquals(3, part.quantity());
        assertEquals(0, new BigDecimal("35.90").compareTo(part.unitPrice()));
    }

    @Test
    void readsACreateBudgetCommandWithoutItemsAndWithoutTotal() {
        CreateBudgetCommand command = codec.toCreateBudget(codec.decode("""
                {"type": "CreateBudget", "payload": {"customerDocument": "11144477735"}}
                """));

        assertTrue(command.items().isEmpty());
        assertNull(command.total());
        assertNull(command.approvalDeadline());
    }

    @Test
    void readsARequestPaymentCommand() {
        RequestPaymentCommand command = codec.toRequestPayment(codec.decode("""
                {
                  "type": "RequestPayment",
                  "payload": {
                    "budgetId": "44444444-4444-4444-4444-444444444444",
                    "amount": 257.70,
                    "paymentDeadline": "2026-10-25T12:30:00Z"
                  }
                }
                """));

        assertEquals("44444444-4444-4444-4444-444444444444", command.budgetId());
        assertEquals(0, new BigDecimal("257.70").compareTo(command.amount()));
        assertEquals(Instant.parse("2026-10-25T12:30:00Z"), command.paymentDeadline());
    }

    @Test
    void readsTheCompensationCommands() {
        JsonNode budgetNode = codec.decode("""
                {"type": "CancelBudget", "payload": {"budgetId": "b-9", "reason": "REJECTED_BY_CUSTOMER"}}
                """);
        JsonNode paymentNode = codec.decode("""
                {"type": "RefundPayment", "payload": {"paymentId": "p-9", "reason": "REPAIR_FAILED"}}
                """);

        CancelBudgetCommand cancelBudget = codec.toCancelBudget(budgetNode);
        CancelPaymentCommand cancelPayment = codec.toCancelPayment(budgetNode);
        RefundPaymentCommand refund = codec.toRefundPayment(paymentNode);

        assertEquals("b-9", cancelBudget.budgetId());
        assertEquals("REJECTED_BY_CUSTOMER", cancelBudget.reason());
        assertEquals("b-9", cancelPayment.budgetId());
        assertEquals("REJECTED_BY_CUSTOMER", cancelPayment.reason());
        assertEquals("p-9", refund.paymentId());
        assertEquals("REPAIR_FAILED", refund.reason());
    }

    private SagaOutboxJpaEntity row() {
        SagaOutboxJpaEntity row = new SagaOutboxJpaEntity();
        row.messageId = UUID.fromString(MESSAGE_ID);
        row.type = "BudgetCreated";
        row.version = 1;
        row.occurredAt = Instant.parse("2026-10-25T12:00:00Z");
        row.sagaId = UUID.fromString(SAGA_ID);
        row.workOrderId = 42L;
        row.branchId = "SP-01";
        row.correlationId = "EVIDENCIA-B5-001";
        row.causationId = UUID.fromString(CAUSATION_ID);
        row.payload = "{\"budgetId\":\"b-1\"}";
        return row;
    }

    private JsonNode createBudgetBody() {
        return codec.decode("""
                {
                  "messageId": "11111111-1111-1111-1111-111111111111",
                  "type": "CreateBudget",
                  "version": 1,
                  "occurredAt": "2026-10-25T12:00:00Z",
                  "sagaId": "22222222-2222-2222-2222-222222222222",
                  "workOrderId": 42,
                  "branchId": "SP-01",
                  "correlationId": "EVIDENCIA-B5-001",
                  "causationId": "33333333-3333-3333-3333-333333333333",
                  "payload": {
                    "customerDocument": "11144477735",
                    "total": 257.70,
                    "approvalDeadline": "2026-10-25T13:00:00Z",
                    "items": [
                      {"type": "SERVICE", "catalogId": 1, "description": "Troca de oleo",
                       "quantity": 1, "unitPrice": 150.00},
                      {"type": "PART", "catalogId": 7, "description": "Filtro de oleo",
                       "quantity": 3, "unitPrice": 35.90}
                    ]
                  }
                }
                """);
    }
}
