package com.dev.julian09.financeserver.ledger.validation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Integrity the database only checks at commit (deferred FKs) or not at all. Looking references up first lets one bad
 * change be rejected alone instead of failing the whole batch at commit. Rows written earlier in the same batch count.
 */
@Component
public class LedgerValidators {

    /** A category reference: its kind and whether it is itself a subcategory. */
    public record CategoryRef(String kind, UUID parentId) {
    }

    private final JdbcTemplate jdbc;

    public LedgerValidators(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean accountExists(UUID userId, UUID accountId) {
        return exists("SELECT 1 FROM accounts WHERE id = ? AND user_id = ?", accountId, userId);
    }

    /** The stored type of an account, empty if it is new: the type never changes after creation (S4-SRV01). */
    public Optional<String> accountType(UUID userId, UUID accountId) {
        return jdbc.queryForList("SELECT type FROM accounts WHERE id = ? AND user_id = ?", String.class, accountId, userId)
                .stream().findFirst();
    }

    public boolean hasSubcategories(UUID userId, UUID categoryId) {
        return exists("SELECT 1 FROM categories WHERE parent_id = ? AND user_id = ? AND deleted_at IS NULL", categoryId, userId);
    }

    public boolean tagExists(UUID userId, UUID tagId) {
        return exists("SELECT 1 FROM tags WHERE id = ? AND user_id = ?", tagId, userId);
    }

    public Optional<CategoryRef> category(UUID userId, UUID categoryId) {
        List<CategoryRef> rows = jdbc.query("SELECT kind, parent_id FROM categories WHERE id = ? AND user_id = ?",
                (rs, i) -> new CategoryRef(rs.getString("kind"), rs.getObject("parent_id", UUID.class)), categoryId, userId);
        return rows.stream().findFirst();
    }

    private boolean exists(String sql, Object... args) {
        return !jdbc.queryForList(sql, Integer.class, args).isEmpty();
    }
}
