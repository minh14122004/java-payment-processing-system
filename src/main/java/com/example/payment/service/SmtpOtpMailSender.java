package com.example.payment.service;

import com.example.payment.enums.OtpPurpose;
import com.example.payment.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class SmtpOtpMailSender implements OtpMailSender {
    private final JavaMailSender mail;
    private final String from;
    public SmtpOtpMailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) { this.mail = mail; this.from = from; }
    @Override public void send(String email, OtpPurpose purpose, String code, Instant expiresAt) {
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Payment demo - " + purpose.name());
        message.setText("Your " + purpose.name() + " code is: " + code + "\nExpires at " + expiresAt + " (UTC).\nDo not share this code.");
        try { mail.send(message); }
        catch (RuntimeException ex) { throw new ApiException(503, "EMAIL_DELIVERY_FAILED", "Unable to send verification email"); }
    }
}
