package br.com.fiap.postech.carworkshop.billing.domain.exception;

public class BudgetNotApprovableException extends BusinessException {

    public BudgetNotApprovableException(String message) {
        super(message);
    }
}
