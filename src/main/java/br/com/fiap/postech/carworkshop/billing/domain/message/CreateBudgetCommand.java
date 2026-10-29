package br.com.fiap.postech.carworkshop.billing.domain.message;

import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateBudgetCommand(SagaEnvelope envelope, String customerDocument,
                                  List<BudgetItem> items, BigDecimal total,
                                  Instant approvalDeadline) {
}
