package com.example.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.example.payment.enums.IdempotencyOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(
        name = "uq_idempotency_operation_key", columnNames = {"operation", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 128)
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 10, updatable = false)
    private IdempotencyOperation operation = IdempotencyOperation.TRANSFER;

    @NotBlank
    @Size(max = 96)
    @Column(name = "request_fingerprint", nullable = false, length = 96, updatable = false)
    private String requestFingerprint;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private PaymentTransaction transaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(String idempotencyKey, String requestFingerprint,
                             PaymentTransaction transaction) {
        this(IdempotencyOperation.TRANSFER, idempotencyKey, requestFingerprint, transaction);
    }

    public IdempotencyRecord(IdempotencyOperation operation, String idempotencyKey,
                             String requestFingerprint, PaymentTransaction transaction) {
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.transaction = transaction;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
