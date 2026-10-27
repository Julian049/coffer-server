package com.dev.julian09.financeserver.mail;

import java.math.BigDecimal;
import java.time.Instant;

/** What a reader takes from a bank email (REQ-COR-02); [merchant] and [lastDigits] may be missing. */
public record ParsedReceipt(String source, String type, BigDecimal amount, Instant occurredAt, String merchant, String lastDigits) {
}
