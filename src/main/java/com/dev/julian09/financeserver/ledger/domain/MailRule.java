package com.dev.julian09.financeserver.ledger.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A provider (and maybe its last 4 digits) sent to one account (V18, REQ-COR-11); the phone applies it. */
@Entity
@Table(name = "mail_rules")
public class MailRule extends SyncableEntity {

    @Column(nullable = false, length = 30)
    private String source;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "last_digits", length = 4)
    private String lastDigits;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    protected MailRule() {
    }

    public String getSource() { return source; }

    public String getLastDigits() { return lastDigits; }

    public UUID getAccountId() { return accountId; }
}
