package com.example.payment.security;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static boolean valid(String value) {
        if (value == null) return false;
        int count = value.codePointCount(0, value.length());
        if (count < 8 || count > 128) return false;
        // Reject malformed UTF-16 rather than silently replace it during UTF-8 encoding.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
