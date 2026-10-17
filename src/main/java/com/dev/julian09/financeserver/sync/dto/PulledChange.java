package com.dev.julian09.financeserver.sync.dto;

import java.time.Instant;
import java.util.UUID;

/** A change as the server sends it: the envelope, the entity data and its version (openapi.yml PulledChange). */
public record PulledChange(String entity, UUID id, Instant createdAt, Instant updatedAt, Instant deletedAt,
                           EntityData data, long version) {
}
