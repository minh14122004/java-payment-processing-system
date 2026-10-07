package com.example.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

public final class ApiErrors {
    private ApiErrors() {}
    public record Body(String code, String message, Instant timestamp) {}
    public static Body body(ApiException error) { return new Body(error.code(), error.getMessage(), Instant.now()); }
    public static void write(ObjectMapper mapper, HttpServletResponse response, ApiException error) throws IOException {
        response.setStatus(error.status()); response.setContentType("application/json");
        if (error.retryAfter() != null) response.setHeader("Retry-After", error.retryAfter().toString());
        mapper.writeValue(response.getOutputStream(), body(error));
    }
}
