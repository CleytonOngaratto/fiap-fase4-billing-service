package br.com.fiap.postech.carworkshop.billing.usecase.interactor;

import br.com.fiap.postech.carworkshop.billing.domain.entity.Budget;
import br.com.fiap.postech.carworkshop.billing.domain.entity.CancelReason;
import br.com.fiap.postech.carworkshop.billing.domain.entity.Payment;
import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentAttempt;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingEvent;
import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import br.com.fiap.postech.carworkshop.billing.domain.message.EventPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentAttemptRejectedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.PaymentConfirmedPayload;
import br.com.fiap.postech.carworkshop.billing.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentNotificationUseCase;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.BudgetRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentAttemptPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentGatewayPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentRepositoryPort;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.PaymentSnapshot;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.RefundCreated;
import br.com.fiap.postech.carworkshop.billing.usecase.port.out.SagaOutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class PaymentNotificationInteractor implements PaymentNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationInteractor.class);

    private final BudgetRepositoryPort budgets;
    private final PaymentRepositoryPort payments;
    private final PaymentAttemptPort attempts;
    private final PaymentGatewayPort gateway;
    private final SagaOutboxPort outbox;
    private final Clock clock;

    public PaymentNotificationInteractor(BudgetRepositoryPort budgets, PaymentRepositoryPort payments,
                                         PaymentAttemptPort attempts, PaymentGatewayPort gateway,
                                         SagaOutboxPort outbox, Clock clock) {
        this.budgets = budgets;
        this.payments = payments;
        this.attempts = attempts;
        this.gateway = gateway;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void onNotification(String mpPaymentId) {
        PaymentSnapshot snapshot = gateway.getPayment(mpPaymentId);
        Payment payment = locate(snapshot);
        Budget budget = budgets.findById(payment.getBudgetId())
                .orElseThrow(() -> new BudgetNotFoundException(
                        "No budget with id " + payment.getBudgetId() + "."));

        if (snapshot.isApproved()) {
            onApproved(budget, payment, snapshot);
            return;
        }
        onRejected(budget, payment, snapshot);
    }

    private void onApproved(Budget budget, Payment payment, PaymentSnapshot snapshot) {
        if (budget.isCanceled()) {
            refundPaidAfterCancellation(payment, snapshot);
            return;
        }
        if (!payment.confirm(snapshot.mpPaymentId(), Instant.now(clock))) {
            log.info("Payment {} was already answered: notification ignored.", snapshot.mpPaymentId());
            return;
        }
        payments.save(payment);
        publish(budget, BillingMessageType.PAYMENT_CONFIRMED,
                new PaymentConfirmedPayload(snapshot.mpPaymentId(), snapshot.amount()));
    }

    private void onRejected(Budget budget, Payment payment, PaymentSnapshot snapshot) {
        PaymentAttempt attempt = new PaymentAttempt(payment.getId(), snapshot.mpPaymentId(),
                snapshot.statusDetail(), Instant.now(clock));
        if (!attempts.recordIfNew(attempt)) {
            log.info("Attempt {} of payment {} was already recorded: notification ignored.",
                    snapshot.statusDetail(), snapshot.mpPaymentId());
            return;
        }
        payment.rememberAttempt(snapshot.mpPaymentId(), Instant.now(clock));
        payments.save(payment);
        publish(budget, BillingMessageType.PAYMENT_ATTEMPT_REJECTED,
                PaymentAttemptRejectedPayload.of(snapshot.statusDetail()));
    }

    private void refundPaidAfterCancellation(Payment payment, PaymentSnapshot snapshot) {
        if (payment.isRefunded()) {
            log.info("Payment {} was already refunded: notification ignored.", snapshot.mpPaymentId());
            return;
        }
        RefundCreated refund = gateway.refundPayment(snapshot.mpPaymentId(),
                payment.getRefundIdempotencyKey());
        payment.refund(refund.refundId(), CancelReason.PAID_AFTER_CANCELLATION, Instant.now(clock));
        payments.save(payment);
        log.warn("Payment {} arrived for a canceled budget and was refunded: no saga event published.",
                snapshot.mpPaymentId());
    }

    private Payment locate(PaymentSnapshot snapshot) {
        UUID budgetId;
        try {
            budgetId = UUID.fromString(snapshot.externalReference());
        } catch (RuntimeException e) {
            throw new BudgetNotFoundException("Notification " + snapshot.mpPaymentId()
                    + " has no usable external reference.");
        }
        return payments.findByBudgetId(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException(
                        "No payment for budget " + budgetId + "."));
    }

    private void publish(Budget budget, String type, EventPayload payload) {
        outbox.append(new BillingEvent(SagaEnvelope.event(type, budget.getWorkOrderId(),
                budget.getSagaId(), budget.getBranchId(), null, null, Instant.now(clock)), payload));
    }
}
