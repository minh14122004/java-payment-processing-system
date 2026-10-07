package com.example.payment.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long retryAfter;
    public ApiException(int status, String code, String message) { this(status, code, message, null); }
    public ApiException(int status, String code, String message, Long retryAfter) {
        super(message); this.status = status; this.code = code; this.retryAfter = retryAfter;
    }
    public int status() { return status; }
    public String code() { return code; }
    public Long retryAfter() { return retryAfter; }
    public static ApiException otp() { return new ApiException(400, "OTP_INVALID", "OTP is invalid or no longer usable"); }
    public static ApiException authentication() { return new ApiException(401, "AUTHENTICATION_REQUIRED", "Authentication required"); }
    public static ApiException credentials() { return new ApiException(401, "INVALID_CREDENTIALS", "Invalid credentials"); }
    public static ApiException limited(long seconds) { return new ApiException(429, "RATE_LIMITED", "Try again later", Math.max(1, seconds)); }
}
