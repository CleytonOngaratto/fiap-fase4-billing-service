package br.com.fiap.postech.carworkshop.billing.infrastructure.config;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.FakePaymentGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.LoggingBillingEventGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.MercadoPagoPaymentGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SnsBillingEventGateway;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentAttemptPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BillingModuleConfigTest {

    private BillingModuleConfig config;

    @BeforeEach
    void setUp() {
        config = new BillingModuleConfig();
        config.objectMapper = new ObjectMapper();
        config.mercadoPagoBaseUrl = "https://api.mercadopago.com";
        config.mercadoPagoAccessToken = "token-de-teste";
        config.mercadoPagoWebhookSecret = "segredo-de-teste";
        config.mercadoPagoTimeout = Duration.ofSeconds(5);
        config.mercadoPagoMaxAttempts = 3;
        config.mercadoPagoRetryBackoff = Duration.ZERO;
        config.mercadoPagoFake = true;
        config.publishToLog = true;
        config.topicArn = "arn:aws:sns:us-east-1:436061918963:billing-events.fifo";
    }

    @Test
    void producesAClock() {
        assertNotNull(config.clock().instant());
    }

    @Test
    void choosesThePaymentGatewayByProfile() {
        assertInstanceOf(FakePaymentGateway.class, config.paymentGateway());

        config.mercadoPagoFake = false;

        assertInstanceOf(MercadoPagoPaymentGateway.class, config.paymentGateway());
    }

    @Test
    void producesAVerifierThatRefusesASignatureItDidNotSign() {
        assertNotNull(config.signatureVerifier());
        assertFalse(config.signatureVerifier()
                .isValid("ts=1761393600,v1=deadbeef", "1234567890", null));
    }

    // O ElasticMQ nao emula SNS, entao o perfil de desenvolvimento registra em log em vez de publicar.
    @Test
    void choosesTheEventPublisherByProfile() {
        SnsClient sns = mock(SnsClient.class);

        assertInstanceOf(LoggingBillingEventGateway.class, config.billingEventPublisher(sns));

        config.publishToLog = false;

        assertInstanceOf(SnsBillingEventGateway.class, config.billingEventPublisher(sns));
    }

    @Test
    void producesTheThreeUseCases() {
        BudgetRepositoryPort budgets = mock(BudgetRepositoryPort.class);
        PaymentRepositoryPort payments = mock(PaymentRepositoryPort.class);
        PaymentAttemptPort attempts = mock(PaymentAttemptPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        SagaOutboxPort outbox = mock(SagaOutboxPort.class);
        Clock clock = Clock.systemUTC();

        assertNotNull(config.budgetUseCase(budgets, payments, outbox, clock));
        assertNotNull(config.paymentUseCase(budgets, payments, gateway, outbox, clock));
        assertNotNull(config.paymentNotificationUseCase(budgets, payments, attempts, gateway, outbox,
                clock));
    }
}
