package br.com.fiap.postech.carworkshop.billing.domain.exception;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
