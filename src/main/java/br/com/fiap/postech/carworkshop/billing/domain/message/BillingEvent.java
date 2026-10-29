package br.com.fiap.postech.carworkshop.billing.domain.message;

public record BillingEvent(SagaEnvelope envelope, EventPayload payload) {

    public static BillingEvent replyTo(SagaEnvelope cause, String type, EventPayload payload) {
        return new BillingEvent(cause.reply(type), payload);
    }
}
