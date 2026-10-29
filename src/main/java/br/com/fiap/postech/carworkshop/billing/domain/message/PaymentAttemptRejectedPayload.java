package br.com.fiap.postech.carworkshop.billing.domain.message;

public record PaymentAttemptRejectedPayload(String statusDetail, String reason) implements EventPayload {

    public static PaymentAttemptRejectedPayload of(String statusDetail) {
        return new PaymentAttemptRejectedPayload(statusDetail, statusDetail);
    }
}
