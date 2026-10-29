package br.com.fiap.postech.carworkshop.billing.domain.exception;

public class BudgetNotFoundException extends BusinessException {

    public BudgetNotFoundException(String message) {
        super(message);
    }
}
