package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationMetadataGatewayTest {

    @Test
    void exposesTheConfiguredNameAndVersion() {
        ApplicationMetadataGateway gateway = new ApplicationMetadataGateway();
        gateway.name = "billing-service";
        gateway.version = "1.2.3";

        assertEquals("billing-service", gateway.name());
        assertEquals("1.2.3", gateway.version());
    }
}
