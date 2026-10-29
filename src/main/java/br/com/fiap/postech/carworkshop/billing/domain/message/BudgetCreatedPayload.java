package br.com.fiap.postech.carworkshop.billing.domain.message;

import java.math.BigDecimal;

public record BudgetCreatedPayload(String budgetId, BigDecimal total) implements EventPayload {
}
