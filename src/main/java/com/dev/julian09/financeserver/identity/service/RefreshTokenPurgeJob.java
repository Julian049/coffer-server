package com.dev.julian09.financeserver.identity.service;

import java.time.Clock;
import java.time.Duration;

import com.dev.julian09.financeserver.identity.repo.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** data-model §4.5: daily, drop refresh tokens expired more than 7 days ago. */
@Component
public class RefreshTokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public RefreshTokenPurgeJob(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public int purge() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
        log.info("Purged {} expired refresh tokens", deleted);
        return deleted;
    }
}
