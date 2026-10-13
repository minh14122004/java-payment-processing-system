package com.example.payment.service;

import com.example.payment.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal MAX = new BigDecimal("9999999999999999999");
    private Money() {}

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX) > 0)
            throw invalid();
        try { return value.setScale(0, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw invalid(); }
    }

    public static BigDecimal credit(BigDecimal balance, BigDecimal amount) {
        var result = balance.add(amount);
        if (result.compareTo(MAX) > 0)
            throw new ApiException(422, "BALANCE_LIMIT_EXCEEDED", "Balance limit exceeded");
        return result;
    }

    public static BigDecimal debit(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0)
            throw new ApiException(422, "INSUFFICIENT_FUNDS", "Insufficient funds");
        return balance.subtract(amount);
    }

    private static ApiException invalid() { return new ApiException(400, "INVALID_REQUEST", "Amount must be positive whole VND within the supported limit"); }
}
