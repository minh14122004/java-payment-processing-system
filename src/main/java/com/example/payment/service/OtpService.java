package com.example.payment.service;

import com.example.payment.dto.AuthDtos.*;
import com.example.payment.entity.*;
import com.example.payment.enums.OtpPurpose;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.*;
import com.example.payment.security.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

@Service
public class OtpService {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final EmailOtpRepository challenges;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final OtpMailSender mail;
    private final OtpCodeGenerator codes;
    private final OtpDigester digester;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OtpService(UserRepository users, AccountRepository accounts, EmailOtpRepository challenges,
            PasswordEncoder encoder, AuthService auth, OtpMailSender mail, OtpCodeGenerator codes,
            OtpDigester digester, Clock clock, PlatformTransactionManager manager) {
        this.users=users; this.accounts=accounts; this.challenges=challenges; this.encoder=encoder;
        this.auth=auth; this.mail=mail; this.codes=codes; this.digester=digester; this.clock=clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private <T> T tx(Supplier<T> work) { return transaction.execute(status -> work.get()); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private ApiException duplicate() { return new ApiException(409,"EMAIL_ALREADY_REGISTERED","Email already registered"); }

    // All secrets remain internal; do not use generated record toString methods here.
    private static final class Issue {
        final String email, name, hash;
        final OtpPurpose purpose;
        final UUID userId, previous;
        final Integer version;
        Issue(String email, OtpPurpose purpose, UUID userId, Integer version, UUID previous, String name, String hash) {
            this.email=email; this.purpose=purpose; this.userId=userId; this.version=version;
            this.previous=previous; this.name=name; this.hash=hash;
        }
    }
    public Challenge register(Register input) {
        if (users.existsByEmail(input.email())) throw duplicate();
        return issue(new Issue(input.email(), OtpPurpose.REGISTRATION, null, null, null,
                input.displayName(), encoder.encode(input.password())));
    }
    public Challenge requestChange(AuthPrincipal principal, String currentPassword) {
        User user = auth.verifyCurrent(principal, currentPassword);
        return issue(new Issue(user.getEmail(), OtpPurpose.PASSWORD_CHANGE, user.getId(), user.getCredentialVersion(), null, null, null));
    }
    public Challenge requestReset(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ApiException(404,"EMAIL_NOT_FOUND","Email is not registered"));
        return issue(new Issue(email, OtpPurpose.PASSWORD_RESET, user.getId(), user.getCredentialVersion(), null, null, null));
    }
    public Challenge resend(UUID challengeId, AuthPrincipal principal) {
        EmailOtpChallenge current = challenges.findByChallengeId(challengeId).orElseThrow(ApiException::otp);
        if (current.getPurpose() == OtpPurpose.PASSWORD_CHANGE) {
            User user = auth.current(principal);
            if (!user.getId().equals(current.getUserId()) || user.getCredentialVersion() != current.getCredentialVersion()) throw ApiException.otp();
        }
        return issue(new Issue(current.getEmail(), current.getPurpose(), current.getUserId(), current.getCredentialVersion(),
                challengeId, null, null));
    }
    private User eligible(Issue input) {
        if (input.purpose == OtpPurpose.REGISTRATION) {
            if (users.existsByEmail(input.email)) throw duplicate();
            return null;
        }
        User user = users.lockById(input.userId).orElseThrow(ApiException::otp);
        if (!user.getEmail().equals(input.email) || user.getCredentialVersion() != input.version) throw ApiException.otp();
        return user;
    }
    private void checkPrevious(Issue input, EmailOtpChallenge slot) {
        if (input.previous != null && (!input.previous.equals(slot.getChallengeId()) || slot.getOtpDigest() == null || slot.getConsumedAt() != null))
            throw ApiException.otp();
    }
    private Challenge issue(Issue input) {
        // Reserve in its own committed transaction; SMTP failures cannot reset limits.
        Instant reservation = tx(() -> {
            eligible(input); // User lock always precedes slot lock.
            Instant time = now();
            challenges.ensureSlot(UUID.randomUUID(), input.email, input.purpose.name(), input.userId, input.version, time);
            EmailOtpChallenge slot = challenges.lockSlot(input.email, input.purpose).orElseThrow(ApiException::otp);
            checkPrevious(input, slot);
            Instant allowed = time;
            if (slot.getLastSentAt() != null && slot.getLastSentAt().plusSeconds(60).isAfter(allowed)) allowed = slot.getLastSentAt().plusSeconds(60);
            if (slot.getWindowStartedAt() != null && time.isBefore(slot.getWindowStartedAt().plusSeconds(3600)) && slot.getWindowSendCount() >= 5
                    && slot.getWindowStartedAt().plusSeconds(3600).isAfter(allowed)) allowed = slot.getWindowStartedAt().plusSeconds(3600);
            if (allowed.isAfter(time)) throw ApiException.limited(Duration.between(time, allowed).toSeconds() + 1);
            slot.reserve(time);
            return time;
        });
        return tx(() -> {
            eligible(input);
            EmailOtpChallenge slot = challenges.lockSlot(input.email, input.purpose).orElseThrow(ApiException::otp);
            // A delayed issuance cannot overwrite a later reservation or confirmation.
            if (!reservation.equals(slot.getLastSentAt())) throw ApiException.otp();
            checkPrevious(input, slot);
            String name = input.previous == null ? input.name : slot.getPendingDisplayName();
            String hash = input.previous == null ? input.hash : slot.getPendingPasswordHash();
            UUID id = UUID.randomUUID(); String code = codes.generate(); Instant issued = now();
            mail.send(input.email, input.purpose, code, issued.plusSeconds(300));
            slot.activate(id, digester.digest(id, code), issued, input.version, name, hash);
            challenges.flush();
            return new Challenge(id, slot.getExpiresAt(), slot.getLastSentAt().plusSeconds(60));
        });
    }
    private boolean verify(EmailOtpChallenge slot, UUID id, String code) {
        if (!id.equals(slot.getChallengeId()) || !slot.usable(now())) return false;
        if (!digester.matches(id, code, slot.getOtpDigest())) { slot.failAttempt(); return false; }
        return true;
    }
    public Registration confirmRegistration(UUID id, String code) {
        EmailOtpChallenge snapshot = challenges.findByChallengeId(id).orElseThrow(ApiException::otp);
        if (snapshot.getPurpose() != OtpPurpose.REGISTRATION) throw ApiException.otp();
        Registration result = tx(() -> {
            var slot = challenges.lockSlot(snapshot.getEmail(), OtpPurpose.REGISTRATION).orElseThrow(ApiException::otp);
            if (!verify(slot, id, code)) return null; // Commit wrong-code counter, then throw outside transaction.
            if (users.existsByEmail(slot.getEmail())) throw duplicate();
            User user = users.saveAndFlush(new User(slot.getEmail(), slot.getPendingDisplayName(), slot.getPendingPasswordHash(), now()));
            Account account = accounts.saveAndFlush(new Account(user, user.getDisplayName()));
            slot.consume(now());
            return new Registration(UserView.of(user), AccountView.of(account));
        });
        if (result == null) throw ApiException.otp();
        return result;
    }
    public void confirmPassword(OtpPurpose purpose, AuthPrincipal principal, PasswordConfirm input) {
        EmailOtpChallenge snapshot = challenges.findByChallengeId(input.challengeId()).orElseThrow(ApiException::otp);
        if (snapshot.getPurpose() != purpose || purpose == OtpPurpose.REGISTRATION) throw ApiException.otp();
        if (purpose == OtpPurpose.PASSWORD_CHANGE && (principal == null || !principal.userId().equals(snapshot.getUserId()))) throw ApiException.otp();
        String encoded = encoder.encode(input.newPassword());
        boolean success = tx(() -> {
            User user = users.lockById(snapshot.getUserId()).orElseThrow(ApiException::otp);
            if (purpose == OtpPurpose.PASSWORD_CHANGE && user.getCredentialVersion() != principal.credentialVersion()) throw ApiException.authentication();
            var slot = challenges.lockSlot(snapshot.getEmail(), purpose).orElseThrow(ApiException::otp);
            if (!Objects.equals(slot.getCredentialVersion(), user.getCredentialVersion()) || !verify(slot, input.challengeId(), input.otp())) return false;
            user.changePassword(encoded); users.flush(); slot.consume(now());
            return true;
        });
        if (!success) throw ApiException.otp();
    }
    @Scheduled(initialDelayString="${app.auth.cleanup-ms:60000}", fixedDelayString="${app.auth.cleanup-ms:60000}")
    public void cleanup() {
        tx(() -> { Instant time = now(); challenges.clearExpired(time); challenges.deleteInactive(time.minusSeconds(3600)); return null; });
    }
}
