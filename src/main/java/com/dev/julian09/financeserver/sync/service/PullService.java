package com.dev.julian09.financeserver.sync.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.dev.julian09.financeserver.ledger.domain.Account;
import com.dev.julian09.financeserver.ledger.domain.Category;
import com.dev.julian09.financeserver.ledger.domain.ChangeLog;
import com.dev.julian09.financeserver.ledger.domain.CreditCardDetails;
import com.dev.julian09.financeserver.ledger.domain.DeferredPurchase;
import com.dev.julian09.financeserver.ledger.domain.Installment;
import com.dev.julian09.financeserver.ledger.domain.LoanDetails;
import com.dev.julian09.financeserver.ledger.domain.RecurringExpense;
import com.dev.julian09.financeserver.ledger.domain.SyncableEntity;
import com.dev.julian09.financeserver.ledger.domain.Tag;
import com.dev.julian09.financeserver.ledger.domain.Transaction;
import com.dev.julian09.financeserver.ledger.domain.UserSettings;
import com.dev.julian09.financeserver.ledger.repo.AccountRepository;
import com.dev.julian09.financeserver.ledger.repo.CategoryRepository;
import com.dev.julian09.financeserver.ledger.repo.ChangeLogRepository;
import com.dev.julian09.financeserver.ledger.repo.CreditCardDetailsRepository;
import com.dev.julian09.financeserver.ledger.repo.DeferredPurchaseRepository;
import com.dev.julian09.financeserver.ledger.repo.InstallmentRepository;
import com.dev.julian09.financeserver.ledger.repo.LoanDetailsRepository;
import com.dev.julian09.financeserver.ledger.repo.RecurringExpenseRepository;
import com.dev.julian09.financeserver.ledger.repo.TagRepository;
import com.dev.julian09.financeserver.ledger.repo.TransactionRepository;
import com.dev.julian09.financeserver.ledger.repo.UserSettingsRepository;
import com.dev.julian09.financeserver.sync.dto.EntityData;
import com.dev.julian09.financeserver.sync.dto.PullResponse;
import com.dev.julian09.financeserver.sync.dto.PulledChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/v1/sync/pull (detailed-design §6.9.8). Step 1: page the (entity, id, version) of every table by the
 * global version cursor; step 2: load those rows with JPA. Deleted rows are included (REQ-SYN-03).
 */
@Service
public class PullService {

    // One SELECT per synced table; add a line here when a sprint adds a table
    private static final String CHANGES = """
            SELECT 'settings' AS entity, id, version FROM user_settings WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'account', id, version FROM accounts WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'creditCardDetails', id, version FROM credit_card_details WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'loanDetails', id, version FROM loan_details WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'category', id, version FROM categories WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'tag', id, version FROM tags WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'transaction', id, version FROM transactions WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'deferredPurchase', id, version FROM deferred_purchases WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'installment', id, version FROM installments WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'recurringExpense', id, version FROM recurring_expenses WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'changeLog', id, version FROM change_log WHERE user_id = :user AND version > :since
            """;

    private record Row(String entity, UUID id, long version) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final UserSettingsRepository settings;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final TransactionRepository transactions;
    private final ChangeLogRepository changeLog;
    private final RecurringExpenseRepository recurring;
    private final CreditCardDetailsRepository cards;
    private final DeferredPurchaseRepository deferred;
    private final InstallmentRepository installments;
    private final LoanDetailsRepository loans;
    private final JsonMapper json;

    public PullService(NamedParameterJdbcTemplate jdbc, UserSettingsRepository settings, AccountRepository accounts,
                       CategoryRepository categories, TagRepository tags, TransactionRepository transactions,
                       ChangeLogRepository changeLog, RecurringExpenseRepository recurring, CreditCardDetailsRepository cards,
                       DeferredPurchaseRepository deferred, InstallmentRepository installments, LoanDetailsRepository loans,
                       JsonMapper json) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.accounts = accounts;
        this.categories = categories;
        this.tags = tags;
        this.transactions = transactions;
        this.changeLog = changeLog;
        this.recurring = recurring;
        this.cards = cards;
        this.deferred = deferred;
        this.installments = installments;
        this.loans = loans;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PullResponse pull(UUID userId, long since, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("user", userId).addValue("since", since);
        List<Row> rows = jdbc.query(CHANGES + " ORDER BY version LIMIT :limit", params.addValue("limit", limit + 1),
                (rs, i) -> new Row(rs.getString("entity"), rs.getObject("id", UUID.class), rs.getLong("version")));
        boolean hasMore = rows.size() > limit;
        List<Row> page = hasMore ? rows.subList(0, limit) : rows;
        long nextCursor = page.isEmpty() ? since : page.getLast().version();

        Map<String, List<UUID>> idsByEntity = page.stream()
                .collect(Collectors.groupingBy(Row::entity, Collectors.mapping(Row::id, Collectors.toList())));
        // By entity and id: a card's terms share the id of its account
        Map<String, PulledChange> loaded = new HashMap<>();
        load(idsByEntity, "settings", settings, this::settingsData, loaded);
        load(idsByEntity, "account", accounts, PullService::accountData, loaded);
        load(idsByEntity, "category", categories, PullService::categoryData, loaded);
        load(idsByEntity, "tag", tags, t -> new EntityData.TagData(t.getName(), t.isPinned()), loaded);
        load(idsByEntity, "transaction", transactions, PullService::transactionData, loaded);
        load(idsByEntity, "creditCardDetails", cards, PullService::cardData, loaded);
        load(idsByEntity, "loanDetails", loans, PullService::loanData, loaded);
        load(idsByEntity, "deferredPurchase", deferred, PullService::deferredData, loaded);
        load(idsByEntity, "installment", installments, PullService::installmentData, loaded);
        load(idsByEntity, "recurringExpense", recurring, PullService::recurringData, loaded);
        load(idsByEntity, "changeLog", changeLog, this::changeLogData, loaded);

        List<PulledChange> changes = new ArrayList<>(page.size());
        page.forEach(row -> changes.add(loaded.get(row.entity() + row.id())));

        Long remaining = since == 0
                ? jdbc.queryForObject("SELECT count(*) FROM (" + CHANGES.replace(":since", ":after") + ") c",
                        params.addValue("after", nextCursor), Long.class)
                : null;
        return new PullResponse(changes, nextCursor, hasMore, remaining);
    }

