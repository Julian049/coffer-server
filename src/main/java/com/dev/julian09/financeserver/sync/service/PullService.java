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
import com.dev.julian09.financeserver.ledger.domain.SyncableEntity;
import com.dev.julian09.financeserver.ledger.domain.Tag;
import com.dev.julian09.financeserver.ledger.domain.Transaction;
import com.dev.julian09.financeserver.ledger.domain.UserSettings;
import com.dev.julian09.financeserver.ledger.repo.AccountRepository;
import com.dev.julian09.financeserver.ledger.repo.CategoryRepository;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/v1/sync/pull (detailed-design §6.9.8). Step 1: page the (entity, id, version) of every table by the
 * global version cursor; step 2: load those rows with JPA. Deleted rows are included (REQ-SYN-03).
 */
@Service
public class PullService {

    // One SELECT per synced table; add a line here when a sprint adds a table (V7, V8)
    private static final String CHANGES = """
            SELECT 'settings' AS entity, id, version FROM user_settings WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'account', id, version FROM accounts WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'category', id, version FROM categories WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'tag', id, version FROM tags WHERE user_id = :user AND version > :since
            UNION ALL SELECT 'transaction', id, version FROM transactions WHERE user_id = :user AND version > :since
            """;

    private record Row(String entity, UUID id, long version) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final UserSettingsRepository settings;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final TransactionRepository transactions;
    private final JsonMapper json;

    public PullService(NamedParameterJdbcTemplate jdbc, UserSettingsRepository settings, AccountRepository accounts,
                       CategoryRepository categories, TagRepository tags, TransactionRepository transactions, JsonMapper json) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.accounts = accounts;
        this.categories = categories;
        this.tags = tags;
        this.transactions = transactions;
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
        Map<UUID, PulledChange> loaded = new HashMap<>();
        load(idsByEntity, "settings", settings, this::settingsData, loaded);
        load(idsByEntity, "account", accounts, PullService::accountData, loaded);
        load(idsByEntity, "category", categories, PullService::categoryData, loaded);
        load(idsByEntity, "tag", tags, t -> new EntityData.TagData(t.getName(), t.isPinned()), loaded);
        load(idsByEntity, "transaction", transactions, PullService::transactionData, loaded);

        List<PulledChange> changes = new ArrayList<>(page.size());
        page.forEach(row -> changes.add(loaded.get(row.id())));

        Long remaining = since == 0
                ? jdbc.queryForObject("SELECT count(*) FROM (" + CHANGES.replace(":since", ":after") + ") c",
                        params.addValue("after", nextCursor), Long.class)
                : null;
        return new PullResponse(changes, nextCursor, hasMore, remaining);
    }

    private <E extends SyncableEntity> void load(Map<String, List<UUID>> ids, String entity, JpaRepository<E, UUID> repo,
                                                 Function<E, EntityData> data, Map<UUID, PulledChange> into) {
        List<UUID> wanted = ids.get(entity);
        if (wanted == null) {
            return;
        }
        for (E row : repo.findAllById(wanted)) {
            into.put(row.getId(), new PulledChange(entity, row.getId(), row.getCreatedAt(), row.getUpdatedAt(),
                    row.getDeletedAt(), data.apply(row), row.getVersion()));
        }
    }

    private EntityData settingsData(UserSettings s) {
        return new EntityData.SettingsData(s.getReminderLeadDays(), s.isNotificationsEnabled(), s.getHomeBalanceView(),
                s.getAmountDecimals(), json.readTree(s.getHomeCards()));
    }

    private static EntityData accountData(Account a) {
        return new EntityData.AccountData(a.getName(), a.getType(), a.getInitialBalance(), a.isIncludeInTotal(), a.isActive(),
                a.getDeactivatedAt(), a.getInstitution(), a.getIcon(), a.getColor(), a.getSortOrder());
    }

    private static EntityData categoryData(Category c) {
        return new EntityData.CategoryData(c.getParentId(), c.getName(), c.getKind(), c.isBase(), c.getSystemKey(), c.getIcon(),
                c.getSortOrder());
    }

    private static EntityData transactionData(Transaction t) {
        return new EntityData.TransactionData(t.getType(), t.getAmount(), t.getOccurredAt(), t.getAccountId(),
                t.getDestinationAccountId(), t.getCategoryId(), t.getTagId(), t.getNote(), t.getInstallments(), t.getPrincipal(),
                t.getInterest(), t.getFees(), t.getInstallmentId(), t.getRecurringId(), t.getPaymentKind());
    }
}
