package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData.AccountData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountSyncHandler extends SyncHandler<AccountData> {

    private static final String UPSERT = """
            INSERT INTO accounts AS t (id, user_id, name, type, initial_balance, include_in_total, active, deactivated_at,
                institution, icon, color, sort_order, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :name, :type, :initialBalance, :includeInTotal, :active, :deactivatedAt, :institution,
                :icon, :color, :sortOrder, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type,
                initial_balance = EXCLUDED.initial_balance, include_in_total = EXCLUDED.include_in_total,
                active = EXCLUDED.active, deactivated_at = EXCLUDED.deactivated_at, institution = EXCLUDED.institution,
                icon = EXCLUDED.icon, color = EXCLUDED.color, sort_order = EXCLUDED.sort_order,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    public AccountSyncHandler(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, AccountData.class);
    }

    @Override
    public String entity() {
        return "account";
    }

    @Override
    protected String table() {
        return "accounts";
    }

    @Override
    public int order(AccountData data) {
        return 1;
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, AccountData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("name", d.name())
                .addValue("type", d.type())
                .addValue("initialBalance", d.initialBalance())
                .addValue("includeInTotal", d.includeInTotal())
                .addValue("active", d.active())
                .addValue("deactivatedAt", utc(d.deactivatedAt()))
                .addValue("institution", d.institution())
                .addValue("icon", d.icon())
                .addValue("color", d.color())
                .addValue("sortOrder", d.sortOrder()));
    }
}
