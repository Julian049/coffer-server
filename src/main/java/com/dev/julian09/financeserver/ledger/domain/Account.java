package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accounts")
public class Account extends SyncableEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 12)
    private String type;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialBalance;

    @Column(name = "include_in_total", nullable = false)
    private boolean includeInTotal;

    @Column(name = "include_in_net", nullable = false)
    private boolean includeInNet;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(length = 60)
    private String institution;

    @Column(length = 30)
    private String icon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Account() {
    }

    public String getName() { return name; }

    public String getType() { return type; }

    public BigDecimal getInitialBalance() { return initialBalance; }

    public boolean isIncludeInTotal() { return includeInTotal; }

    public boolean isIncludeInNet() { return includeInNet; }

    public boolean isActive() { return active; }

    public Instant getDeactivatedAt() { return deactivatedAt; }

    public String getInstitution() { return institution; }

    public String getIcon() { return icon; }

    public String getColor() { return color; }

    public int getSortOrder() { return sortOrder; }
}
