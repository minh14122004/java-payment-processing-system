package com.example.payment.security;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class OtpDigester {
    private final byte[] key;
    public OtpDigester(String secret) { key = SecretKey.decode(secret, "OTP_HMAC_KEY"); }
    public String digest(UUID challenge, String code) {
        return HexFormat.of().formatHex(SecretKey.hmac("HmacSHA256", key, challenge + ":" + code));
    }
    public boolean matches(UUID challenge, String code, String digest) {
        if (digest == null || code == null || !code.matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(digest), HexFormat.of().parseHex(digest(challenge, code)));
    }
}
