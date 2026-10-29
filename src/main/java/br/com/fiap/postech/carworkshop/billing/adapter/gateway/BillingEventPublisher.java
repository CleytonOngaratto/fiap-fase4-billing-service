package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

public interface BillingEventPublisher {

    void publish(OutboundMessage message);
}
