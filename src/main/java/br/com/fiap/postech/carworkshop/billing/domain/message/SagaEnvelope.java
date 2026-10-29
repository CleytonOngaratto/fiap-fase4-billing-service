package br.com.fiap.postech.carworkshop.billing.domain.message;

import java.time.Instant;
import java.util.UUID;

public record SagaEnvelope(UUID messageId, String type, int version, Instant occurredAt, UUID sagaId,
                           Long workOrderId, String branchId, String correlationId, UUID causationId) {

    public static final int CURRENT_VERSION = 1;

    public SagaEnvelope reply(String type) {
        return new SagaEnvelope(UUID.randomUUID(), type, CURRENT_VERSION, Instant.now(), sagaId,
                workOrderId, branchId, correlationId, messageId);
    }

    public static SagaEnvelope event(String type, Long workOrderId, UUID sagaId, String branchId,
                                     String correlationId, UUID causationId, Instant occurredAt) {
        return new SagaEnvelope(UUID.randomUUID(), type, CURRENT_VERSION, occurredAt, sagaId,
                workOrderId, branchId, correlationId, causationId);
    }
}
