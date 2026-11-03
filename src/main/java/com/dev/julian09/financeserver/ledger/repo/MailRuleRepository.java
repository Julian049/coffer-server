package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.MailRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailRuleRepository extends JpaRepository<MailRule, UUID> {
}
