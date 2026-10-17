package com.dev.julian09.financeserver.ledger.repo;

import java.util.UUID;

import com.dev.julian09.financeserver.ledger.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
