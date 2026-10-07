package com.example.payment.service;

import com.example.payment.enums.OtpPurpose;
import java.time.Instant;

public interface OtpMailSender {
    void send(String email, OtpPurpose purpose, String code, Instant expiresAt);
}
