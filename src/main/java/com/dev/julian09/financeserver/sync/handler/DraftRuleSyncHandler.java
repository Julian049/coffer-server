package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.DraftRuleData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 2.5 (coffer-server#141): the user's merchant → title and category rules, kept so a new phone gets them back. */
@Component
public class DraftRuleSyncHandler extends SyncHandler<DraftRuleData> {

    private static final String UPSERT = """
            INSERT INTO draft_rules AS t (id, user_id, merchant, merchant_key, amount, account_id, note, category_id, level,
                origin, enabled, learned_since, position, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :merchant, :merchantKey, :amount, :accountId, :note, :categoryId, :level,
                :origin, :enabled, :learnedSince, :position, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET merchant = EXCLUDED.merchant, merchant_key = EXCLUDED.merchant_key,
                amount = EXCLUDED.amount, account_id = EXCLUDED.account_id, note = EXCLUDED.note,
                category_id = EXCLUDED.category_id, level = EXCLUDED.level, origin = EXCLUDED.origin,
                enabled = EXCLUDED.enabled, learned_since = EXCLUDED.learned_since, position = EXCLUDED.position,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public DraftRuleSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, DraftRuleData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "draftRule";
    }

    @Override
    protected String table() {
        return "draft_rules";
    }

    @Override
    public int order(DraftRuleData data) {
        // After the account and the category (root or sub) it points to
        return 4;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, DraftRuleData d) {
        if (d.accountId() != null && !ledger.accountExists(userId, d.accountId())) {
            return Optional.of(Rejection.reference("La cuenta no existe"));
        }
        return ledger.category(userId, d.categoryId()).isPresent()
                ? Optional.empty()
                : Optional.of(Rejection.reference("La categoría no existe"));
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, DraftRuleData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("merchant", d.merchant())
                .addValue("merchantKey", d.merchantKey())
                .addValue("amount", d.amount())
                .addValue("accountId", d.accountId())
                .addValue("note", d.note())
                .addValue("categoryId", d.categoryId())
                .addValue("level", d.level())
                .addValue("origin", d.origin())
                .addValue("enabled", d.enabled())
                .addValue("learnedSince", utc(d.learnedSince()))
                .addValue("position", d.position()));
    }
}
