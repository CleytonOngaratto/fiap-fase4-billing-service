package br.com.fiap.postech.carworkshop.billing.domain.exception;

public class NotBudgetOwnerException extends BusinessException {

    public NotBudgetOwnerException(String message) {
        super(message);
    }
}
