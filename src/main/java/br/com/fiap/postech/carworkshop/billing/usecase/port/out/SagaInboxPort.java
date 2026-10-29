package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import java.util.UUID;

public interface SagaInboxPort {

    boolean recordIfNew(UUID messageId, Long workOrderId, String type);
}
