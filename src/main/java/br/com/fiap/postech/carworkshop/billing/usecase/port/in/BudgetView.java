package br.com.fiap.postech.carworkshop.billing.usecase.port.in;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;

public record BudgetView(Budget budget, Payment payment) {
}
