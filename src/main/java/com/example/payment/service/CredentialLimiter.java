package com.example.payment.service;

import com.example.payment.exception.ApiException;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

@Component
public class CredentialLimiter {
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    public CredentialLimiter(Clock clock) { this.clock = clock; }
    // Bounded, serialized checks also prevent concurrent requests exceeding the cap.
    public synchronized void verify(String email, BooleanSupplier check) {
        Instant now = clock.instant();
        failures.values().forEach(queue -> { while (!queue.isEmpty() && !queue.peek().isAfter(now.minusSeconds(900))) queue.remove(); });
        failures.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!failures.containsKey(email) && failures.size() >= 10000) throw ApiException.limited(60);
        Deque<Instant> queue = failures.computeIfAbsent(email, ignored -> new ArrayDeque<>());
        if (queue.size() >= 5) throw ApiException.limited(Duration.between(now, queue.peek().plusSeconds(900)).toSeconds() + 1);
        if (!check.getAsBoolean()) { queue.add(now); throw ApiException.credentials(); }
    }
}
