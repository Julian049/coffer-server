package com.dev.julian09.financeserver.sync.handler;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.TransactionData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Transactions go last: they reference accounts, categories and tags of the same batch. */
@Component
public class TransactionSyncHandler extends SyncHandler<TransactionData> {

    private static final String UPSERT = """
            INSERT INTO transactions AS t (id, user_id, type, amount, occurred_at, account_id, destination_account_id,
                category_id, tag_id, note, installments, principal, interest, fees, installment_id, recurring_id,
                payment_kind, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :type, :amount, :occurredAt, :accountId, :destinationAccountId, :categoryId, :tagId,
                :note, :installments, :principal, :interest, :fees, :installmentId, :recurringId, :paymentKind,
                :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET type = EXCLUDED.type, amount = EXCLUDED.amount,
                occurred_at = EXCLUDED.occurred_at, account_id = EXCLUDED.account_id,
                destination_account_id = EXCLUDED.destination_account_id, category_id = EXCLUDED.category_id,
                tag_id = EXCLUDED.tag_id, note = EXCLUDED.note, installments = EXCLUDED.installments,
                principal = EXCLUDED.principal, interest = EXCLUDED.interest, fees = EXCLUDED.fees,
                installment_id = EXCLUDED.installment_id, recurring_id = EXCLUDED.recurring_id,
                payment_kind = EXCLUDED.payment_kind, updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public TransactionSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, TransactionData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "transaction";
    }

    @Override
    protected String table() {
        return "transactions";
    }

    @Override
    public int order(TransactionData data) {
        return 5;
    }

    /** TransactionData rules of the contract, then the references (FIN-REFERENCE). */
    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, TransactionData d) {
        boolean transfer = d.type().equals("TRANSFER");
        if (transfer && (d.destinationAccountId() == null || d.destinationAccountId().equals(d.accountId()))) {
            return Optional.of(Rejection.invalid("Una transferencia necesita una cuenta de destino distinta de la de origen"));
        }
        if (transfer == (d.categoryId() != null) || (!transfer && d.destinationAccountId() != null)) {
            return Optional.of(Rejection.invalid("Gastos e ingresos llevan categoría; las transferencias, cuenta de destino"));
        }
        boolean breakdown = Stream.of(d.principal(), d.interest(), d.fees()).anyMatch(v -> v != null);
        if (breakdown && Stream.of(d.principal(), d.interest(), d.fees()).map(v -> v == null ? BigDecimal.ZERO : v)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(d.amount()) != 0) {
            return Optional.of(Rejection.invalid("Capital, intereses y cargos deben sumar el monto"));
        }
        if (!ledger.accountExists(userId, d.accountId()) || (transfer && !ledger.accountExists(userId, d.destinationAccountId()))) {
            return Optional.of(Rejection.reference("La cuenta no existe"));
        }
        if (d.tagId() != null && !ledger.tagExists(userId, d.tagId())) {
            return Optional.of(Rejection.reference("La etiqueta no existe"));
        }
        if (d.recurringId() != null && !ledger.recurringExists(userId, d.recurringId())) {
            return Optional.of(Rejection.reference("El gasto recurrente no existe"));
        }
        if (!transfer) {
            var category = ledger.category(userId, d.categoryId());
            if (category.isEmpty()) {
                return Optional.of(Rejection.reference("La categoría no existe"));
            }
            if (!category.get().kind().equals(d.type())) {
                return Optional.of(Rejection.invalid("La categoría no corresponde al tipo de movimiento"));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, TransactionData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("type", d.type())
                .addValue("amount", d.amount())
                .addValue("occurredAt", utc(d.occurredAt()))
                .addValue("accountId", d.accountId())
                .addValue("destinationAccountId", d.destinationAccountId())
                .addValue("categoryId", d.categoryId())
                .addValue("tagId", d.tagId())
                .addValue("note", d.note())
                .addValue("installments", (short) d.installments())
                .addValue("principal", d.principal())
                .addValue("interest", d.interest())
                .addValue("fees", d.fees())
                .addValue("installmentId", d.installmentId())
                .addValue("recurringId", d.recurringId())
                .addValue("paymentKind", d.paymentKind()));
    }
}
