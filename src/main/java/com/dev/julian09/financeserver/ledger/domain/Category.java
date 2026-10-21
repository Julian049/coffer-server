package com.dev.julian09.financeserver.ledger.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category extends SyncableEntity {

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String kind;

    @Column(name = "is_base", nullable = false)
    private boolean base;

    @Column(name = "system_key", length = 40)
    private String systemKey;

    @Column(length = 30)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "exclude_from_reports", nullable = false)
    private boolean excludeFromReports;

    protected Category() {
    }

    public UUID getParentId() { return parentId; }

    public String getName() { return name; }

    public String getKind() { return kind; }

    public boolean isBase() { return base; }

    public String getSystemKey() { return systemKey; }

    public String getIcon() { return icon; }

    public int getSortOrder() { return sortOrder; }

    public boolean isExcludeFromReports() { return excludeFromReports; }
}
