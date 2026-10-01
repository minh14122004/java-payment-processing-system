package com.example.payment.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValidationTest {

    private static final Instant VERIFIED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void normalizesEmailIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            User user = new User("  INFO@EXAMPLE.TEST  ", "  Demo User  ", "encoded-hash", VERIFIED_AT);

            assertThat(user.getEmail()).isEqualTo("info@example.test");
            assertThat(user.getDisplayName()).isEqualTo("Demo User");
            assertThat(user.getCredentialVersion()).isZero();
            assertThat(validator.validate(user)).isEmpty();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-an-email", "name@"})
    void rejectsInvalidEmail(String email) {
        assertInvalidProperty(new User(email, "Demo", "encoded-hash", VERIFIED_AT), "email");
    }

    @Test
    void validatesProfileHashAndVerificationTimestamp() {
        assertInvalidProperty(new User("demo@example.test", " ", "hash", VERIFIED_AT), "displayName");
        assertInvalidProperty(new User("demo@example.test", "a".repeat(101), "hash", VERIFIED_AT), "displayName");
        assertInvalidProperty(new User("demo@example.test", "Demo", null, VERIFIED_AT), "passwordHash");
        assertInvalidProperty(new User("demo@example.test", "Demo", " ", VERIFIED_AT), "passwordHash");
        assertInvalidProperty(new User("demo@example.test", "Demo", "h".repeat(256), VERIFIED_AT), "passwordHash");
        assertInvalidProperty(new User("demo@example.test", "Demo", "hash", null), "emailVerifiedAt");
    }

    @Test
    void oneUserCanOwnMultipleAccountsWithoutReassigningOwnership() {
        User user = new User("demo@example.test", "Demo", "encoded-hash", VERIFIED_AT);
        Account first = new Account(user, "First");
        Account second = new Account(user, "Second");

        assertThat(user.getAccounts()).containsExactly(first, second);
        assertThat(first.getUser()).isSameAs(user);
        assertThat(second.getUser()).isSameAs(user);
        assertThatThrownBy(() -> user.getAccounts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new Account(null, "Orphan")).isInstanceOf(NullPointerException.class);
        assertInvalidProperty(new Account(), "user");
    }

    @Test
    void doesNotSerializePasswordHashOrAccountCollection() throws Exception {
        User user = new User("demo@example.test", "Demo", "encoded-hash", VERIFIED_AT);
        new Account(user, "Demo");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash", "encoded-hash", "accounts", "username");
        assertThat(json).contains("demo@example.test", "displayName");
    }

    @Test
    void preservesCreationAndVerificationTimestamps() {
        User user = new User("demo@example.test", "Demo", "encoded-hash", VERIFIED_AT);
        Instant before = Instant.now();
        user.onCreate();
        Instant createdAt = user.getCreatedAt();
        user.onCreate();

        assertThat(createdAt).isBetween(before, Instant.now());
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(VERIFIED_AT);
    }

    private static void assertInvalidProperty(Object entity, String property) {
        assertThat(validator.validate(entity))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }
}
