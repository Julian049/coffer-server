package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.ChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {
}
