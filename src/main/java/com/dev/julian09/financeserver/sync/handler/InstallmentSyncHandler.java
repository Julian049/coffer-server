package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.InstallmentData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** REQ-TC-07, 12 · REQ-PRE-03. Last of the plan: after its deferred purchase. */
@Component
public class InstallmentSyncHandler extends SyncHandler<InstallmentData> {

    private static final String UPSERT = """
            INSERT INTO installments AS t (id, user_id, deferred_purchase_id, loan_account_id, number, due_date, principal,
                interest, insurance, other_costs, status, paid_at, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :deferredPurchaseId, :loanAccountId, :number, :dueDate, :principal, :interest, :insurance,
                :otherCosts, :status, :paidAt, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET deferred_purchase_id = EXCLUDED.deferred_purchase_id,
                loan_account_id = EXCLUDED.loan_account_id, number = EXCLUDED.number, due_date = EXCLUDED.due_date,
                principal = EXCLUDED.principal, interest = EXCLUDED.interest, insurance = EXCLUDED.insurance,
                other_costs = EXCLUDED.other_costs, status = EXCLUDED.status, paid_at = EXCLUDED.paid_at,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public InstallmentSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, InstallmentData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "installment";
    }

    @Override
    protected String table() {
        return "installments";
    }

    @Override
    public int order(InstallmentData data) {
        return 7;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, InstallmentData d) {
        if ((d.deferredPurchaseId() == null) == (d.loanAccountId() == null)) {
            return Optional.of(Rejection.invalid("Una cuota es de una compra diferida o de un préstamo, no de ambos"));
        }
        if (d.loanAccountId() != null) {
            return ledger.accountType(userId, d.loanAccountId()).map("LOAN"::equals).orElse(false)
                    ? Optional.empty() : Optional.of(Rejection.reference("El préstamo no existe"));
        }
        var amount = ledger.deferredPurchaseAmount(userId, d.deferredPurchaseId());
        if (amount.isEmpty()) {
            return Optional.of(Rejection.reference("La compra diferida no existe"));
        }
        // The plan comes one row at a time, so the rule is that its capital never goes over the purchase
        boolean live = change.deletedAt() == null && !d.status().equals("CANCELLED");
        if (live && ledger.otherInstallmentsPrincipal(d.deferredPurchaseId(), change.id()).add(d.principal()).compareTo(amount.get()) > 0) {
            return Optional.of(Rejection.invalid("El capital de las cuotas supera el monto de la compra"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, InstallmentData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("deferredPurchaseId", d.deferredPurchaseId())
                .addValue("loanAccountId", d.loanAccountId())
                .addValue("number", (short) d.number())
                .addValue("dueDate", d.dueDate())
                .addValue("principal", d.principal())
                .addValue("interest", d.interest())
                .addValue("insurance", d.insurance())
                .addValue("otherCosts", d.otherCosts())
                .addValue("status", d.status())
                .addValue("paidAt", utc(d.paidAt())));
    }
}
