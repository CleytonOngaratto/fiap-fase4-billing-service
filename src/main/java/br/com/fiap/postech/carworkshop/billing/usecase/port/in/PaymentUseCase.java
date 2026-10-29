package br.com.fiap.postech.carworkshop.billing.usecase.port.in;

import br.com.fiap.postech.carworkshop.billing.domain.message.CancelPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RefundPaymentCommand;
import br.com.fiap.postech.carworkshop.billing.domain.message.RequestPaymentCommand;

public interface PaymentUseCase {

    void request(RequestPaymentCommand command);

    void cancel(CancelPaymentCommand command);

    void refund(RefundPaymentCommand command);
}
