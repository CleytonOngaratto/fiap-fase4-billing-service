package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret-de-teste";
    private static final String DATA_ID = "1234567890";
    private static final String REQUEST_ID = "a1b2c3d4-0000-0000-0000-000000000000";
    private static final String TIMESTAMP = "1761393600";

    private final MercadoPagoSignatureVerifier verifier = new MercadoPagoSignatureVerifier(SECRET);

    @Test
    void acceptsASignatureThatMatchesTheManifest() {
        assertTrue(verifier.isValid(header(TIMESTAMP, sign(TIMESTAMP)), DATA_ID, REQUEST_ID));
    }

    @Test
    void acceptsASignatureWhenTheGatewayDidNotForwardTheRequestId() {
        String v1 = TestSignature.v1(SECRET, DATA_ID, null, TIMESTAMP);

        assertTrue(verifier.isValid(header(TIMESTAMP, v1), DATA_ID, null));
    }

    @Test
    void refusesASignatureComputedOverAnotherTimestamp() {
        assertFalse(verifier.isValid(header("1761393601", sign(TIMESTAMP)), DATA_ID, REQUEST_ID));
    }

    @Test
    void refusesAHeaderWithoutTimestampOrWithoutSignature() {
        assertFalse(verifier.isValid("v1=" + sign(TIMESTAMP), DATA_ID, REQUEST_ID));
        assertFalse(verifier.isValid("ts=" + TIMESTAMP, DATA_ID, REQUEST_ID));
        assertFalse(verifier.isValid("nonsense", DATA_ID, REQUEST_ID));
    }

    @Test
    void refusesWhenTheHeaderOrTheNotificationIdIsMissing() {
        assertFalse(verifier.isValid(null, DATA_ID, REQUEST_ID));
        assertFalse(verifier.isValid(header(TIMESTAMP, sign(TIMESTAMP)), null, REQUEST_ID));
    }

    // Fail closed sem segredo configurado: deixar passar seria aceitar qualquer notificacao, e esta rota e
    // publica por desenho.
    @Test
    void refusesEverythingWhenThereIsNoSecret() {
        assertFalse(new MercadoPagoSignatureVerifier(null)
                .isValid(header(TIMESTAMP, sign(TIMESTAMP)), DATA_ID, REQUEST_ID));
        assertFalse(new MercadoPagoSignatureVerifier("   ")
                .isValid(header(TIMESTAMP, sign(TIMESTAMP)), DATA_ID, REQUEST_ID));
    }

    @Test
    void buildsTheManifestInTheHypothesizedTemplate() {
        assertEquals("id:abc123;request-id:req-1;ts:1761393600;",
                MercadoPagoSignatureVerifier.manifest("ABC123", "req-1", TIMESTAMP));
        assertEquals("id:abc123;ts:1761393600;",
                MercadoPagoSignatureVerifier.manifest("abc123", "  ", TIMESTAMP));
    }

    private static String header(String timestamp, String v1) {
        return TestSignature.header(timestamp, v1);
    }

    private static String sign(String timestamp) {
        return TestSignature.v1(SECRET, DATA_ID, REQUEST_ID, timestamp);
    }
}
