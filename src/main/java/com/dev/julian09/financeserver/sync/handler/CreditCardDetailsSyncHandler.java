package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.CreditCardDetailsData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** REQ-TC-01 to 03. Right after accounts: the id is the card account's id. */
@Component
public class CreditCardDetailsSyncHandler extends SyncHandler<CreditCardDetailsData> {

    private static final String UPSERT = """
            INSERT INTO credit_card_details AS t (id, user_id, credit_limit, cut_off_day, payment_day, rate, rate_type,
                first_installment_interest_free, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :creditLimit, :cutOffDay, :paymentDay, :rate, :rateType, :firstInstallmentInterestFree,
                :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET credit_limit = EXCLUDED.credit_limit, cut_off_day = EXCLUDED.cut_off_day,
                payment_day = EXCLUDED.payment_day, rate = EXCLUDED.rate, rate_type = EXCLUDED.rate_type,
                first_installment_interest_free = EXCLUDED.first_installment_interest_free,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public CreditCardDetailsSyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, CreditCardDetailsData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "creditCardDetails";
    }

    @Override
    protected String table() {
        return "credit_card_details";
    }

    @Override
    public int order(CreditCardDetailsData data) {
        return 2;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, CreditCardDetailsData d) {
        var type = ledger.accountType(userId, change.id());
        if (type.isEmpty()) {
            return Optional.of(Rejection.reference("La cuenta no existe"));
        }
        if (!type.get().equals("CREDIT_CARD")) {
            return Optional.of(Rejection.invalid("Las condiciones de crédito son de una tarjeta"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, CreditCardDetailsData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("creditLimit", d.creditLimit())
                .addValue("cutOffDay", (short) d.cutOffDay())
                .addValue("paymentDay", (short) d.paymentDay())
                .addValue("rate", d.rate())
                .addValue("rateType", d.rateType())
                .addValue("firstInstallmentInterestFree", d.firstInstallmentInterestFree()));
    }
}
