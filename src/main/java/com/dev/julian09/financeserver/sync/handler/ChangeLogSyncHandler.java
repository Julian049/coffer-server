package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData.ChangeLogData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** REQ-HIS-01. No foreign key to the transaction: the history outlives purges and arrives in any order. */
@Component
public class ChangeLogSyncHandler extends SyncHandler<ChangeLogData> {

    private static final String UPSERT = """
            INSERT INTO change_log AS t (id, user_id, transaction_id, action, before_value, after_value, changed_at,
                created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :transactionId, :action, CAST(:beforeValue AS jsonb), CAST(:afterValue AS jsonb),
                :changedAt, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET transaction_id = EXCLUDED.transaction_id, action = EXCLUDED.action,
                before_value = EXCLUDED.before_value, after_value = EXCLUDED.after_value, changed_at = EXCLUDED.changed_at,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    public ChangeLogSyncHandler(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, ChangeLogData.class);
    }

    @Override
    public String entity() {
        return "changeLog";
    }

    @Override
    protected String table() {
        return "change_log";
    }

    @Override
    public int order(ChangeLogData data) {
        return 6;
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, ChangeLogData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("transactionId", d.transactionId())
                .addValue("action", d.action())
                .addValue("beforeValue", jsonOrNull(d.beforeValue()))
                .addValue("afterValue", jsonOrNull(d.afterValue()))
                .addValue("changedAt", utc(d.changedAt())));
    }

    // A JSON null arrives as NullNode; it must be stored as SQL NULL, not as the jsonb value null
    private static String jsonOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}
