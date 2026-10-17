package com.dev.julian09.financeserver.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag extends SyncableEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean pinned;

    protected Tag() {
    }

    public String getName() { return name; }

    public boolean isPinned() { return pinned; }
}
