package com.example.payment.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Base64;
import java.util.regex.Pattern;

public final class PepperedPasswordEncoder implements PasswordEncoder {
    public static final String PREFIX = "{bcrypt-hmac-sha384-v1}";
    private static final Pattern HASH = Pattern.compile("\\$2b\\$(1[0-6])\\$[./A-Za-z0-9]{53}");
    private final byte[] pepper;
    private final BCryptPasswordEncoder bcrypt;

    public PepperedPasswordEncoder(String secret, int cost) {
        pepper = SecretKey.decode(secret, "PASSWORD_PEPPER");
        if (cost < 10 || cost > 16) throw new IllegalArgumentException("BCrypt cost must be 10-16");
        bcrypt = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, cost);
    }
    private String preprocess(CharSequence raw) {
        return Base64.getEncoder().encodeToString(SecretKey.hmac("HmacSHA384", pepper, raw.toString()));
    }
    @Override public String encode(CharSequence raw) {
        if (raw == null || !PasswordPolicy.valid(raw.toString())) throw new IllegalArgumentException("Invalid password length or Unicode");
        return PREFIX + bcrypt.encode(preprocess(raw));
    }
    @Override public boolean matches(CharSequence raw, String encoded) {
        if (raw == null || !PasswordPolicy.valid(raw.toString()) || encoded == null || !encoded.startsWith(PREFIX)) return false;
        String hash = encoded.substring(PREFIX.length());
        return HASH.matcher(hash).matches() && bcrypt.matches(preprocess(raw), hash);
    }
}
