package com.dev.julian09.financeserver.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dev.julian09.financeserver.shared.error.RateLimitException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sliding window in memory: 5 login attempts per minute per (IP, identifier) (ADR-0007).
 * shortcut: per instance; fine for the single Render instance, needs a shared store if the API ever scales out.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts this attempt or throws 429 with how long to wait. */
    public void check(String ip, String identifier) {
        Instant now = clock.instant();
        String key = ip + "|" + identifier.trim().toLowerCase(Locale.ROOT);
        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            if (window.size() >= MAX_ATTEMPTS) {
                throw new RateLimitException(Duration.between(now, window.peekFirst().plus(WINDOW)));
            }
            window.addLast(now);
        }
    }

    // Keys of people who stopped trying would otherwise stay in memory forever
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> window, Instant now) {
        while (!window.isEmpty() && !window.peekFirst().plus(WINDOW).isAfter(now)) {
            window.pollFirst();
        }
    }
}
