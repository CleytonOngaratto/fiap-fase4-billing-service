package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import java.math.BigDecimal;

public record PaymentSnapshot(String mpPaymentId, String status, String statusDetail,
                              BigDecimal amount, String externalReference) {

    public boolean isApproved() {
        return "approved".equals(status);
    }

    public boolean isCancelable() {
        return "pending".equals(status) || "in_process".equals(status) || "authorized".equals(status);
    }
}
