package br.com.fiap.postech.carworkshop.billing.infrastructure.config;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.BillingEventPublisher;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.FakePaymentGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.LoggingBillingEventGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.MercadoPagoPaymentGateway;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.MercadoPagoSignatureVerifier;
import br.com.fiap.postech.carworkshop.billing.adapter.gateway.SnsBillingEventGateway;
import br.com.fiap.postech.carworkshop.billing.usecase.interactor.BudgetInteractor;
import br.com.fiap.postech.carworkshop.billing.usecase.interactor.PaymentInteractor;
import br.com.fiap.postech.carworkshop.billing.usecase.interactor.PaymentNotificationInteractor;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.BudgetUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentNotificationUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentAttemptPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import software.amazon.awssdk.services.sns.SnsClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@ApplicationScoped
public class BillingModuleConfig {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "mercadopago.base-url")
    String mercadoPagoBaseUrl;

    @ConfigProperty(name = "mercadopago.access-token")
    String mercadoPagoAccessToken;

    @ConfigProperty(name = "mercadopago.webhook-secret")
    String mercadoPagoWebhookSecret;

    @ConfigProperty(name = "mercadopago.request-timeout")
    Duration mercadoPagoTimeout;

    @ConfigProperty(name = "mercadopago.max-attempts")
    int mercadoPagoMaxAttempts;

    @ConfigProperty(name = "mercadopago.retry-backoff")
    Duration mercadoPagoRetryBackoff;

    @ConfigProperty(name = "mercadopago.fake")
    boolean mercadoPagoFake;

    @ConfigProperty(name = "billing.events.publish-to-log")
    boolean publishToLog;

    @ConfigProperty(name = "billing.events.topic-arn")
    String topicArn;

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    public PaymentGatewayPort paymentGateway() {
        if (mercadoPagoFake) {
            return new FakePaymentGateway();
        }
        return new MercadoPagoPaymentGateway(mercadoPagoBaseUrl, mercadoPagoAccessToken,
                mercadoPagoTimeout, mercadoPagoMaxAttempts, mercadoPagoRetryBackoff,
                HttpClient.newBuilder().connectTimeout(mercadoPagoTimeout).build(), objectMapper);
    }

    @Produces
    @ApplicationScoped
    public MercadoPagoSignatureVerifier signatureVerifier() {
        return new MercadoPagoSignatureVerifier(mercadoPagoWebhookSecret);
    }

    // O ElasticMQ nao emula SNS, entao o perfil de desenvolvimento registra o envelope em log em vez de
    // publicar; apontar o relay para a fila do OS poria a fila de outro servico na configuracao deste.
    @Produces
    @ApplicationScoped
    public BillingEventPublisher billingEventPublisher(SnsClient sns) {
        return publishToLog
                ? new LoggingBillingEventGateway()
                : new SnsBillingEventGateway(sns, topicArn);
    }

    @Produces
    @ApplicationScoped
    public BudgetUseCase budgetUseCase(BudgetRepositoryPort budgets, PaymentRepositoryPort payments,
                                       SagaOutboxPort outbox, Clock clock) {
        return new BudgetInteractor(budgets, payments, outbox, clock);
    }

    @Produces
    @ApplicationScoped
    public PaymentUseCase paymentUseCase(BudgetRepositoryPort budgets, PaymentRepositoryPort payments,
                                         PaymentGatewayPort gateway, SagaOutboxPort outbox,
                                         Clock clock) {
        return new PaymentInteractor(budgets, payments, gateway, outbox, clock);
    }

    @Produces
    @ApplicationScoped
    public PaymentNotificationUseCase paymentNotificationUseCase(BudgetRepositoryPort budgets,
                                                                 PaymentRepositoryPort payments,
                                                                 PaymentAttemptPort attempts,
                                                                 PaymentGatewayPort gateway,
                                                                 SagaOutboxPort outbox, Clock clock) {
        return new PaymentNotificationInteractor(budgets, payments, attempts, gateway, outbox, clock);
    }
}
