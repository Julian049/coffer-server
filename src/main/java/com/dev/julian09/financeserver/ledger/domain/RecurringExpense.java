package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A scheduled expense (REQ-REC-01): the client computes the next due date; the server only stores it (ADR-0010). */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense extends SyncableEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "estimated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal estimatedAmount;

    @Column(name = "last_paid_amount", precision = 19, scale = 4)
    private BigDecimal lastPaidAmount;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(nullable = false, length = 10)
    private String frequency;

    @Column(name = "interval_count")
    private Short intervalCount;

    @Column(name = "interval_unit", length = 5)
    private String intervalUnit;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "last_notified_on")
    private LocalDate lastNotifiedOn;

    @Column(nullable = false)
    private boolean active;

    protected RecurringExpense() {
    }

    public String getName() { return name; }

    public BigDecimal getEstimatedAmount() { return estimatedAmount; }

    public BigDecimal getLastPaidAmount() { return lastPaidAmount; }

    public UUID getAccountId() { return accountId; }

    public UUID getCategoryId() { return categoryId; }

    public LocalDate getStartDate() { return startDate; }

    public String getFrequency() { return frequency; }

    public Short getIntervalCount() { return intervalCount; }

    public String getIntervalUnit() { return intervalUnit; }

    public LocalDate getNextDueDate() { return nextDueDate; }

    public LocalDate getLastNotifiedOn() { return lastNotifiedOn; }

    public boolean isActive() { return active; }
}
