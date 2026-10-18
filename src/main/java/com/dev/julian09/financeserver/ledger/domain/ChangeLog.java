package com.dev.julian09.financeserver.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** History of a transaction (REQ-HIS-01): the client writes it with the change and it syncs like any row. */
@Entity
@Table(name = "change_log")
public class ChangeLog extends SyncableEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, length = 7)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ChangeLog() {
    }

    public UUID getTransactionId() { return transactionId; }

    public String getAction() { return action; }

    public String getBeforeValue() { return beforeValue; }

    public String getAfterValue() { return afterValue; }

    public Instant getChangedAt() { return changedAt; }
}
