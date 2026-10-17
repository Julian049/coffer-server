package com.dev.julian09.financeserver.sync.dto;

import java.util.List;

public record PushResponse(List<ChangeResult> results) {
}
