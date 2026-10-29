package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.CancelReason;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.PaymentGatewayException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.CancelPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentCanceledPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentRefundedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentRequestedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.RefundPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RequestPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.CancellationOutcome;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PreferenceCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class PaymentInteractor implements PaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentInteractor.class);

    private final BudgetRepositoryPort budgets;
    private final PaymentRepositoryPort payments;
    private final PaymentGatewayPort gateway;
    private final SagaOutboxPort outbox;
    private final Clock clock;

    public PaymentInteractor(BudgetRepositoryPort budgets, PaymentRepositoryPort payments,
                             PaymentGatewayPort gateway, SagaOutboxPort outbox, Clock clock) {
        this.budgets = budgets;
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void request(RequestPaymentCommand command) {
        Budget budget = require(command.budgetId());
        Payment payment = payments.findByBudgetId(budget.getId())
                .orElseGet(() -> payments.save(Payment.create(budget.getId(),
                        command.envelope().workOrderId(), command.amount(),
                        command.paymentDeadline(), Instant.now(clock))));

        if (payment.getPaymentLink() == null) {
            try {
                PreferenceCreated preference = gateway.createPreference(budget, payment);
                payment.linkCreated(preference.preferenceId(), preference.initPoint(),
                        Instant.now(clock));
                payments.save(payment);
            } catch (PaymentGatewayException e) {
                log.warn("Could not create the Checkout Pro preference for budget {}: {}",
                        budget.getId(), e.getMessage());
                outbox.append(BillingEvent.replyTo(command.envelope(),
                        BillingMessageType.PAYMENT_REQUEST_FAILED, new FailurePayload(e.getMessage())));
                return;
            }
        }

        outbox.append(BillingEvent.replyTo(command.envelope(), BillingMessageType.PAYMENT_REQUESTED,
                new PaymentRequestedPayload(budget.getId().toString(), payment.getPaymentLink(),
                        payment.getPreferenceId())));
    }

    @Override
    public void cancel(CancelPaymentCommand command) {
        Budget budget = require(command.budgetId());
        Payment payment = payments.findByBudgetId(budget.getId()).orElse(null);

        if (payment != null) {
            CancellationOutcome outcome = gateway.cancelPayment(payment.getMpPaymentId());
            if (outcome == CancellationOutcome.NOT_CANCELABLE) {
                refundAtGateway(payment, CancelReason.PAID_AFTER_CANCELLATION);
            } else if (payment.cancel(command.reason(), Instant.now(clock))) {
                payments.save(payment);
            }
        }

        outbox.append(BillingEvent.replyTo(command.envelope(), BillingMessageType.PAYMENT_CANCELED,
                new PaymentCanceledPayload(budget.getId().toString())));
    }

    @Override
    public void refund(RefundPaymentCommand command) {
        Payment payment = payments.findByMpPaymentId(command.paymentId())
                .orElseThrow(() -> new BudgetNotFoundException(
                        "No payment with Mercado Pago id " + command.paymentId() + "."));

        if (!payment.isRefunded()) {
            refundAtGateway(payment, command.reason());
        }

        outbox.append(BillingEvent.replyTo(command.envelope(), BillingMessageType.PAYMENT_REFUNDED,
                new PaymentRefundedPayload(payment.getMpPaymentId(), payment.getRefundId())));
    }

    private void refundAtGateway(Payment payment, String reason) {
        RefundCreated refund = gateway.refundPayment(payment.getMpPaymentId(),
                payment.getRefundIdempotencyKey());
        payment.refund(refund.refundId(), reason, Instant.now(clock));
        payments.save(payment);
    }

    private Budget require(String budgetId) {
        UUID id;
        try {
            id = UUID.fromString(budgetId);
        } catch (RuntimeException e) {
            throw new BudgetNotFoundException("Budget id " + budgetId + " is not a valid identifier.");
        }
        return budgets.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException("No budget with id " + budgetId + "."));
    }
}
