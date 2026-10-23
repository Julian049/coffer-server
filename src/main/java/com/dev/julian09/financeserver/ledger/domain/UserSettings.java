package com.dev.julian09.financeserver.ledger.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row per user (id = user id). */
@Entity
@Table(name = "user_settings")
public class UserSettings extends SyncableEntity {

    @Column(name = "reminder_lead_days", nullable = false)
    private short reminderLeadDays;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "home_balance_view", nullable = false, length = 10)
    private String homeBalanceView;

    @Column(name = "amount_decimals", nullable = false)
    private short amountDecimals;

    // Raw JSON array of {key, visible}; parsed by whoever needs it
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "home_cards", nullable = false)
    private String homeCards;

    @Column(name = "net_balance_mode", nullable = false, length = 12)
    private String netBalanceMode;

    @Column(name = "loan_payment_category_id")
    private UUID loanPaymentCategoryId;

    protected UserSettings() {
    }

    public short getReminderLeadDays() { return reminderLeadDays; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    public String getHomeBalanceView() { return homeBalanceView; }

    public short getAmountDecimals() { return amountDecimals; }

    public String getHomeCards() { return homeCards; }

    public String getNetBalanceMode() { return netBalanceMode; }

    public UUID getLoanPaymentCategoryId() { return loanPaymentCategoryId; }
}
