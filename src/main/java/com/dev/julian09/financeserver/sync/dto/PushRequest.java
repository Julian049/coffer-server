package com.dev.julian09.financeserver.sync.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/v1/sync/push. More than 200 changes is a 413 (checked by PushService, not here). */
public record PushRequest(@NotNull @Size(min = 8, max = 64) String deviceId, @NotEmpty List<@Valid @NotNull IncomingChange> changes) {
}
