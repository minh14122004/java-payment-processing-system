package com.example.payment.auth;

import com.example.payment.enums.OtpPurpose;
import com.example.payment.exception.ApiException;
import com.example.payment.service.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.time.*;
import java.util.concurrent.CopyOnWriteArrayList;

@TestConfiguration
public class AuthTestConfiguration {
    public static final String PEPPER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    public static final String OTP_KEY = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    @Bean @Primary public MutableClock testClock() { return new MutableClock(); }
    @Bean @Primary public RecordingMail testMail() { return new RecordingMail(); }
    @Bean @Primary public OtpCodeGenerator testCodes() { return () -> "000007"; }
    public static class MutableClock extends Clock {
        private volatile Instant time = Instant.parse("2026-10-06T12:00:00Z");
        public void advance(long seconds) { time=time.plusSeconds(seconds); }
        public void reset() { time=Instant.parse("2026-10-06T12:00:00Z"); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
    public static class RecordingMail implements OtpMailSender {
        public record Delivery(String email, OtpPurpose purpose, String code, Instant expires) {
            @Override public String toString() { return "Delivery[REDACTED]"; }
        }
        public final CopyOnWriteArrayList<Delivery> deliveries = new CopyOnWriteArrayList<>();
        public volatile boolean fail;
        @Override public void send(String email, OtpPurpose purpose, String code, Instant expires) {
            if (fail) throw new ApiException(503,"EMAIL_DELIVERY_FAILED","Test mail unavailable");
            deliveries.add(new Delivery(email,purpose,code,expires));
        }
        public void reset() { fail=false; deliveries.clear(); }
        public String lastCode() { return deliveries.getLast().code(); }
    }
}
