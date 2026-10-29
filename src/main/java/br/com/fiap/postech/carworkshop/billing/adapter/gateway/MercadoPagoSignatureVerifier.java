package br.com.fiap.postech.carworkshop.billing.adapter.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

// Template do manifesto NAO confirmado na doc oficial do Mercado Pago: a pagina de Webhooks mostra so o
// formato "ts=...,v1=..." e manda usar o SDK. O formato abaixo e hipotese, a medir com o botao "Simular"
// do painel antes de depender dele; o request-id tambem depende de o API Gateway repassar o header.
public final class MercadoPagoSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public MercadoPagoSignatureVerifier(String secret) {
        this.secret = secret;
    }

    public boolean isValid(String signatureHeader, String dataId, String requestId) {
        if (secret == null || secret.isBlank() || signatureHeader == null || dataId == null) {
            return false;
        }
        String timestamp = part(signatureHeader, "ts");
        String received = part(signatureHeader, "v1");
        if (timestamp == null || received == null) {
            return false;
        }
        String expected = hmac(manifest(dataId, requestId, timestamp));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    static String manifest(String dataId, String requestId, String timestamp) {
        StringBuilder manifest = new StringBuilder("id:").append(dataId.toLowerCase()).append(';');
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        return manifest.append("ts:").append(timestamp).append(';').toString();
    }

    private String hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC could not be computed.", e);
        }
    }

    private static String part(String header, String key) {
        for (String piece : header.split(",")) {
            String[] pair = piece.trim().split("=", 2);
            if (pair.length == 2 && key.equals(pair[0].trim())) {
                return pair[1].trim();
            }
        }
        return null;
    }
}
