package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.LoanDetailsData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** REQ-PRE-01 to 03, 08 (S10-SRV01). Right after accounts: the id is the loan account's id; ck_loan_mode per mode. */
@Component
public class LoanDetailsSyncHandler extends SyncHandler<LoanDetailsData> {

    private static final String UPSERT = """
            INSERT INTO loan_details AS t (id, user_id, mode, disbursement_account_id, principal, installments_count, rate,
                rate_type, first_due_date, payment_day, monthly_payment, life_insurance_monthly, other_costs_monthly,
                amortization, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :mode, :disbursementAccountId, :principal, :installmentsCount, :rate, :rateType,
                :firstDueDate, :paymentDay, :monthlyPayment, :lifeInsuranceMonthly, :otherCostsMonthly, :amortization,
                :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET mode = EXCLUDED.mode, disbursement_account_id = EXCLUDED.disbursement_account_id,
                principal = EXCLUDED.principal, installments_count = EXCLUDED.installments_count, rate = EXCLUDED.rate,
                rate_type = EXCLUDED.rate_type, first_due_date = EXCLUDED.first_due_date, payment_day = EXCLUDED.payment_day,
                monthly_payment = EXCLUDED.monthly_payment, life_insurance_monthly = EXCLUDED.life_insurance_monthly,
                other_costs_monthly = EXCLUDED.other_costs_monthly, amortization = EXCLUDED.amortization,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public LoanDetailsSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, LoanDetailsData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "loanDetails";
    }

    @Override
    protected String table() {
        return "loan_details";
    }

    @Override
    public int order(LoanDetailsData data) {
        return 2;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, LoanDetailsData d) {
        var type = ledger.accountType(userId, change.id());
        if (type.isEmpty()) {
            return Optional.of(Rejection.reference("La cuenta no existe"));
        }
        if (!type.get().equals("LOAN")) {
            return Optional.of(Rejection.invalid("Las condiciones de préstamo son de una cuenta de préstamo"));
        }
        boolean detailed = d.mode().equals("DETAILED");
        if (detailed && (d.principal() == null || d.installmentsCount() == null || d.rate() == null || d.rateType() == null
                || d.firstDueDate() == null)) {
            return Optional.of(Rejection.invalid("Un préstamo detallado necesita monto, cuotas, tasa y fecha de la primera cuota"));
        }
        if (!detailed && d.monthlyPayment() == null) {
            return Optional.of(Rejection.invalid("Un préstamo simple necesita el valor de la cuota"));
        }
        if (d.disbursementAccountId() != null
                && (d.disbursementAccountId().equals(change.id()) || ledger.accountType(userId, d.disbursementAccountId()).isEmpty())) {
            return Optional.of(Rejection.reference("La cuenta de destino no existe"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, LoanDetailsData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("mode", d.mode())
                .addValue("disbursementAccountId", d.disbursementAccountId())
                .addValue("principal", d.principal())
                .addValue("installmentsCount", d.installmentsCount() == null ? null : d.installmentsCount().shortValue())
                .addValue("rate", d.rate())
                .addValue("rateType", d.rateType())
                .addValue("firstDueDate", d.firstDueDate())
                .addValue("paymentDay", (short) d.paymentDay())
                .addValue("monthlyPayment", d.monthlyPayment())
                .addValue("lifeInsuranceMonthly", d.lifeInsuranceMonthly())
                .addValue("otherCostsMonthly", d.otherCostsMonthly())
                .addValue("amortization", d.amortizationOrDefault()));
    }
}