    private <E extends SyncableEntity> void load(Map<String, List<UUID>> ids, String entity, JpaRepository<E, UUID> repo,
                                                 Function<E, EntityData> data, Map<String, PulledChange> into) {
        List<UUID> wanted = ids.get(entity);
        if (wanted == null) {
            return;
        }
        for (E row : repo.findAllById(wanted)) {
            into.put(entity + row.getId(), new PulledChange(entity, row.getId(), row.getCreatedAt(), row.getUpdatedAt(),
                    row.getDeletedAt(), data.apply(row), row.getVersion()));
        }
    }

    private EntityData settingsData(UserSettings s) {
        return new EntityData.SettingsData(s.getReminderLeadDays(), s.isNotificationsEnabled(), s.getHomeBalanceView(),
                s.getAmountDecimals(), json.readTree(s.getHomeCards()));
    }

    private EntityData changeLogData(ChangeLog c) {
        return new EntityData.ChangeLogData(c.getTransactionId(), c.getAction(), readOrNull(c.getBeforeValue()),
                readOrNull(c.getAfterValue()), c.getChangedAt());
    }

    private JsonNode readOrNull(String value) {
        return value == null ? null : json.readTree(value);
    }

    private static EntityData accountData(Account a) {
        return new EntityData.AccountData(a.getName(), a.getType(), a.getInitialBalance(), a.isIncludeInTotal(), a.isActive(),
                a.getDeactivatedAt(), a.getInstitution(), a.getIcon(), a.getColor(), a.getSortOrder());
    }

    private static EntityData categoryData(Category c) {
        return new EntityData.CategoryData(c.getParentId(), c.getName(), c.getKind(), c.isBase(), c.getSystemKey(), c.getIcon(),
                c.getSortOrder(), c.isExcludeFromReports());
    }

    private static EntityData cardData(CreditCardDetails c) {
        return new EntityData.CreditCardDetailsData(c.getCreditLimit(), c.getCutOffDay(), c.getPaymentDay(), c.getRate(),
                c.getRateType(), c.isFirstInstallmentInterestFree());
    }

    private static EntityData loanData(LoanDetails l) {
        return new EntityData.LoanDetailsData(l.getMode(), l.getDisbursementAccountId(), l.getPrincipal(), l.getInstallmentsCount(),
                l.getRate(), l.getRateType(), l.getFirstDueDate(), l.getPaymentDay(), l.getMonthlyPayment(),
                l.getLifeInsuranceMonthly(), l.getOtherCostsMonthly(), l.getAmortization());
    }

    private static EntityData deferredData(DeferredPurchase d) {
        return new EntityData.DeferredPurchaseData(d.getCardAccountId(), d.getPurchaseTransactionId(), d.getAmount(),
                d.getInstallmentsCount(), d.getRate(), d.getRateType(), d.isFirstInstallmentInterestFree(), d.getFirstDueDate());
    }

    private static EntityData installmentData(Installment i) {
        return new EntityData.InstallmentData(i.getDeferredPurchaseId(), i.getLoanAccountId(), i.getNumber(), i.getDueDate(),
                i.getPrincipal(), i.getInterest(), i.getInsurance(), i.getOtherCosts(), i.getStatus(), i.getPaidAt());
    }

    private static EntityData recurringData(RecurringExpense r) {
        return new EntityData.RecurringExpenseData(r.getName(), r.getEstimatedAmount(), r.getLastPaidAmount(), r.getAccountId(),
                r.getCategoryId(), r.getStartDate(), r.getFrequency(),
                r.getIntervalCount() == null ? null : r.getIntervalCount().intValue(), r.getIntervalUnit(), r.getNextDueDate(),
                r.getLastNotifiedOn(), r.isActive());
    }

    private static EntityData transactionData(Transaction t) {
        return new EntityData.TransactionData(t.getType(), t.getAmount(), t.getOccurredAt(), t.getAccountId(),
                t.getDestinationAccountId(), t.getCategoryId(), t.getTagId(), t.getNote(), t.getInstallments(), t.getPrincipal(),
                t.getInterest(), t.getFees(), t.getInstallmentId(), t.getRecurringId(), t.getPaymentKind());
    }
}
