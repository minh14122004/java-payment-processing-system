package com.example.payment.dto;

import com.example.payment.entity.Account;
import com.example.payment.entity.User;
import com.example.payment.security.ValidPassword;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public static String canonicalEmail(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    public record Register(@NotBlank @Email @Size(max=254) String email,
                           @ValidPassword String password, @NotBlank @Size(max=100) String displayName) {
        public Register { email = canonicalEmail(email); displayName = displayName == null ? null : displayName.strip(); }
        @Override public String toString() { return "Register[REDACTED]"; }
    }
    public record Login(@NotBlank @Email @Size(max=254) String email, @ValidPassword String password) {
        public Login { email = canonicalEmail(email); }
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    public record Verify(@NotNull UUID challengeId, @NotNull @Pattern(regexp="[0-9]{6}") String otp) {
        @Override public String toString() { return "Verify[REDACTED]"; }
    }
    public record ChangeRequest(@ValidPassword String currentPassword) {
        @Override public String toString() { return "ChangeRequest[REDACTED]"; }
    }
    public record ResetRequest(@NotBlank @Email @Size(max=254) String email) {
        public ResetRequest { email = canonicalEmail(email); }
    }
    public record PasswordConfirm(@NotNull UUID challengeId, @NotNull @Pattern(regexp="[0-9]{6}") String otp,
                                  @ValidPassword String newPassword) {
        @Override public String toString() { return "PasswordConfirm[REDACTED]"; }
    }
    public record Resend(@NotNull UUID challengeId) {}
    public record Challenge(UUID challengeId, Instant expiresAt, Instant resendAvailableAt) {}
    public record UserView(UUID id, String email, String displayName, Instant emailVerifiedAt, Instant createdAt) {
        public static UserView of(User user) { return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getEmailVerifiedAt(), user.getCreatedAt()); }
    }
    public record AccountView(UUID id, UUID userId, String accountHolderName, BigDecimal balance, String currency, Instant createdAt) {
        public static AccountView of(Account account) { return new AccountView(account.getId(), account.getUser().getId(), account.getAccountHolderName(), account.getBalance(), account.getCurrency().name(), account.getCreatedAt()); }
    }
    public record Balance(UUID accountId, BigDecimal balance, String currency) {}
    public record Registration(UserView user, AccountView account) {}
    public record CsrfView(String token, String headerName) {}
}
