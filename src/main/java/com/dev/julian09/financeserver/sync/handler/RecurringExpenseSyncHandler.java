package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.RecurringExpenseData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** REQ-REC-01, 02, 11. Before transactions, which may come from a recurring expense of the same batch. */
@Component
public class RecurringExpenseSyncHandler extends SyncHandler<RecurringExpenseData> {

    private static final String UPSERT = """
            INSERT INTO recurring_expenses AS t (id, user_id, name, estimated_amount, last_paid_amount, account_id,
                category_id, start_date, frequency, interval_count, interval_unit, next_due_date, last_notified_on, active,
                created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :name, :estimatedAmount, :lastPaidAmount, :accountId, :categoryId, :startDate, :frequency,
                :intervalCount, :intervalUnit, :nextDueDate, :lastNotifiedOn, :active, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, estimated_amount = EXCLUDED.estimated_amount,
                last_paid_amount = EXCLUDED.last_paid_amount, account_id = EXCLUDED.account_id,
                category_id = EXCLUDED.category_id, start_date = EXCLUDED.start_date, frequency = EXCLUDED.frequency,
                interval_count = EXCLUDED.interval_count, interval_unit = EXCLUDED.interval_unit,
                next_due_date = EXCLUDED.next_due_date, last_notified_on = EXCLUDED.last_notified_on,
                active = EXCLUDED.active, updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public RecurringExpenseSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, RecurringExpenseData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "recurringExpense";
    }

    @Override
    protected String table() {
        return "recurring_expenses";
    }

    @Override
    public int order(RecurringExpenseData data) {
        return 4;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, RecurringExpenseData d) {
        boolean custom = d.frequency().equals("CUSTOM");
        if (custom != (d.intervalCount() != null && d.intervalUnit() != null)
                || (!custom && (d.intervalCount() != null || d.intervalUnit() != null))) {
            return Optional.of(Rejection.invalid("Solo la frecuencia personalizada lleva cada cuántos días, semanas o meses"));
        }
        if (!ledger.accountExists(userId, d.accountId())) {
            return Optional.of(Rejection.reference("La cuenta no existe"));
        }
        var category = ledger.category(userId, d.categoryId());
        if (category.isEmpty()) {
            return Optional.of(Rejection.reference("La categoría no existe"));
        }
        if (!category.get().kind().equals("EXPENSE")) {
            return Optional.of(Rejection.invalid("Los recurrentes solo aplican a gastos"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, RecurringExpenseData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("name", d.name())
                .addValue("estimatedAmount", d.estimatedAmount())
                .addValue("lastPaidAmount", d.lastPaidAmount())
                .addValue("accountId", d.accountId())
                .addValue("categoryId", d.categoryId())
                .addValue("startDate", d.startDate())
                .addValue("frequency", d.frequency())
                .addValue("intervalCount", d.intervalCount() == null ? null : d.intervalCount().shortValue())
                .addValue("intervalUnit", d.intervalUnit())
                .addValue("nextDueDate", d.nextDueDate())
                .addValue("lastNotifiedOn", d.lastNotifiedOn())
                .addValue("active", d.active()));
    }
}
