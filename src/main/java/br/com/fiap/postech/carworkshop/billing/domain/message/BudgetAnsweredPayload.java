package br.com.fiap.postech.carworkshop.billing.domain.message;

public record BudgetAnsweredPayload(String budgetId, String approvedBy) implements EventPayload {
}
