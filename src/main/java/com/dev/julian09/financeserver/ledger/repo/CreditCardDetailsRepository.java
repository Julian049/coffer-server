package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.CreditCardDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardDetailsRepository extends JpaRepository<CreditCardDetails, UUID> {
}
