package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One installment of a deferred purchase or of a loan (exactly one owner). */
@Entity
@Table(name = "installments")
public class Installment extends SyncableEntity {

    @Column(name = "deferred_purchase_id")
    private UUID deferredPurchaseId;

    @Column(name = "loan_account_id")
    private UUID loanAccountId;

    @Column(nullable = false)
    private short number;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal interest;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal insurance;

    @Column(name = "other_costs", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherCosts;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Installment() {
    }

    public UUID getDeferredPurchaseId() { return deferredPurchaseId; }

    public UUID getLoanAccountId() { return loanAccountId; }

    public short getNumber() { return number; }

    public LocalDate getDueDate() { return dueDate; }

    public BigDecimal getPrincipal() { return principal; }

    public BigDecimal getInterest() { return interest; }

    public BigDecimal getInsurance() { return insurance; }

    public BigDecimal getOtherCosts() { return otherCosts; }

    public String getStatus() { return status; }

    public Instant getPaidAt() { return paidAt; }
}
