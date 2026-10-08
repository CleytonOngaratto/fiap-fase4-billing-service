package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.ServiceInfo;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.ServiceMetadataPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceInfoInteractorTest {

    @Test
    void getInfo_buildsServiceInfoFromMetadata() {
        ServiceMetadataPort metadata = new ServiceMetadataPort() {
            @Override
            public String name() {
                return "billing-service";
            }

            @Override
            public String version() {
                return "1.2.3";
            }
        };

        ServiceInfo info = new ServiceInfoInteractor(metadata).getInfo();

        assertEquals(new ServiceInfo("billing-service", "1.2.3"), info);
    }
}
