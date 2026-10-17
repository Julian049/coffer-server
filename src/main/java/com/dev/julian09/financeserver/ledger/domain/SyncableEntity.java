package com.dev.julian09.financeserver.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Columns every synced table shares (data-model §1.2). The client generates the id; updated_at decides
 * last-write-wins; version is set by the database trigger (V4) and is the pull cursor, so JPA never writes it.
 */
@MappedSuperclass
public abstract class SyncableEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private long version;

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }

    public long getVersion() { return version; }
}
