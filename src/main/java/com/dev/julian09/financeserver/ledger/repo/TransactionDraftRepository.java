package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.TransactionDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionDraftRepository extends JpaRepository<TransactionDraft, UUID> {
}
