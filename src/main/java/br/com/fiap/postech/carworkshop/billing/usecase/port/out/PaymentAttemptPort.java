package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

import br.com.fiap.postech.carworkshop.billing.domain.entity.PaymentAttempt;

public interface PaymentAttemptPort {

    boolean recordIfNew(PaymentAttempt attempt);
}
