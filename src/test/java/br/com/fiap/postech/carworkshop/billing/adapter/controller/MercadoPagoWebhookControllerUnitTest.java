package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.MercadoPagoSignatureVerifier;
import br.com.fiap.postech.carworkshop.billing.usecase.port.in.PaymentNotificationUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MercadoPagoWebhookControllerUnitTest {

    private static final String DATA_ID = "1234567890";
    private static final String SIGNATURE = "ts=1761393600,v1=abc";
    private static final String REQUEST_ID = "a1b2c3d4";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaymentNotificationUseCase useCase;
    private MercadoPagoSignatureVerifier verifier;
    private MercadoPagoWebhookController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(PaymentNotificationUseCase.class);
        verifier = mock(MercadoPagoSignatureVerifier.class);
        when(verifier.isValid(anyString(), anyString(), any())).thenReturn(true);
        controller = new MercadoPagoWebhookController();
        controller.useCase = useCase;
        controller.verifier = verifier;
    }

    @Test
    void handlesTheNotificationIdentifiedByTheQueryString() {
        int status = controller.notify(null, "payment", null, DATA_ID, SIGNATURE, REQUEST_ID)
                .getStatus();

        assertEquals(200, status);
        verify(useCase).onNotification(DATA_ID);
        verify(verifier).isValid(SIGNATURE, DATA_ID, REQUEST_ID);
    }

    @Test
    void handlesTheNotificationIdentifiedByTheBody() {
        JsonNode body = body("payment", DATA_ID);

        assertEquals(200, controller.notify(body, null, null, null, SIGNATURE, REQUEST_ID).getStatus());

        verify(useCase).onNotification(DATA_ID);
    }

    @Test
    void refusesANotificationWithAnInvalidSignature() {
        when(verifier.isValid(any(), any(), any())).thenReturn(false);

        int status = controller.notify(null, "payment", null, DATA_ID, "ts=1,v1=wrong", REQUEST_ID)
                .getStatus();

        assertEquals(401, status);
        verifyNoInteractions(useCase);
    }

    // Qualquer coisa fora de 200 faz o Mercado Pago reentregar para sempre, e o painel manda
    // merchant_order em alguns fluxos.
    @Test
    void acknowledgesATopicItDoesNotHandle() {
        int status = controller.notify(null, null, "merchant_order", DATA_ID, SIGNATURE, REQUEST_ID)
                .getStatus();

        assertEquals(200, status);
        verify(useCase, never()).onNotification(anyString());
    }

    @Test
    void acknowledgesANotificationWithoutTopicOrIdentifier() {
        when(verifier.isValid(any(), eq(null), any())).thenReturn(true);

        assertEquals(200, controller.notify(null, null, null, null, SIGNATURE, null).getStatus());

        verifyNoInteractions(useCase);
    }

    private JsonNode body(String type, String dataId) {
        return objectMapper.createObjectNode()
                .put("type", type)
                .set("data", objectMapper.createObjectNode().put("id", dataId));
    }
}
