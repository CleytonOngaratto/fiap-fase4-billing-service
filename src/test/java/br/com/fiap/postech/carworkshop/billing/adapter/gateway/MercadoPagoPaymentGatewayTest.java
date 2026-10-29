package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.exception.PaymentGatewayException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.UnknownPaymentException;
import br.com.fiap.postech.carworkshop.billing.support.BillingFixtures;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Teste de contrato contra o WireMock: exercita os ramos de HTTP que a sandbox do Mercado Pago nao da de
// graca - 5xx, timeout, corpo malformado e o erro 2018 do cancelamento.
class MercadoPagoPaymentGatewayTest {

    private static final String TOKEN = "APP_USR-test-token";

    private static WireMockServer wireMock;

    private MercadoPagoPaymentGateway gateway;
    private Budget budget;
    private Payment payment;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        WireMock.configureFor(wireMock.port());
        gateway = newGateway(Duration.ofSeconds(5), 3);
        budget = BillingFixtures.budget();
        payment = BillingFixtures.payment(budget.getId());
    }

    private MercadoPagoPaymentGateway newGateway(Duration timeout, int attempts) {
        return new MercadoPagoPaymentGateway("http://localhost:" + wireMock.port(), TOKEN, timeout,
                attempts, Duration.ZERO, HttpClient.newHttpClient(), new ObjectMapper());
    }

    @Test
    void createsThePreferenceWithExpirationExternalReferenceAndBearerToken() {
        wireMock.stubFor(post(urlPathEqualTo("/checkout/preferences"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pref-1\",\"init_point\":\"https://mp/checkout\"}")));

        PreferenceCreated created = gateway.createPreference(budget, payment);

        assertEquals("pref-1", created.preferenceId());
        assertEquals("https://mp/checkout", created.initPoint());
        wireMock.verify(postRequestedFor(urlPathEqualTo("/checkout/preferences"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(containing("\"external_reference\":\"" + budget.getId() + "\""))
                .withRequestBody(containing("\"expires\":true"))
                .withRequestBody(containing("\"expiration_date_from\":\"2026-10-25T12:00:00.000+00:00\""))
                .withRequestBody(containing("\"expiration_date_to\":\"2026-10-25T12:30:00.000+00:00\"")));
    }

    @Test
    void failsWhenThePreferenceComesWithoutInitPoint() {
        wireMock.stubFor(post(urlPathEqualTo("/checkout/preferences"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pref-1\"}")));

        assertThrows(PaymentGatewayException.class, () -> gateway.createPreference(budget, payment));
    }

    @Test
    void readsTheRealStatusFromTheApiAndNotFromTheNotification() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"mp-1\",\"status\":\"approved\","
                                + "\"status_detail\":\"accredited\",\"transaction_amount\":257.70,"
                                + "\"external_reference\":\"" + budget.getId() + "\"}")));

        PaymentSnapshot snapshot = gateway.getPayment("mp-1");

        assertTrue(snapshot.isApproved());
        assertEquals("accredited", snapshot.statusDetail());
        assertEquals(0, new BigDecimal("257.70").compareTo(snapshot.amount()));
        assertEquals(budget.getId().toString(), snapshot.externalReference());
    }

    @Test
    void cancelsThePaymentWhenMercadoPagoAccepts() {
        wireMock.stubFor(put(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"mp-1\",\"status\":\"cancelled\"}")));

        assertEquals(CancellationOutcome.CANCELED, gateway.cancelPayment("mp-1"));
    }

    // Erro 2018: o pagamento nao esta mais pending/in_process/authorized. Nao e falha de transporte, e
    // mudanca de caminho - quem chama estorna em vez de cancelar.
    @Test
    void reportsNotCancelableOnError2018() {
        wireMock.stubFor(put(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Invalid payment status\",\"error\":\"bad_request\","
                                + "\"status\":400,\"cause\":[{\"code\":2018,"
                                + "\"description\":\"Invalid payment status\"}]}")));

        assertEquals(CancellationOutcome.NOT_CANCELABLE, gateway.cancelPayment("mp-1"));
    }

    @Test
    void reportsRefusedOnAnyOtherRefusal() {
        wireMock.stubFor(put(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":400,\"cause\":[{\"code\":4017}]}")));

        assertEquals(CancellationOutcome.REFUSED, gateway.cancelPayment("mp-1"));
    }

    @Test
    void reportsNoPaymentWhenTheCustomerNeverOpenedTheLink() {
        assertEquals(CancellationOutcome.NO_PAYMENT, gateway.cancelPayment(null));
        assertEquals(CancellationOutcome.NO_PAYMENT, gateway.cancelPayment("  "));
    }

    @Test
    void refundsSendingTheIdempotencyKey() {
        UUID key = UUID.randomUUID();
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/mp-1/refunds"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"refund-1\",\"payment_id\":\"mp-1\",\"amount\":257.70}")));

        RefundCreated refund = gateway.refundPayment("mp-1", key);

        assertEquals("refund-1", refund.refundId());
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/payments/mp-1/refunds"))
                .withHeader("X-Idempotency-Key", equalTo(key.toString())));
    }

    @Test
    void failsWhenTheRefundComesWithoutId() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/mp-1/refunds"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody("{}")));

        UUID key = UUID.randomUUID();
        assertThrows(PaymentGatewayException.class, () -> gateway.refundPayment("mp-1", key));
    }

    // 5xx e retentado; esgotado, vira excecao, e quem chama transforma em PaymentRequestFailed.
    @Test
    void retriesOnServerErrorAndGivesUpAfterTheLastAttempt() {
        wireMock.stubFor(post(urlPathEqualTo("/checkout/preferences"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThrows(PaymentGatewayException.class, () -> gateway.createPreference(budget, payment));

        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/checkout/preferences")));
    }

    @Test
    void doesNotRetryOnARefusal() {
        wireMock.stubFor(post(urlPathEqualTo("/checkout/preferences"))
                .willReturn(aResponse().withStatus(400).withBody("{\"status\":400}")));

        assertThrows(PaymentGatewayException.class, () -> gateway.createPreference(budget, payment));

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/checkout/preferences")));
    }

    @Test
    void reportsAnUnknownPaymentWhenMercadoPagoAnswersNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(404).withBody("{\"status\":404}")));

        assertThrows(UnknownPaymentException.class, () -> gateway.getPayment("mp-1"));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/v1/payments/mp-1")));
    }

    @Test
    void failsOnAMalformedBody() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("not json at all")));

        assertThrows(PaymentGatewayException.class, () -> gateway.getPayment("mp-1"));
    }

    @Test
    void failsOnTimeout() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/mp-1"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                        .withHeader("Content-Type", "application/json").withBody("{\"id\":\"mp-1\"}")));
        MercadoPagoPaymentGateway impatient = newGateway(Duration.ofMillis(200), 1);

        assertThrows(PaymentGatewayException.class, () -> impatient.getPayment("mp-1"));
    }
}
