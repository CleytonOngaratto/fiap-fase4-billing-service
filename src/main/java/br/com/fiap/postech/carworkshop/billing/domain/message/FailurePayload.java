package br.com.fiap.postech.carworkshop.billing.domain.message;

public record FailurePayload(String reason) implements EventPayload {
}
