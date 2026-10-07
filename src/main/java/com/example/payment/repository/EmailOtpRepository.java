package com.example.payment.repository;

import com.example.payment.entity.EmailOtpChallenge;
import com.example.payment.enums.OtpPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface EmailOtpRepository extends JpaRepository<EmailOtpChallenge, UUID> {
    Optional<EmailOtpChallenge> findByChallengeId(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailOtpChallenge c where c.email = :email and c.purpose = :purpose")
    Optional<EmailOtpChallenge> lockSlot(String email, OtpPurpose purpose);

    @Modifying
    @Query(value="""
        INSERT INTO email_otp_challenges
          (id,email,purpose,user_id,credential_version,failed_attempts,window_send_count,created_at)
        VALUES (:id,:email,:purpose,:userId,:version,0,0,:now)
        ON CONFLICT (email,purpose) DO NOTHING
        """, nativeQuery=true)
    void ensureSlot(UUID id, String email, String purpose, UUID userId, Integer version, Instant now);

    @Modifying
    @Query(value="""
        UPDATE email_otp_challenges SET otp_digest=NULL, pending_display_name=NULL,
          pending_password_hash=NULL, consumed_at=:now
        WHERE otp_digest IS NOT NULL AND expires_at <= :now
        """, nativeQuery=true)
    int clearExpired(Instant now);

    @Modifying
    @Query(value="""
        DELETE FROM email_otp_challenges WHERE otp_digest IS NULL
          AND (window_started_at IS NULL OR window_started_at <= :cutoff)
        """, nativeQuery=true)
    int deleteInactive(Instant cutoff);
}
