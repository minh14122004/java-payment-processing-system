package com.example.payment.entity;

import com.example.payment.enums.Currency;
import com.example.payment.enums.TransactionStatus;
import com.example.payment.enums.TransactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntityValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void newAccountHasZeroBalanceAndVndCurrency() {
        Account account = new Account("  Demo Account  ");

        assertThat(account.getAccountHolderName()).isEqualTo("Demo Account");
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo(Currency.VND);
        assertThat(validator.validate(account)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankAccountHolderName(String name) {
        assertInvalidProperty(new Account(name), "accountHolderName");
    }

    @Test
    void enforcesAccountHolderNameLength() {
        assertThat(validator.validate(new Account("a".repeat(100)))).isEmpty();
        assertInvalidProperty(new Account("a".repeat(101)), "accountHolderName");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "0.1", "1000.5", "10000000000000000000", "1E+19"})
    void rejectsInvalidBalance(String value) {
        Account account = new Account("Demo Account");
        account.setBalance(decimal(value));

        assertInvalidProperty(account, "balance");
        if (value != null) {
            assertThat(account.getBalance()).isEqualByComparingTo(value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "1000.0", "1E+3", "9999999999999999999.00"})
    void acceptsIntegralBalancesWithinRange(String value) {
        Account account = new Account("Demo Account");
        account.setBalance(new BigDecimal(value));

        assertThat(validator.validate(account)).isEmpty();
        assertThat(account.getBalance()).isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.01", "1000.5", "10000000000000000000", "1E+19"})
    void rejectsInvalidTransactionAmount(String value) {
        PaymentTransaction transaction = deposit(decimal(value));

        assertInvalidProperty(transaction, "amount");
        if (value != null) {
            assertThat(transaction.getAmount()).isEqualByComparingTo(value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1000.0", "1E+3", "9999999999999999999.00"})
    void acceptsIntegralTransactionAmountsAndSetsDefaults(String value) {
        PaymentTransaction transaction = deposit(new BigDecimal(value));

        assertThat(validator.validate(transaction)).isEmpty();
        assertThat(transaction.getAmount()).isEqualByComparingTo(value);
        assertThat(transaction.getCurrency()).isEqualTo(Currency.VND);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getSourceAccount()).isNull();
    }

    @Test
    void requiresTransactionType() {
        PaymentTransaction transaction = new PaymentTransaction(
                null, null, new Account("Destination"), BigDecimal.ONE);

        assertInvalidProperty(transaction, "type");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void requiresNonBlankIdempotencyFields(String value) {
        assertInvalidProperty(new IdempotencyRecord(value, "fingerprint", transfer()), "idempotencyKey");
        assertInvalidProperty(new IdempotencyRecord("key", value, transfer()), "requestFingerprint");
    }

    @Test
    void enforcesIdempotencyFieldLengthsAndRequiredTransaction() {
        assertThat(validator.validate(new IdempotencyRecord(
                "k".repeat(128), "f".repeat(96), transfer()))).isEmpty();
        assertInvalidProperty(new IdempotencyRecord(
                "k".repeat(129), "fingerprint", transfer()), "idempotencyKey");
        assertInvalidProperty(new IdempotencyRecord(
                "key", "f".repeat(97), transfer()), "requestFingerprint");
        assertInvalidProperty(new IdempotencyRecord("key", "fingerprint", null), "transaction");
    }

    @Test
    void preservesIdempotencyKeyAndFingerprintExactly() {
        IdempotencyRecord record = new IdempotencyRecord("Key-AbC", "v1|source|destination|1000", transfer());

        assertThat(record.getIdempotencyKey()).isEqualTo("Key-AbC");
        assertThat(record.getRequestFingerprint()).isEqualTo("v1|source|destination|1000");
    }

    @Test
    void creationCallbacksAssignTimestampsOnce() {
        Account account = new Account("Demo Account");
        PaymentTransaction transaction = transfer();
        IdempotencyRecord record = new IdempotencyRecord("key", "fingerprint", transaction);
        Instant before = Instant.now();

        account.onCreate();
        transaction.onCreate();
        record.onCreate();

        Instant after = Instant.now();
        Instant accountCreatedAt = account.getCreatedAt();
        Instant transactionCreatedAt = transaction.getCreatedAt();
        Instant recordCreatedAt = record.getCreatedAt();
        assertThat(accountCreatedAt).isBetween(before, after);
        assertThat(transactionCreatedAt).isBetween(before, after);
        assertThat(recordCreatedAt).isBetween(before, after);

        account.onCreate();
        transaction.onCreate();
        record.onCreate();

        assertThat(account.getCreatedAt()).isEqualTo(accountCreatedAt);
        assertThat(transaction.getCreatedAt()).isEqualTo(transactionCreatedAt);
        assertThat(record.getCreatedAt()).isEqualTo(recordCreatedAt);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static PaymentTransaction deposit(BigDecimal amount) {
        return new PaymentTransaction(TransactionType.DEPOSIT, null, new Account("Destination"), amount);
    }

    private static PaymentTransaction transfer() {
        return new PaymentTransaction(TransactionType.TRANSFER,
                new Account("Source"), new Account("Destination"), BigDecimal.ONE);
    }

    private static void assertInvalidProperty(Object entity, String property) {
        assertThat(validator.validate(entity))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }
}
