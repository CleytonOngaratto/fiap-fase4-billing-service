package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

// O manifesto vem da propria classe de producao: um template copiado para ca continuaria verde depois de a
// hipotese do manifesto ser corrigida com a medicao do painel do Mercado Pago.
public final class TestSignature {

    private TestSignature() {
    }

    public static String v1(String secret, String dataId, String requestId, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String manifest = MercadoPagoSignatureVerifier.manifest(dataId, requestId, timestamp);
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String header(String timestamp, String v1) {
        return "ts=" + timestamp + ",v1=" + v1;
    }
}
