package br.com.fiap.postech.carworkshop.billing.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceInfoTest {

    @Test
    void keepsNameAndVersion() {
        ServiceInfo info = new ServiceInfo("billing-service", "1.0.0");

        assertEquals("billing-service", info.name());
        assertEquals("1.0.0", info.version());
    }

    @Test
    void rejectsMissingNameOrVersion() {
        assertThrows(NullPointerException.class, () -> new ServiceInfo(null, "1.0.0"));
        assertThrows(NullPointerException.class, () -> new ServiceInfo("billing-service", null));
    }
}
