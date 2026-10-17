package com.dev.julian09.financeserver.sync.handler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Applies the changes of one entity (detailed-design §6.9.7): an upsert that only overwrites a row of the same user
 * with an older updated_at, returning the new version; no row back means STALE or another user's id.
 */
public abstract class SyncHandler<D extends EntityData> {

    /** Why a change cannot be applied; the batch goes on. */
    public record Rejection(String code, String detail) {

        public static Rejection invalid(String detail) {
            return new Rejection("FIN-VALIDATION", detail);
        }

        public static Rejection reference(String detail) {
            return new Rejection("FIN-REFERENCE", detail);
        }
    }

    protected final NamedParameterJdbcTemplate jdbc;
    private final Class<D> dataType;

    protected SyncHandler(NamedParameterJdbcTemplate jdbc, Class<D> dataType) {
        this.jdbc = jdbc;
        this.dataType = dataType;
    }

    /** Value of {@code entity} in the contract. */
    public abstract String entity();

    protected abstract String table();

    /** Order inside a batch: what others reference goes first, so lookups see it (lower runs first). */
    public abstract int order(D data);

    /** Rules across fields or rows; empty when the change can be written. */
    public Optional<Rejection> check(UUID userId, IncomingChange change, D data) {
        return Optional.empty();
    }

    /** Insert or last-write-wins update; the new version, or empty when the stored row wins or is not ours. */
    public abstract Optional<Long> upsert(UUID userId, IncomingChange change, D data);

    public Class<D> dataType() {
        return dataType;
    }

    /** Owner of an existing row, to tell STALE from another user's id. */
    public Optional<UUID> owner(UUID id) {
        return jdbc.queryForList("SELECT user_id FROM " + table() + " WHERE id = :id", new MapSqlParameterSource("id", id), UUID.class)
                .stream().findFirst();
    }

    /** Envelope parameters shared by every table. */
    protected static MapSqlParameterSource base(UUID userId, IncomingChange change) {
        return new MapSqlParameterSource()
                .addValue("id", change.id())
                .addValue("userId", userId)
                .addValue("createdAt", utc(change.createdAt()))
                .addValue("updatedAt", utc(change.updatedAt()))
                .addValue("deletedAt", utc(change.deletedAt()));
    }

    protected static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    protected Optional<Long> returning(String sql, MapSqlParameterSource params) {
        List<Long> versions = jdbc.queryForList(sql, params, Long.class);
        return versions.stream().findFirst();
    }
}
