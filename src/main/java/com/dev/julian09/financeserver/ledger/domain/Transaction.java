package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Expense, income or transfer (V6). Amounts are always positive; the type gives the direction. */
@Entity
@Table(name = "transactions")
public class Transaction extends SyncableEntity {

    @Column(nullable = false, length = 8)
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "tag_id")
    private UUID tagId;

    @Column(length = 500)
    private String note;

    // Transfers only, apart from the title (note); S12-H01
    @Column(length = 200)
    private String concept;

    @Column(nullable = false)
    private short installments;

    @Column(precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(precision = 19, scale = 4)
    private BigDecimal interest;

    @Column(precision = 19, scale = 4)
    private BigDecimal fees;

    @Column(name = "installment_id")
    private UUID installmentId;

    @Column(name = "recurring_id")
    private UUID recurringId;

    @Column(name = "payment_kind", length = 12)
    private String paymentKind;

    protected Transaction() {
    }

    public String getType() { return type; }

    public BigDecimal getAmount() { return amount; }

    public Instant getOccurredAt() { return occurredAt; }

    public UUID getAccountId() { return accountId; }

    public UUID getDestinationAccountId() { return destinationAccountId; }

    public UUID getCategoryId() { return categoryId; }

    public UUID getTagId() { return tagId; }

    public String getNote() { return note; }

    public String getConcept() { return concept; }

    public short getInstallments() { return installments; }

    public BigDecimal getPrincipal() { return principal; }

    public BigDecimal getInterest() { return interest; }

    public BigDecimal getFees() { return fees; }

    public UUID getInstallmentId() { return installmentId; }

    public UUID getRecurringId() { return recurringId; }

    public String getPaymentKind() { return paymentKind; }
}
