package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A card purchase in 2 or more installments (REQ-TC-05 to 07); the client builds the plan (ADR-0010). */
@Entity
@Table(name = "deferred_purchases")
public class DeferredPurchase extends SyncableEntity {

    @Column(name = "card_account_id", nullable = false)
    private UUID cardAccountId;

    @Column(name = "purchase_transaction_id", nullable = false)
    private UUID purchaseTransactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "installments_count", nullable = false)
    private short installmentsCount;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "rate_type", nullable = false, length = 2)
    private String rateType;

    @Column(name = "first_installment_interest_free", nullable = false)
    private boolean firstInstallmentInterestFree;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    protected DeferredPurchase() {
    }

    public UUID getCardAccountId() { return cardAccountId; }

    public UUID getPurchaseTransactionId() { return purchaseTransactionId; }

    public BigDecimal getAmount() { return amount; }

    public short getInstallmentsCount() { return installmentsCount; }

    public BigDecimal getRate() { return rate; }

    public String getRateType() { return rateType; }

    public boolean isFirstInstallmentInterestFree() { return firstInstallmentInterestFree; }

    public LocalDate getFirstDueDate() { return firstDueDate; }
}
