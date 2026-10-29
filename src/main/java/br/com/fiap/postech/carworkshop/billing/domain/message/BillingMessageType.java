package br.com.fiap.postech.carworkshop.billing.domain.message;

public final class BillingMessageType {

    public static final String CREATE_BUDGET = "CreateBudget";
    public static final String CANCEL_BUDGET = "CancelBudget";
    public static final String REQUEST_PAYMENT = "RequestPayment";
    public static final String CANCEL_PAYMENT = "CancelPayment";
    public static final String REFUND_PAYMENT = "RefundPayment";

    public static final String BUDGET_CREATED = "BudgetCreated";
    public static final String BUDGET_CREATION_FAILED = "BudgetCreationFailed";
    public static final String BUDGET_APPROVED = "BudgetApproved";
    public static final String BUDGET_REJECTED = "BudgetRejected";
    public static final String BUDGET_CANCELED = "BudgetCanceled";
    public static final String PAYMENT_REQUESTED = "PaymentRequested";
    public static final String PAYMENT_REQUEST_FAILED = "PaymentRequestFailed";
    public static final String PAYMENT_ATTEMPT_REJECTED = "PaymentAttemptRejected";
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    public static final String PAYMENT_CANCELED = "PaymentCanceled";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";

    private BillingMessageType() {
    }
}
