package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData.TransactionDraftData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drafts are born on the server; the phone only sends them back deleted, once applied or discarded (REQ-COR-05).
 * Its data is ignored on an update, so a phone cannot rewrite what the email said.
 */
@Component
public class TransactionDraftSyncHandler extends SyncHandler<TransactionDraftData> {

    private static final String UPSERT = """
            INSERT INTO transaction_drafts AS t (id, user_id, source, type, amount, occurred_at, merchant, last_digits,
                created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :source, :type, :amount, :occurredAt, :merchant, :lastDigits, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    public TransactionDraftSyncHandler(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, TransactionDraftData.class);
    }

    @Override
    public String entity() {
        return "transactionDraft";
    }

    @Override
    protected String table() {
        return "transaction_drafts";
    }

    @Override
    public int order(TransactionDraftData data) {
        // After the transaction it became
        return 6;
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, TransactionDraftData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("source", d.source())
                .addValue("type", d.type())
                .addValue("amount", d.amount())
                .addValue("occurredAt", utc(d.occurredAt()))
                .addValue("merchant", d.merchant())
                .addValue("lastDigits", d.lastDigits()));
    }
}
