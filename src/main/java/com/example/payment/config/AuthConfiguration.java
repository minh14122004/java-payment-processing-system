package com.example.payment.config;

import com.example.payment.security.*;
import com.example.payment.service.OtpCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Arrays;

@Configuration
@EnableScheduling
public class AuthConfiguration {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder(@Value("${app.auth.password-pepper}") String pepper,
            @Value("${app.auth.otp-key}") String otpKey, @Value("${app.auth.bcrypt-cost:12}") int cost) {
        if (Arrays.equals(SecretKey.decode(pepper, "PASSWORD_PEPPER"), SecretKey.decode(otpKey, "OTP_HMAC_KEY")))
            throw new IllegalArgumentException("Password and OTP keys must be different");
        return new PepperedPasswordEncoder(pepper, cost);
    }
    @Bean public OtpDigester otpDigester(@Value("${app.auth.otp-key}") String key) { return new OtpDigester(key); }
    @Bean public OtpCodeGenerator otpCodeGenerator() {
        var random = new SecureRandom();
        return () -> String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }
}
