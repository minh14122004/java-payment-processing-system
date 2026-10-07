package com.example.payment.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class SecretKey {
    private SecretKey() {}
    public static byte[] decode(String value, String name) {
        try {
            byte[] key = Base64.getDecoder().decode(value);
            if (key.length < 32) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " must be Base64 encoding of at least 32 random bytes");
        }
    }
    public static byte[] hmac(String algorithm, byte[] key, String input) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Required HMAC algorithm unavailable", ex);
        }
    }
}
