package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Usado so no perfil de desenvolvimento: o ElasticMQ nao emula SNS, e apontar o relay para a fila do OS
// poria a fila de outro servico na configuracao deste, contra "o topico e o contrato".
public class LoggingBillingEventGateway implements BillingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingBillingEventGateway.class);

    @Override
    public void publish(OutboundMessage message) {
        log.info("{} para a OS {} (saga {}): {}", message.type(), message.workOrderId(),
                message.sagaId(), message.body());
    }
}
