package com.dev.julian09.financeserver.mail;

import java.util.UUID;

/** Published after a draft is stored; whoever listens (notify, ADR-0016) is unknown to this module. */
public record DraftCreatedEvent(UUID userId) {
}
