package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.DeferredPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeferredPurchaseRepository extends JpaRepository<DeferredPurchase, UUID> {
}
