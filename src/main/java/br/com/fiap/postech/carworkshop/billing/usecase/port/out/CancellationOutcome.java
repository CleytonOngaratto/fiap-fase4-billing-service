package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

public enum CancellationOutcome {
    CANCELED,
    NOT_CANCELABLE,
    REFUSED,
    NO_PAYMENT
}
