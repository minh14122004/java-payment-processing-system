package com.example.payment.service;

import com.example.payment.entity.User;
import com.example.payment.repository.UserRepository;
import com.example.payment.security.AuthPrincipal;
import com.example.payment.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CredentialLimiter limiter;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder encoder, CredentialLimiter limiter) {
        this.users = users; this.encoder = encoder; this.limiter = limiter;
        dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }
    public User login(String email, String password) {
        User user = users.findByEmail(email).orElse(null);
        limiter.verify(email, () -> {
            boolean matches = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
            return user != null && matches;
        });
        return user;
    }
    public User current(AuthPrincipal principal) {
        if (principal == null) throw ApiException.authentication();
        User user = users.findById(principal.userId()).orElseThrow(ApiException::authentication);
        if (user.getCredentialVersion() != principal.credentialVersion()) throw ApiException.authentication();
        return user;
    }
    public User verifyCurrent(AuthPrincipal principal, String password) {
        User user = current(principal);
        limiter.verify(user.getEmail(), () -> encoder.matches(password, user.getPasswordHash()));
        return user;
    }
}
