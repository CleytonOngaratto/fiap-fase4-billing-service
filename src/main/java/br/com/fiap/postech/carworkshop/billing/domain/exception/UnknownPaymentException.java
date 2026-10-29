package br.com.fiap.postech.carworkshop.billing.domain.exception;

public class UnknownPaymentException extends PaymentGatewayException {

    public UnknownPaymentException(String message) {
        super(message);
    }
}
