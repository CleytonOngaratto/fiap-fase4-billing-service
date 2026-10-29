package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;

import java.util.UUID;

public interface PaymentGatewayPort {

    PreferenceCreated createPreference(Budget budget, Payment payment);

    PaymentSnapshot getPayment(String mpPaymentId);

    CancellationOutcome cancelPayment(String mpPaymentId);

    RefundCreated refundPayment(String mpPaymentId, UUID idempotencyKey);
}
