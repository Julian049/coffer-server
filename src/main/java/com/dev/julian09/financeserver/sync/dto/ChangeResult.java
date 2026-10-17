package com.dev.julian09.financeserver.sync.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Outcome of one change; {@code version} only when APPLIED, {@code code} and {@code detail} only when REJECTED. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeResult(UUID id, String status, Long version, String code, String detail) {

    public static ChangeResult applied(UUID id, long version) {
        return new ChangeResult(id, "APPLIED", version, null, null);
    }

    public static ChangeResult stale(UUID id) {
        return new ChangeResult(id, "STALE", null, null, null);
    }

    public static ChangeResult rejected(UUID id, String code, String detail) {
        return new ChangeResult(id, "REJECTED", null, code, detail);
    }
}
