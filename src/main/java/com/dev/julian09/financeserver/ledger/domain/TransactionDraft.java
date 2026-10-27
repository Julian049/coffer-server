package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A transaction to review, read from a bank email (V16, ADR-0014); written by the mail module through JDBC. */
@Entity
@Table(name = "transaction_drafts")
public class TransactionDraft extends SyncableEntity {

    @Column(nullable = false, length = 12)
    private String source;

    @Column(nullable = false, length = 8)
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 200)
    private String merchant;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "last_digits", length = 4)
    private String lastDigits;

    protected TransactionDraft() {
    }

    public String getSource() { return source; }

    public String getType() { return type; }

    public BigDecimal getAmount() { return amount; }

    public Instant getOccurredAt() { return occurredAt; }

    public String getMerchant() { return merchant; }

    public String getLastDigits() { return lastDigits; }
}
