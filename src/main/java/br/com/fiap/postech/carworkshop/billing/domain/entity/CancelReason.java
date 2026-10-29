package br.com.fiap.postech.carworkshop.billing.domain.entity;

public final class CancelReason {

    public static final String REJECTED_BY_CUSTOMER = "REJECTED_BY_CUSTOMER";
    public static final String PAID_AFTER_CANCELLATION = "PAID_AFTER_CANCELLATION";

    private CancelReason() {
    }
}
