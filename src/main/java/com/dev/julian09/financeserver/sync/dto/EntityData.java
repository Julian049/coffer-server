package com.dev.julian09.financeserver.sync.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** The {@code data} member of a change, one record per entity (openapi.yml *Data schemas). */
public sealed interface EntityData {

    record SettingsData(int reminderLeadDays, boolean notificationsEnabled, String homeBalanceView, int amountDecimals,
                        JsonNode homeCards) implements EntityData {
    }

    record AccountData(String name, String type, BigDecimal initialBalance, boolean includeInTotal, boolean active,
                       Instant deactivatedAt, String institution, String icon, String color, int sortOrder) implements EntityData {
    }

    record CategoryData(UUID parentId, String name, String kind, boolean isBase, String systemKey, String icon,
                        int sortOrder) implements EntityData {
    }

    record TagData(String name, boolean pinned) implements EntityData {
    }

    record TransactionData(String type, BigDecimal amount, Instant occurredAt, UUID accountId, UUID destinationAccountId,
                           UUID categoryId, UUID tagId, String note, int installments, BigDecimal principal,
                           BigDecimal interest, BigDecimal fees, UUID installmentId, UUID recurringId,
                           String paymentKind) implements EntityData {
    }
}
