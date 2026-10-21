package com.dev.julian09.financeserver.sync.job;

import java.time.Clock;
import java.time.Duration;

import com.dev.julian09.financeserver.ledger.repo.ChangeLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** REQ-HIS-04, data-model §«Trabajos programados»: daily, drop history entries older than 90 days. */
@Component
public class ChangeLogPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ChangeLogPurgeJob.class);

    private final ChangeLogRepository changeLog;
    private final Clock clock;

    public ChangeLogPurgeJob(ChangeLogRepository changeLog, Clock clock) {
        this.changeLog = changeLog;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public int purge() {
        int deleted = changeLog.deleteChangedBefore(clock.instant().minus(Duration.ofDays(90)));
        log.info("Purged {} change log entries older than 90 days", deleted);
        return deleted;
    }
}
