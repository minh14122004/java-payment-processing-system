package com.example.payment.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrors.Body> api(ApiException error) {
        var builder = ResponseEntity.status(error.status());
        if (error.retryAfter() != null) builder.header("Retry-After", error.retryAfter().toString());
        return builder.body(ApiErrors.body(error));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrors.Body> invalid(Exception ignored) {
        return api(new ApiException(400, "INVALID_REQUEST", "Invalid request fields or JSON"));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrors.Body> notFound(Exception ignored) { return api(new ApiException(404, "NOT_FOUND", "Route not found")); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrors.Body> methodNotAllowed(Exception ignored) { return api(new ApiException(405, "METHOD_NOT_ALLOWED", "Method not supported")); }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrors.Body> conflict(Exception ignored) { return api(new ApiException(409, "CONCURRENCY_CONFLICT", "Concurrent operation; retry later")); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrors.Body> unexpected(Exception error) {
        // JDBC can leave PostgreSQL lock errors untranslated (notably SQLSTATE 55P03).
        // This runs after the service transaction has rolled back, including commit failures.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null
                    && java.util.Set.of("55P03", "40P01", "40001").contains(sql.getSQLState()))
                return conflict(error);
        }
        return api(new ApiException(500, "INTERNAL_ERROR", "Unexpected server error"));
    }
}
