package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.gateway.TestSignature;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class MercadoPagoWebhookControllerTest {

    private static final String PATH = "/webhooks/mercadopago";
    private static final String SECRET = "test-webhook-secret";
    private static final String DATA_ID = "1234567890";
    private static final String REQUEST_ID = "a1b2c3d4";
    private static final String TIMESTAMP = "1761393600";

    // Rota publica por desenho: a requisicao nao leva JWT nenhum e, mesmo assim, nao e 401 por autenticacao,
    // e sim por assinatura — e e a assinatura a unica protecao desta rota.
    @Test
    void aNotificationWithoutSignatureIsRefused() {
        given().contentType("application/json").body("{}")
                .queryParam("type", "payment")
                .queryParam("data.id", DATA_ID)
                .when().post(PATH)
                .then().statusCode(401);
    }

    @Test
    void aNotificationWithAWrongSignatureIsRefused() {
        given().contentType("application/json").body("{}")
                .header("x-signature", TestSignature.header(TIMESTAMP, "deadbeef"))
                .header("x-request-id", REQUEST_ID)
                .queryParam("type", "payment")
                .queryParam("data.id", DATA_ID)
                .when().post(PATH)
                .then().statusCode(401);
    }

    // Qualquer resposta fora de 200 faz o Mercado Pago reentregar para sempre.
    @Test
    void aSignedNotificationOfAnotherTopicIsAcknowledged() {
        given().contentType("application/json").body("{}")
                .header("x-signature", TestSignature.header(TIMESTAMP,
                        TestSignature.v1(SECRET, DATA_ID, REQUEST_ID, TIMESTAMP)))
                .header("x-request-id", REQUEST_ID)
                .queryParam("topic", "merchant_order")
                .queryParam("data.id", DATA_ID)
                .when().post(PATH)
                .then().statusCode(200);
    }
}
