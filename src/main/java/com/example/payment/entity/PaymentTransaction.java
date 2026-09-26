package com.example.payment.entity;

import com.example.payment.enums.Currency;
import com.example.payment.enums.TransactionStatus;
import com.example.payment.enums.TransactionType;
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
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_transactions_source_history", columnList = "source_account_id, created_at, id"),
        @Index(name = "idx_transactions_destination_history", columnList = "destination_account_id, created_at, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", updatable = false)
    private Account destinationAccount;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 19, fraction = 0)
    @Column(name = "amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency = Currency.VND;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 7, updatable = false)
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentTransaction(TransactionType type, Account sourceAccount,
                              Account destinationAccount, BigDecimal amount) {
        this.type = type;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        // Preserve nonzero fractional digits so Bean Validation can reject them.
        this.amount = amount == null ? null : amount.stripTrailingZeros();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
