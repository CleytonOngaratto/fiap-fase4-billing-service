package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;

import java.math.BigDecimal;
import java.util.UUID;

// Perfis de desenvolvimento e de teste: deixa a aplicacao subir e o fluxo andar sem conta no Mercado Pago e
// sem rede. Os ramos de HTTP (5xx, timeout, corpo malformado, 2018) sao exercidos pelo adapter de verdade
// contra o WireMock, nao aqui.
public class FakePaymentGateway implements PaymentGatewayPort {

    public static final String APPROVED_DETAIL = "accredited";

    @Override
    public PreferenceCreated createPreference(Budget budget, Payment payment) {
        String preferenceId = "fake-preference-" + budget.getId();
        return new PreferenceCreated(preferenceId,
                "https://www.mercadopago.com.br/checkout/start?pref_id=" + preferenceId);
    }

    @Override
    public PaymentSnapshot getPayment(String mpPaymentId) {
        return new PaymentSnapshot(mpPaymentId, "approved", APPROVED_DETAIL, BigDecimal.ZERO, null);
    }

    @Override
    public CancellationOutcome cancelPayment(String mpPaymentId) {
        return mpPaymentId == null || mpPaymentId.isBlank()
                ? CancellationOutcome.NO_PAYMENT
                : CancellationOutcome.CANCELED;
    }

    @Override
    public RefundCreated refundPayment(String mpPaymentId, UUID idempotencyKey) {
        return new RefundCreated("fake-refund-" + idempotencyKey);
    }
}
