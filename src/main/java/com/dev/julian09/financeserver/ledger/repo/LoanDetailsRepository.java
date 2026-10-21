package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.LoanDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanDetailsRepository extends JpaRepository<LoanDetails, UUID> {
}
