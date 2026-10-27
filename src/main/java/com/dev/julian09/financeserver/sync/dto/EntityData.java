package com.dev.julian09.financeserver.sync.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * The {@code data} member of a change, one record per entity (openapi.yml *Data schemas). The constraints mirror the
 * contract and run on every pushed change; rules across fields or rows live in the sync handlers.
 */
public sealed interface EntityData {

    record SettingsData(@Min(0) @Max(30) int reminderLeadDays, boolean notificationsEnabled,
                        @NotNull @Pattern(regexp = "AVAILABLE|NET") String homeBalanceView,
                        @Min(0) @Max(2) int amountDecimals, @NotNull JsonNode homeCards,
                        @Pattern(regexp = "PERIOD|CUSTOM|TOTAL_DEBT") String netBalanceMode, UUID loanPaymentCategoryId,
                        Boolean notifyDrafts) implements EntityData {
    }

    record AccountData(@NotBlank @Size(max = 60) String name,
                       @NotNull @Pattern(regexp = "CASH|DEBIT|SAVINGS|CREDIT_CARD|LOAN") String type,
                       @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal initialBalance,
                       boolean includeInTotal, boolean active, Instant deactivatedAt, @Size(max = 60) String institution,
                       @Size(max = 30) String icon, @Pattern(regexp = "^#[0-9A-F]{6}$") String color, int sortOrder,
                       Boolean includeInNet, @Pattern(regexp = "^([0-9]{4})?$") String lastDigits) implements EntityData {
        /** Optional since openapi 1.5.0: older apps do not send it, and every debt then counts. */
        public boolean inNet() {
            return !Boolean.FALSE.equals(includeInNet);
        }

    }

    record CategoryData(UUID parentId, @NotBlank @Size(max = 50) String name,
                        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String kind, boolean isBase,
                        @Pattern(regexp = "FINANCIAL_CHARGES|OPENING_BALANCE") String systemKey, @Size(max = 30) String icon,
                        int sortOrder, Boolean excludeFromReports) implements EntityData {
        /** Optional since openapi 1.1.0: v1.0.x apps do not send it (REQ-CAT-04). */
        public boolean excluded() {
            return Boolean.TRUE.equals(excludeFromReports);
        }

    }

    /** color is optional: 1.6 clients do not send it. */
    /** REQ-COR-02: created by the server from a bank email; the phone applies or discards it (pushes it deleted). */
    record TransactionDraftData(@NotNull @Pattern(regexp = "BANCOLOMBIA|RAPPICARD") String source,
                                @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
                                @NotNull Instant occurredAt, @Size(max = 200) String merchant,
                                @Pattern(regexp = "^[0-9]{4}$") String lastDigits) implements EntityData {
    }

    record TagData(@NotBlank @Size(max = 50) String name, boolean pinned, @Pattern(regexp = "^#[0-9A-F]{6}$") String color)
            implements EntityData {
    }

    record TransactionData(@NotNull @Pattern(regexp = "EXPENSE|INCOME|TRANSFER") String type,
                           @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
                           @NotNull Instant occurredAt, @NotNull UUID accountId, UUID destinationAccountId, UUID categoryId,
                           UUID tagId, @Size(max = 500) String note, @Min(1) @Max(72) int installments,
                           @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal principal,
                           @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal interest,
                           @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal fees, UUID installmentId,
                           UUID recurringId,
                           @Pattern(regexp = "CARD_MIN|CARD_TOTAL|LOAN_INSTALL|LOAN_EXTRA") String paymentKind,
                           @Size(max = 200) String concept, @Size(max = 20) List<UUID> tagIds) implements EntityData {

        /**
         * ADR-0015 expand: a 1.6 client sends only tagId, a 2.0 client sends tagIds; both are kept in step.
         */
        public List<UUID> effectiveTagIds() {
            if (tagIds != null) {
                return tagIds.stream().distinct().toList();
            }
            return tagId == null ? List.of() : List.of(tagId);
        }
    }

    record RecurringExpenseData(@NotBlank @Size(max = 60) String name,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal estimatedAmount,
                                @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal lastPaidAmount,
                                @NotNull UUID accountId, @NotNull UUID categoryId, @NotNull LocalDate startDate,
                                @NotNull @Pattern(regexp = "WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|YEARLY|CUSTOM") String frequency,
                                @Min(1) @Max(365) Integer intervalCount, @Pattern(regexp = "DAY|WEEK|MONTH") String intervalUnit,
                                @NotNull LocalDate nextDueDate, LocalDate lastNotifiedOn, boolean active) implements EntityData {
    }

    /** The envelope id is the CREDIT_CARD account's id. Rates are fractions (0.020000 = 2 %). */
    record CreditCardDetailsData(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal creditLimit,
                                 @Min(1) @Max(31) int cutOffDay, @Min(1) @Max(31) int paymentDay,
                                 @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 6) BigDecimal rate,
                                 @NotNull @Pattern(regexp = "EA|MV") String rateType,
                                 boolean firstInstallmentInterestFree) implements EntityData {
    }

    record DeferredPurchaseData(@NotNull UUID cardAccountId, @NotNull UUID purchaseTransactionId,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
                                @Min(2) @Max(72) int installmentsCount,
                                @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 6) BigDecimal rate,
                                @NotNull @Pattern(regexp = "EA|MV") String rateType, boolean firstInstallmentInterestFree,
                                @NotNull LocalDate firstDueDate) implements EntityData {
    }

    /** REQ-PRE-01 to 03, 08; {@code amortization} is optional since openapi 1.2.0 (null = FIXED_PRINCIPAL). */
    record LoanDetailsData(@NotNull @Pattern(regexp = "DETAILED|SIMPLE") String mode, UUID disbursementAccountId,
                           @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal principal,
                           @Min(1) @Max(600) Integer installmentsCount,
                           @DecimalMin("0") @Digits(integer = 3, fraction = 6) BigDecimal rate,
                           @Pattern(regexp = "EA|MV") String rateType, LocalDate firstDueDate,
                           @Min(1) @Max(31) int paymentDay,
                           @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal monthlyPayment,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal lifeInsuranceMonthly,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal otherCostsMonthly,
                           @Pattern(regexp = "FIXED_PAYMENT|FIXED_PRINCIPAL") String amortization) implements EntityData {
        public String amortizationOrDefault() {
            return amortization == null ? "FIXED_PRINCIPAL" : amortization;
        }
    }

    record InstallmentData(UUID deferredPurchaseId, UUID loanAccountId, @Min(1) @Max(32767) int number, @NotNull LocalDate dueDate,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal principal,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal interest,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal insurance,
                           @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal otherCosts,
                           @NotNull @Pattern(regexp = "SCHEDULED|PAID|CANCELLED") String status, Instant paidAt) implements EntityData {
    }

    /** Before and after are TransactionData snapshots (or null); stored as they come, as an audit trail. */
    record ChangeLogData(@NotNull UUID transactionId, @NotNull @Pattern(regexp = "CREATE|UPDATE|DELETE|RESTORE") String action,
                         JsonNode beforeValue, JsonNode afterValue, @NotNull Instant changedAt) implements EntityData {
    }
}
