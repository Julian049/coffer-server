package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Terms of a CREDIT_CARD account (REQ-TC-01 to 03); its id is the account's id. */
@Entity
@Table(name = "credit_card_details")
public class CreditCardDetails extends SyncableEntity {

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "cut_off_day", nullable = false)
    private short cutOffDay;

    @Column(name = "payment_day", nullable = false)
    private short paymentDay;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "rate_type", nullable = false, length = 2)
    private String rateType;

    @Column(name = "first_installment_interest_free", nullable = false)
    private boolean firstInstallmentInterestFree;

    protected CreditCardDetails() {
    }

    public BigDecimal getCreditLimit() { return creditLimit; }

    public short getCutOffDay() { return cutOffDay; }

    public short getPaymentDay() { return paymentDay; }

    public BigDecimal getRate() { return rate; }

    public String getRateType() { return rateType; }

    public boolean isFirstInstallmentInterestFree() { return firstInstallmentInterestFree; }
}
