package br.com.fiap.postech.carworkshop.billing.infrastructure.config;

import br.com.fiap.postech.carworkshop.billing.usecase.interactor.ServiceInfoInteractor;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.ServiceInfoUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.ServiceMetadataPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InfoModuleConfigTest {

    @Test
    void serviceInfoUseCase_buildsWiredInteractor() {
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

        ServiceInfoUseCase useCase = new InfoModuleConfig().serviceInfoUseCase(metadata);

        assertNotNull(useCase);
        assertInstanceOf(ServiceInfoInteractor.class, useCase);
    }
}
