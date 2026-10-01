package com.example.payment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(name = "email", nullable = false, unique = true, length = 254, updatable = false)
    private String email;

    @NotBlank
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Column(name = "email_verified_at", nullable = false, updatable = false)
    private Instant emailVerifiedAt;

    @Min(0)
    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Account> accounts = new ArrayList<>();

    /**
     * Creates a verified user. The registration service must supply an already
     * encoded password and create the first account in the same transaction.
     */
    public User(String email, String displayName, String passwordHash, Instant emailVerifiedAt) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        this.displayName = displayName == null ? null : displayName.strip();
        this.passwordHash = passwordHash;
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    void attachAccount(Account account) {
        if (account.getUser() != this) {
            throw new IllegalArgumentException("Account belongs to another user");
        }
        if (!accounts.contains(account)) {
            accounts.add(account);
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
