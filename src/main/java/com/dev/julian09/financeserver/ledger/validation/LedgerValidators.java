package com.dev.julian09.financeserver.ledger.validation;

import java.math.BigDecimal;
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

    /** A card purchase: the expense's account and amount, empty if it is not an expense of this user. */
    public record PurchaseRef(UUID accountId, BigDecimal amount) {
    }

    public Optional<PurchaseRef> purchase(UUID userId, UUID transactionId) {
        return jdbc.query("SELECT account_id, amount FROM transactions WHERE id = ? AND user_id = ? AND type = 'EXPENSE'",
                (rs, i) -> new PurchaseRef(rs.getObject("account_id", UUID.class), rs.getBigDecimal("amount")), transactionId, userId)
                .stream().findFirst();
    }

    /** The amount of a deferred purchase of this user, empty if it does not exist. */
    public Optional<BigDecimal> deferredPurchaseAmount(UUID userId, UUID purchaseId) {
        return jdbc.queryForList("SELECT amount FROM deferred_purchases WHERE id = ? AND user_id = ?", BigDecimal.class, purchaseId, userId)
                .stream().findFirst();
    }

    /** Principal of the other live installments of a purchase: cancelled ones were settled by a full payment (REQ-TC-12). */
    public BigDecimal otherInstallmentsPrincipal(UUID purchaseId, UUID installmentId) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(principal), 0) FROM installments
                WHERE deferred_purchase_id = ? AND id <> ? AND deleted_at IS NULL AND status <> 'CANCELLED'
                """, BigDecimal.class, purchaseId, installmentId);
    }

    public boolean installmentExists(UUID userId, UUID installmentId) {
        return exists("SELECT 1 FROM installments WHERE id = ? AND user_id = ?", installmentId, userId);
    }

    public boolean hasSubcategories(UUID userId, UUID categoryId) {
        return exists("SELECT 1 FROM categories WHERE parent_id = ? AND user_id = ? AND deleted_at IS NULL", categoryId, userId);
    }

    public boolean recurringExists(UUID userId, UUID recurringId) {
        return exists("SELECT 1 FROM recurring_expenses WHERE id = ? AND user_id = ?", recurringId, userId);
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
