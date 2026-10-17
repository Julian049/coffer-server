package com.dev.julian09.financeserver.sync.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One page of changes; {@code remaining} only on the initial download (since = 0) to drive the progress bar. */
public record PullResponse(List<PulledChange> changes, long nextCursor, boolean hasMore,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Long remaining) {
}
