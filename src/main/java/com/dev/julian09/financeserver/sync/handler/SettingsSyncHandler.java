package com.dev.julian09.financeserver.sync.handler;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.SettingsData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SettingsSyncHandler extends SyncHandler<SettingsData> {

    private static final String UPSERT = """
            INSERT INTO user_settings AS t (id, user_id, reminder_lead_days, notifications_enabled, home_balance_view,
                amount_decimals, home_cards, net_balance_mode, loan_payment_category_id, notify_drafts, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :reminderLeadDays, :notificationsEnabled, :homeBalanceView, :amountDecimals,
                CAST(:homeCards AS jsonb), COALESCE(:netBalanceMode, 'PERIOD'), :loanPaymentCategoryId, COALESCE(:notifyDrafts, TRUE), :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET reminder_lead_days = EXCLUDED.reminder_lead_days,
                notifications_enabled = EXCLUDED.notifications_enabled, home_balance_view = EXCLUDED.home_balance_view,
                amount_decimals = EXCLUDED.amount_decimals, home_cards = EXCLUDED.home_cards,
                -- Apps before 1.4.0 do not send it: keep the stored mode
                net_balance_mode = COALESCE(:netBalanceMode, t.net_balance_mode),
                -- Apps before 1.6.0 do not send it: keep the stored category
                loan_payment_category_id = COALESCE(:loanPaymentCategoryId, t.loan_payment_category_id),
                -- Apps before 2.0 do not send it: keep the stored choice
                notify_drafts = COALESCE(:notifyDrafts, t.notify_drafts),
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public SettingsSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, SettingsData.class);
        this.ledger = ledger;
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
        if (!change.id().equals(userId)) {
            return Optional.of(Rejection.invalid("El id de los ajustes debe ser el del usuario"));
        }
        UUID category = data.loanPaymentCategoryId();
        return category == null || ledger.category(userId, category).filter(c -> c.kind().equals("EXPENSE")).isPresent()
                ? Optional.empty() : Optional.of(Rejection.invalid("La categoría de las cuotas debe ser una categoría de gasto propia"));
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, SettingsData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("reminderLeadDays", (short) d.reminderLeadDays())
                .addValue("notificationsEnabled", d.notificationsEnabled())
                .addValue("homeBalanceView", d.homeBalanceView())
                .addValue("amountDecimals", (short) d.amountDecimals())
                .addValue("homeCards", d.homeCards().toString())
                // Apps 1.4.x and 1.5.x still send TOTAL_DEBT: with every debt marked, CUSTOM is the same result
                .addValue("netBalanceMode", "TOTAL_DEBT".equals(d.netBalanceMode()) ? "CUSTOM" : d.netBalanceMode(), Types.VARCHAR)
                .addValue("loanPaymentCategoryId", d.loanPaymentCategoryId(), Types.OTHER)
                .addValue("notifyDrafts", d.notifyDrafts(), Types.BOOLEAN));
    }
}
