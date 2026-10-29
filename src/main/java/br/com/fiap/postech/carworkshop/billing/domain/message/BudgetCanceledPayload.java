package br.com.fiap.postech.carworkshop.billing.domain.message;

public record BudgetCanceledPayload(String budgetId) implements EventPayload {
}
