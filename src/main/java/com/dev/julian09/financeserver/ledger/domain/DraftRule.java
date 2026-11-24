package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A merchant (and maybe an amount and/or an account) registered with a title and a category (V20); the phone applies it. */
@Entity
@Table(name = "draft_rules")
public class DraftRule extends SyncableEntity {

    @Column(nullable = false, length = 100)
    private String merchant;

    @Column(name = "merchant_key", nullable = false, length = 100)
    private String merchantKey;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, length = 10)
    private String origin;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "learned_since")
    private Instant learnedSince;

    @Column(nullable = false)
    private int position;

    protected DraftRule() {
    }

    public String getMerchant() { return merchant; }

    public String getMerchantKey() { return merchantKey; }

    public BigDecimal getAmount() { return amount; }

    public UUID getAccountId() { return accountId; }

    public String getNote() { return note; }

    public UUID getCategoryId() { return categoryId; }

    public String getLevel() { return level; }

    public String getOrigin() { return origin; }

    public boolean isEnabled() { return enabled; }

    public Instant getLearnedSince() { return learnedSince; }

    public int getPosition() { return position; }
}
