package br.com.supermercados.prices.subscription.abacatepay;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs test bodies the way AbacatePay does: Base64 HMAC-SHA256 with its documented public key. */
public final class AbacatePaySignatures {

    private AbacatePaySignatures() {
    }

    public static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(AbacatePayProperties.DOCUMENTED_SIGNATURE_KEY.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
