package com.example.payment.entity;

import com.example.payment.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = @Index(name = "idx_accounts_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @NotBlank
    @Size(max = 100)
    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 19, fraction = 0)
    @Column(name = "balance", nullable = false, precision = 19, scale = 0)
    private BigDecimal balance = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency = Currency.VND;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Account(User user, String accountHolderName) {
        this.user = Objects.requireNonNull(user, "Account owner is required");
        this.accountHolderName = accountHolderName == null ? null : accountHolderName.strip();
        user.attachAccount(this);
    }

    public void setBalance(BigDecimal balance) {
        // Remove insignificant zeros without rounding fractional input before validation.
        this.balance = balance == null ? null : balance.stripTrailingZeros();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
