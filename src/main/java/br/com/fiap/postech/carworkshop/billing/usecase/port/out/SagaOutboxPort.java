package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;

public interface SagaOutboxPort {

    void append(BillingEvent event);
}
