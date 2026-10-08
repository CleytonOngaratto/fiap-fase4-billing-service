package br.com.fiap.postech.carworkshop.billing.adapter.presenter;

import br.com.fiap.postech.carworkshop.billing.domain.entity.ServiceInfo;

public record InfoResponse(String name, String version) {

    public static InfoResponse from(ServiceInfo info) {
        return new InfoResponse(info.name(), info.version());
    }
}
