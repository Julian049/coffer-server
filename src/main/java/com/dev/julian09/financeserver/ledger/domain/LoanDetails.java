package com.dev.julian09.financeserver.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Terms of a LOAN account (REQ-PRE-01 to 03, 08); its id is the account's id. */
@Entity
@Table(name = "loan_details")
public class LoanDetails extends SyncableEntity {

    @Column(nullable = false, length = 8)
    private String mode;

    @Column(name = "disbursement_account_id")
    private UUID disbursementAccountId;

    @Column(precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "installments_count")
    private Short installmentsCount;

    @Column(precision = 9, scale = 6)
    private BigDecimal rate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "rate_type", length = 2)
    private String rateType;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "payment_day", nullable = false)
    private short paymentDay;

    @Column(name = "monthly_payment", precision = 19, scale = 4)
    private BigDecimal monthlyPayment;

    @Column(name = "life_insurance_monthly", nullable = false, precision = 19, scale = 4)
    private BigDecimal lifeInsuranceMonthly;

    @Column(name = "other_costs_monthly", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherCostsMonthly;

    @Column(nullable = false, length = 15)
    private String amortization;

    protected LoanDetails() {
    }

    public String getMode() { return mode; }

    public UUID getDisbursementAccountId() { return disbursementAccountId; }

    public BigDecimal getPrincipal() { return principal; }

    public Integer getInstallmentsCount() { return installmentsCount == null ? null : installmentsCount.intValue(); }

    public BigDecimal getRate() { return rate; }

    public String getRateType() { return rateType; }

    public LocalDate getFirstDueDate() { return firstDueDate; }

    public short getPaymentDay() { return paymentDay; }

    public BigDecimal getMonthlyPayment() { return monthlyPayment; }

    public BigDecimal getLifeInsuranceMonthly() { return lifeInsuranceMonthly; }

    public BigDecimal getOtherCostsMonthly() { return otherCostsMonthly; }

    public String getAmortization() { return amortization; }
}
