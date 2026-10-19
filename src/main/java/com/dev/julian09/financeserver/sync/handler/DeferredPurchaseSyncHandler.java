package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.DeferredPurchaseData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** REQ-TC-05 to 07. After transactions: it points to its purchase. */
@Component
public class DeferredPurchaseSyncHandler extends SyncHandler<DeferredPurchaseData> {

    private static final String UPSERT = """
            INSERT INTO deferred_purchases AS t (id, user_id, card_account_id, purchase_transaction_id, amount,
                installments_count, rate, rate_type, first_installment_interest_free, first_due_date,
                created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :cardAccountId, :purchaseTransactionId, :amount, :installmentsCount, :rate, :rateType,
                :firstInstallmentInterestFree, :firstDueDate, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET card_account_id = EXCLUDED.card_account_id,
                purchase_transaction_id = EXCLUDED.purchase_transaction_id, amount = EXCLUDED.amount,
                installments_count = EXCLUDED.installments_count, rate = EXCLUDED.rate, rate_type = EXCLUDED.rate_type,
                first_installment_interest_free = EXCLUDED.first_installment_interest_free,
                first_due_date = EXCLUDED.first_due_date, updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public DeferredPurchaseSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, DeferredPurchaseData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "deferredPurchase";
    }

    @Override
    protected String table() {
        return "deferred_purchases";
    }

    @Override
    public int order(DeferredPurchaseData data) {
        return 6;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, DeferredPurchaseData d) {
        if (!ledger.accountType(userId, d.cardAccountId()).map("CREDIT_CARD"::equals).orElse(false)) {
            return Optional.of(Rejection.reference("La tarjeta no existe"));
        }
        var purchase = ledger.purchase(userId, d.purchaseTransactionId());
        if (purchase.isEmpty()) {
            return Optional.of(Rejection.reference("La compra no existe"));
        }
        if (!purchase.get().accountId().equals(d.cardAccountId()) || purchase.get().amount().compareTo(d.amount()) != 0) {
            return Optional.of(Rejection.invalid("La compra diferida debe ser un gasto de esa tarjeta por el mismo monto"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, DeferredPurchaseData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("cardAccountId", d.cardAccountId())
                .addValue("purchaseTransactionId", d.purchaseTransactionId())
                .addValue("amount", d.amount())
                .addValue("installmentsCount", (short) d.installmentsCount())
                .addValue("rate", d.rate())
                .addValue("rateType", d.rateType())
                .addValue("firstInstallmentInterestFree", d.firstInstallmentInterestFree())
                .addValue("firstDueDate", d.firstDueDate()));
    }
}
