package com.dev.julian09.financeserver.ledger.repo;

import java.time.Instant;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.ChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {

    @Modifying
    @Query("delete from ChangeLog c where c.changedAt < :cutoff")
    int deleteChangedBefore(@Param("cutoff") Instant cutoff);
}
