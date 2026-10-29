package br.com.fiap.postech.carworkshop.billing.domain.message;

public sealed interface EventPayload permits BudgetCreatedPayload, BudgetAnsweredPayload,
        BudgetCanceledPayload, PaymentRequestedPayload, PaymentAttemptRejectedPayload,
        PaymentConfirmedPayload, PaymentCanceledPayload, PaymentRefundedPayload, FailurePayload {
}
