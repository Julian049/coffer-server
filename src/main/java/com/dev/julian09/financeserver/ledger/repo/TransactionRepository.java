package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
