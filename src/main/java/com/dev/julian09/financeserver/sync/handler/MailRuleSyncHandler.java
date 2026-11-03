package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.MailRuleData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 2.3-02 (REQ-COR-11): the user's provider → account rules, kept so a new phone gets them back. */
@Component
public class MailRuleSyncHandler extends SyncHandler<MailRuleData> {

    private static final String UPSERT = """
            INSERT INTO mail_rules AS t (id, user_id, source, last_digits, account_id, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :source, :lastDigits, :accountId, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET source = EXCLUDED.source, last_digits = EXCLUDED.last_digits,
                account_id = EXCLUDED.account_id, updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public MailRuleSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, MailRuleData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "mailRule";
    }

    @Override
    protected String table() {
        return "mail_rules";
    }

    @Override
    public int order(MailRuleData data) {
        // After the account it points to
        return 4;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, MailRuleData d) {
        return ledger.accountExists(userId, d.accountId()) ? Optional.empty() : Optional.of(Rejection.reference("La cuenta no existe"));
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, MailRuleData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("source", d.source())
                .addValue("lastDigits", d.lastDigits())
                .addValue("accountId", d.accountId()));
    }
}
