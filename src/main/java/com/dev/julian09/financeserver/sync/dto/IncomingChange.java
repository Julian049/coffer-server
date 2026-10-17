package com.dev.julian09.financeserver.sync.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** A change sent by a device; {@code data} is parsed by the handler of its entity. */
public record IncomingChange(@NotNull String entity, @NotNull UUID id, @NotNull Instant createdAt, @NotNull Instant updatedAt,
                             Instant deletedAt, @NotNull JsonNode data) {
}
