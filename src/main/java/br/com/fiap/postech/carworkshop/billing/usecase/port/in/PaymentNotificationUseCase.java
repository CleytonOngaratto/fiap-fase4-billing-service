package br.com.fiap.postech.carworkshop.billing.usecase.port.in;

public interface PaymentNotificationUseCase {

    void onNotification(String mpPaymentId);
}
