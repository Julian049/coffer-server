package com.dev.julian09.financeserver.sync.handler;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData.SettingsData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SettingsSyncHandler extends SyncHandler<SettingsData> {

    private static final String UPSERT = """
            INSERT INTO user_settings AS t (id, user_id, reminder_lead_days, notifications_enabled, home_balance_view,
                amount_decimals, home_cards, net_balance_mode, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :reminderLeadDays, :notificationsEnabled, :homeBalanceView, :amountDecimals,
                CAST(:homeCards AS jsonb), COALESCE(:netBalanceMode, 'PERIOD'), :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET reminder_lead_days = EXCLUDED.reminder_lead_days,
                notifications_enabled = EXCLUDED.notifications_enabled, home_balance_view = EXCLUDED.home_balance_view,
                amount_decimals = EXCLUDED.amount_decimals, home_cards = EXCLUDED.home_cards,
                -- Apps before 1.4.0 do not send it: keep the stored mode
                net_balance_mode = COALESCE(:netBalanceMode, t.net_balance_mode),
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    public SettingsSyncHandler(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, SettingsData.class);
    }

    @Override
    public String entity() {
        return "settings";
    }

    @Override
    protected String table() {
        return "user_settings";
    }

    @Override
    public int order(SettingsData data) {
        return 0;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, SettingsData data) {
        // One settings row per user, identified by the user id (ck_settings_id)
        return change.id().equals(userId) ? Optional.empty() : Optional.of(Rejection.invalid("El id de los ajustes debe ser el del usuario"));
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, SettingsData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("reminderLeadDays", (short) d.reminderLeadDays())
                .addValue("notificationsEnabled", d.notificationsEnabled())
                .addValue("homeBalanceView", d.homeBalanceView())
                .addValue("amountDecimals", (short) d.amountDecimals())
                .addValue("homeCards", d.homeCards().toString())
                .addValue("netBalanceMode", d.netBalanceMode(), Types.VARCHAR));
    }
}
