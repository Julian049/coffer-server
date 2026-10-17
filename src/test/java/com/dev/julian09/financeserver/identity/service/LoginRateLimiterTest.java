package com.dev.julian09.financeserver.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.dev.julian09.financeserver.shared.error.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");

        @Override
        public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void fiveAttemptsPerMinutePerIpAndIdentifier() {
        for (int i = 0; i < 5; i++) {
            limiter.check("1.1.1.1", "julian");
        }
        assertThatThrownBy(() -> limiter.check("1.1.1.1", "JULIAN"))
                .isInstanceOf(RateLimitException.class)
                .satisfies(e -> assertThat(((RateLimitException) e).retryAfter()).isEqualTo(Duration.ofMinutes(1)));
        assertThatNoException().isThrownBy(() -> limiter.check("2.2.2.2", "julian"));
        assertThatNoException().isThrownBy(() -> limiter.check("1.1.1.1", "otra"));
    }

    @Test
    void theWindowSlides() {
        for (int i = 0; i < 5; i++) {
            limiter.check("1.1.1.1", "julian");
            clock.now = clock.now.plusSeconds(10);
        }
        // 50 s later the first attempt is still inside the minute; at 60 s it leaves
        assertThatThrownBy(() -> limiter.check("1.1.1.1", "julian")).isInstanceOf(RateLimitException.class);
        clock.now = clock.now.plusSeconds(10);
        assertThatNoException().isThrownBy(() -> limiter.check("1.1.1.1", "julian"));
    }
}
