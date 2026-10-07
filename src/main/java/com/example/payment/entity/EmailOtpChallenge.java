package com.example.payment.entity;

import com.example.payment.enums.OtpPurpose;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_otp_challenges", uniqueConstraints = @UniqueConstraint(columnNames={"email", "purpose"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOtpChallenge {
    @Id private UUID id;
    @Column(name="challenge_id", unique=true) private UUID challengeId;
    @Column(nullable=false, length=254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OtpPurpose purpose;
    @Column(name="user_id") private UUID userId;
    @Column(name="credential_version") private Integer credentialVersion;
    @JsonIgnore @Column(name="pending_display_name", length=100) private String pendingDisplayName;
    @JsonIgnore @Column(name="pending_password_hash", length=255) private String pendingPasswordHash;
    @JsonIgnore @Column(name="otp_digest", length=64) private String otpDigest;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="consumed_at") private Instant consumedAt;
    @Column(name="failed_attempts", nullable=false) private int failedAttempts;
    @Column(name="last_sent_at") private Instant lastSentAt;
    @Column(name="window_started_at") private Instant windowStartedAt;
    @Column(name="window_send_count", nullable=false) private int windowSendCount;
    @Column(name="created_at", nullable=false) private Instant createdAt;

    public void reserve(Instant now) {
        if (windowStartedAt == null || !now.isBefore(windowStartedAt.plusSeconds(3600))) {
            windowStartedAt = now; windowSendCount = 0;
        }
        windowSendCount++; lastSentAt = now;
    }
    public void activate(UUID challenge, String digest, Instant now, Integer version, String name, String hash) {
        challengeId = challenge; otpDigest = digest; expiresAt = now.plusSeconds(300);
        consumedAt = null; failedAttempts = 0; credentialVersion = version;
        pendingDisplayName = name; pendingPasswordHash = hash;
    }
    public boolean usable(Instant now) {
        return challengeId != null && otpDigest != null && consumedAt == null && failedAttempts < 5 && now.isBefore(expiresAt);
    }
    public void failAttempt() { failedAttempts++; }
    public void consume(Instant now) {
        consumedAt = now; otpDigest = null; pendingDisplayName = null; pendingPasswordHash = null;
    }
}
