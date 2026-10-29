package br.com.fiap.postech.carworkshop.billing.adapter.presenter;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.BudgetItem;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetResponse(String id, Long workOrderId, String status, BigDecimal total,
                             Instant approvalDeadline, String cancelReason, List<Item> items,
                             String paymentStatus, String paymentLink, Instant paymentDeadline) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String type, Long catalogId, String description, int quantity,
                       BigDecimal unitPrice) {
    }

    public static BudgetResponse from(BudgetView view) {
        Budget budget = view.budget();
        Payment payment = view.payment();
        List<Item> items = new ArrayList<>();
        for (BudgetItem item : budget.getItems()) {
            items.add(new Item(item.type(), item.catalogId(), item.description(), item.quantity(),
                    item.unitPrice()));
        }
        return new BudgetResponse(budget.getId().toString(), budget.getWorkOrderId(),
                budget.getStatus().name(), budget.getTotal(), budget.getApprovalDeadline(),
                budget.getCancelReason(), items,
                payment == null ? null : payment.getStatus().name(),
                payment == null ? null : payment.getPaymentLink(),
                payment == null ? null : payment.getPaymentDeadline());
    }
}
