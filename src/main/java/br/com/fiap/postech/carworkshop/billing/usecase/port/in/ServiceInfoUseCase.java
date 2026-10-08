package br.com.fiap.postech.carworkshop.billing.usecase.port.in;

import br.com.fiap.postech.carworkshop.billing.domain.entity.ServiceInfo;

public interface ServiceInfoUseCase {
    ServiceInfo getInfo();
}
