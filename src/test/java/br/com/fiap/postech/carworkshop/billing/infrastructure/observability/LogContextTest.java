package br.com.fiap.postech.carworkshop.billing.infrastructure.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogContextTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void putsTheCorrelationAndSagaIdsInTheContextAndRemovesThemOnClose() {
        UUID sagaId = UUID.randomUUID();

        try (LogContext ignored = LogContext.of("EVIDENCIA-B5-001", sagaId)) {
            assertEquals("EVIDENCIA-B5-001", MDC.get(LogContext.CORRELATION_ID));
            assertEquals(sagaId.toString(), MDC.get(LogContext.SAGA_ID));
        }

        assertNull(MDC.get(LogContext.CORRELATION_ID));
        assertNull(MDC.get(LogContext.SAGA_ID));
    }

    @Test
    void doesNotPutAbsentValuesInTheContext() {
        try (LogContext ignored = LogContext.of("   ", null)) {
            assertNull(MDC.get(LogContext.CORRELATION_ID));
            assertNull(MDC.get(LogContext.SAGA_ID));
        }
    }
}
