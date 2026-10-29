package br.com.fiap.postech.carworkshop.billing.domain.entity;

import java.math.BigDecimal;

public record BudgetItem(String type, Long catalogId, String description, int quantity,
                         BigDecimal unitPrice) {

    public static final String SERVICE = "SERVICE";
    public static final String PART = "PART";

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
