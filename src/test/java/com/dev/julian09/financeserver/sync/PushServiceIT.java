package com.dev.julian09.financeserver.sync;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dev.julian09.financeserver.Contract;
import com.dev.julian09.financeserver.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/v1/sync/push against PostgreSQL 16 (S3-QA01, REQ-SYN-01 to 04, RNF-17). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PushServiceIT {

    private static final Instant T1 = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T11:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private UUID userId;
    private final UUID account = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();
    private final UUID restaurants = UUID.randomUUID();
    private final UUID salary = UUID.randomUUID();
    private final UUID trip = UUID.randomUUID();

    @BeforeEach
    void user() throws Exception {
        String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = json.writeValueAsString(Map.of("displayName", "Push", "email", name + "@correo.com", "username", name,
                "password", "clave-segura-1", "deviceId", "device-0001"));
        JsonNode session = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        token = session.get("accessToken").asText();
        userId = UUID.fromString(session.get("user").get("id").asText());
    }

    @Test
    void aWholeBatchIsAppliedInDependencyOrderAndCanBePulled() throws Exception {
        UUID expense = UUID.randomUUID();
        // Deliberately out of order: the transaction comes before what it references
        push(
                change("transaction", expense, T1, null, expense(28000, restaurants)),
                change("category", restaurants, T1, null, category("Restaurantes", "EXPENSE", food)),
                change("tag", trip, T1, null, Map.of("name", "Viaje", "pinned", false)),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("account", account, T1, null, account("Bancolombia")),
                change("settings", userId, T1, null, settings()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(6))
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(jsonPath("$.results[0].id").value(expense.toString()))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString());
        assertThat(pulled.get("changes")).hasSize(6);
        assertThat(pulled.get("changes").findValuesAsString("entity")).containsExactly(
                "settings", "account", "category", "category", "tag", "transaction");
    }

    @Test
    void aCategoryExcludedFromReportsSyncsBothWaysAndOlderClientsLeaveItIncluded() throws Exception {
        UUID savings = UUID.randomUUID();
        Map<String, Object> excluded = category("Ahorros", "EXPENSE", null);
        excluded.put("excludeFromReports", true);
        // A v1.0.x app does not send the field (REQ-CAT-04, openapi 1.1.0)
        push(change("category", savings, T1, null, excluded),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString());
        Map<String, Boolean> flags = new LinkedHashMap<>();
        pulled.get("changes").forEach(c -> flags.put(c.get("id").asText(), c.get("data").get("excludeFromReports").asBoolean()));
        assertThat(flags).containsEntry(savings.toString(), true).containsEntry(food.toString(), false);
    }

    @Test
    void aLoanWithItsTermsAndPlanSyncsBothWaysAndEachModeNeedsItsFields() throws Exception {
        UUID loan = UUID.randomUUID();
        UUID simple = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        Map<String, Object> detailed = new LinkedHashMap<>(Map.of("mode", "DETAILED", "principal", "5000000.0000",
                "installmentsCount", 24, "rate", "0.015000", "rateType", "MV", "firstDueDate", "2026-11-05", "paymentDay", 5,
                "lifeInsuranceMonthly", "8000.0000", "otherCostsMonthly", "0.0000"));
        detailed.put("disbursementAccountId", account.toString());
        // A v1.1.x client does not send amortization (openapi 1.2.0): it stays FIXED_PRINCIPAL
        Map<String, Object> missingPayment = Map.of("mode", "SIMPLE", "paymentDay", 5, "lifeInsuranceMonthly", "0.0000",
                "otherCostsMonthly", "0.0000");
        push(
                change("installment", first, T1, null, installment(null, loan, 1, "208333.0000", "75000.0000", "SCHEDULED")),
                change("loanDetails", loan, T1, null, detailed),
                change("loanDetails", simple, T1, null, missingPayment),
                change("account", simple, T1, null, loanAccount("Vehículo")),
                change("account", loan, T1, null, loanAccount("Libre inversión")),
                change("account", account, T1, null, account("Bancolombia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andExpect(Contract.matchesComposed())
                .andReturn().getResponse().getContentAsString());
        JsonNode terms = null;
        for (JsonNode c : pulled.get("changes")) {
            if (c.get("entity").asString().equals("loanDetails")) {
                terms = c.get("data");
            }
        }
        assertThat(terms).isNotNull();
        assertThat(terms.get("amortization").asString()).isEqualTo("FIXED_PRINCIPAL");
        assertThat(terms.get("installmentsCount").asInt()).isEqualTo(24);
        assertThat(terms.get("disbursementAccountId").asString()).isEqualTo(account.toString());
        assertThat(pulled.get("changes").findValuesAsString("entity")).containsSubsequence("account", "loanDetails", "installment");
    }

    @Test
    void theHistoryOfATransactionSyncsBothWays() throws Exception {
        UUID expense = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        Map<String, Object> after = expense(28000, food);
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("transaction", expense, T1, null, after),
                change("changeLog", created, T1, null, history(expense, "CREATE", null, after)),
                change("changeLog", deleted, T2, null, history(expense, "DELETE", after, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(Contract.matchesComposed());

        // A JSON null is stored as SQL NULL, not as the jsonb value null
        assertThat(jdbc.queryForObject("SELECT before_value IS NULL FROM change_log WHERE id = ?", Boolean.class, created)).isTrue();
        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andExpect(Contract.matchesComposed())
                .andReturn().getResponse().getContentAsString());
        JsonNode history = pulled.get("changes").get(4);
        assertThat(history.get("entity").asString()).isEqualTo("changeLog");
        assertThat(history.get("data").get("action").asString()).isEqualTo("DELETE");
        assertThat(history.get("data").get("beforeValue").get("amount").asString()).isEqualTo("28000.0000");
        assertThat(history.get("data").get("afterValue").isNull()).isTrue();
    }

    @Test
    void aRecurringExpenseAndItsPaymentSyncBothWays() throws Exception {
        UUID rent = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        Map<String, Object> paid = new LinkedHashMap<>(expense(900000, food));
        paid.put("recurringId", rent.toString());
        // The payment comes before the recurring expense it belongs to
        push(
                change("transaction", payment, T1, null, paid),
                change("recurringExpense", rent, T1, null, recurring("Arriendo", food, "MONTHLY", null, null)),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("account", account, T1, null, account("Bancolombia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andExpect(Contract.matchesComposed())
                .andReturn().getResponse().getContentAsString());
        JsonNode data = pulled.get("changes").get(2).get("data");
        assertThat(pulled.get("changes").get(2).get("entity").asString()).isEqualTo("recurringExpense");
        assertThat(data.get("nextDueDate").asString()).isEqualTo("2026-11-05");
        assertThat(data.get("lastPaidAmount").asString()).isEqualTo("900000.0000");
        assertThat(data.get("intervalCount").isNull()).isTrue();
        assertThat(pulled.get("changes").get(3).get("data").get("recurringId").asString()).isEqualTo(rent.toString());
    }

    @Test
    void recurringExpensesAreOnlyExpensesWithAConsistentFrequency() throws Exception {
        Map<String, Object> orphanPayment = new LinkedHashMap<>(expense(1000, food));
        orphanPayment.put("recurringId", UUID.randomUUID().toString());
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("category", salary, T1, null, category("Salario", "INCOME", null)),
                change("recurringExpense", UUID.randomUUID(), T1, null, recurring("Salario", salary, "MONTHLY", null, null)),
                change("recurringExpense", UUID.randomUUID(), T1, null, recurring("Gym", food, "CUSTOM", null, null)),
                change("recurringExpense", UUID.randomUUID(), T1, null, recurring("Gym", food, "WEEKLY", 2, "WEEK")),
                change("recurringExpense", UUID.randomUUID(), T1, null, recurring("Gym", food, "CUSTOM", 2, "WEEK")),
                change("transaction", UUID.randomUUID(), T1, null, orphanPayment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[3].detail").value("Los recurrentes solo aplican a gastos"))
                .andExpect(jsonPath("$.results[4].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[5].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[6].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[7].code").value("FIN-REFERENCE"))
                .andExpect(Contract.matchesComposed());
    }

    @Test
    void aCardPurchaseInInstallmentsSyncsBothWays() throws Exception {
        UUID card = UUID.randomUUID();
        UUID laptop = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // Out of order: the plan comes before its purchase and the card terms before the card
        push(
                change("installment", second, T1, null, installment(plan, null, 2, "600000.0000", "12000.0000", "SCHEDULED")),
                change("installment", first, T1, null, installment(plan, null, 1, "600000.0000", "0.0000", "SCHEDULED")),
                change("deferredPurchase", plan, T1, null, deferred(card, laptop, "1200000.0000")),
                change("transaction", laptop, T1, null, withAccount(expense(1200000, food), card)),
                change("creditCardDetails", card, T1, null, cardTerms()),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("account", card, T1, null, card("Visa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andExpect(Contract.matchesComposed())
                .andReturn().getResponse().getContentAsString());
        assertThat(pulled.get("changes").findValuesAsString("entity")).containsExactly(
                "account", "creditCardDetails", "category", "transaction", "deferredPurchase", "installment", "installment");
        JsonNode terms = pulled.get("changes").get(1).get("data");
        assertThat(terms.get("rate").asString()).isEqualTo("0.020000");
        assertThat(terms.get("rateType").asString()).isEqualTo("MV");
        assertThat(terms.get("cutOffDay").asInt()).isEqualTo(20);
        assertThat(pulled.get("changes").get(4).get("data").get("installmentsCount").asInt()).isEqualTo(2);
        JsonNode last = pulled.get("changes").get(5).get("data"); // the second installment came first in the batch
        assertThat(last.get("deferredPurchaseId").asString()).isEqualTo(plan.toString());
        assertThat(last.get("interest").asString()).isEqualTo("12000.0000");
        assertThat(last.get("loanAccountId").isNull()).isTrue();
    }

    @Test
    void cardTermsPurchasesAndPlansMustBeConsistent() throws Exception {
        UUID card = UUID.randomUUID();
        UUID laptop = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        Map<String, Object> payment = new LinkedHashMap<>(expense(1000, food));
        payment.put("installmentId", UUID.randomUUID().toString());
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("account", card, T1, null, card("Visa")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("creditCardDetails", account, T1, null, cardTerms()),
                change("transaction", laptop, T1, null, withAccount(expense(1200000, food), card)),
                change("deferredPurchase", UUID.randomUUID(), T1, null, deferred(card, laptop, "1000000.0000")),
                change("deferredPurchase", plan, T1, null, deferred(card, laptop, "1200000.0000")),
                change("installment", first, T1, null, installment(plan, null, 1, "600000.0000", "0.0000", "SCHEDULED")),
                change("installment", UUID.randomUUID(), T1, null, installment(plan, null, 2, "700000.0000", "0.0000", "SCHEDULED")),
                change("installment", UUID.randomUUID(), T1, null, installment(plan, null, 2, "700000.0000", "0.0000", "CANCELLED")),
                change("installment", UUID.randomUUID(), T1, null, installment(plan, card, 2, "1.0000", "0.0000", "SCHEDULED")),
                change("transaction", UUID.randomUUID(), T1, null, payment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[3].detail").value("Las condiciones de crédito son de una tarjeta"))
                .andExpect(jsonPath("$.results[5].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[6].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[7].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[8].detail").value("El capital de las cuotas supera el monto de la compra"))
                .andExpect(jsonPath("$.results[9].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[10].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[11].code").value("FIN-REFERENCE"))
                .andExpect(Contract.matchesComposed());
    }

    @Test
    void resendingTheSameBatchCreatesNoDuplicates() throws Exception {
        Object[] batch = {
            change("account", account, T1, null, account("Bancolombia")),
            change("category", food, T1, null, category("Comida", "EXPENSE", null)),
            change("transaction", UUID.randomUUID(), T1, null, expense(5000, food))};
        push(batch);

        push(batch)
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("STALE"))))
                .andExpect(jsonPath("$.results[0].version").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void theLatestChangeWins() throws Exception {
        push(change("account", account, T2, null, account("Nueva")));

        push(change("account", account, T1, null, account("Vieja")))
                .andExpect(jsonPath("$.results[0].status").value("STALE"));
        push(change("account", account, T2.plusSeconds(1), null, account("Más nueva")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].version").isNumber());
        assertThat(jdbc.queryForObject("SELECT name FROM accounts WHERE id = ?", String.class, account)).isEqualTo("Más nueva");
    }

    @Test
    void deletionsAreStoredAndTravel() throws Exception {
        push(change("account", account, T1, null, account("Bancolombia")));
        push(change("account", account, T2, T2, account("Bancolombia")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM accounts WHERE id = ?", Boolean.class, account)).isTrue();
    }

    @Test
    void anotherUsersIdIsRejectedWithoutTouchingIt() throws Exception {
        UUID stranger = UUID.randomUUID();
        String name = "x" + stranger.toString().replace("-", "").substring(0, 12);
        jdbc.update("INSERT INTO users (id, username, email, display_name, password_hash) VALUES (?, ?, ?, 'Otro', '!')",
                stranger, name, name + "@correo.com");
        jdbc.update("INSERT INTO tags (id, user_id, name, created_at, updated_at) VALUES (?, ?, 'Ajena', ?, ?)",
                trip, stranger, Timestamp.from(T1), Timestamp.from(T1));

        push(change("tag", trip, T2, null, Map.of("name", "Mía", "pinned", true)))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("FIN-NOT-OWNER"))
                .andExpect(Contract.matchesComposed());
        assertThat(jdbc.queryForObject("SELECT name FROM tags WHERE id = ?", String.class, trip)).isEqualTo("Ajena");
    }

    @Test
    void oneBadChangeDoesNotSinkTheBatch() throws Exception {
        UUID orphan = UUID.randomUUID();
        UUID transferWithoutDestination = UUID.randomUUID();
        UUID wrongKind = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        Map<String, Object> transfer = new LinkedHashMap<>(expense(1000, null));
        transfer.put("type", "TRANSFER");
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("category", salary, T1, null, category("Salario", "INCOME", null)),
                change("transaction", orphan, T1, null, withAccount(expense(1000, food), UUID.randomUUID())),
                change("transaction", transferWithoutDestination, T1, null, transfer),
                change("transaction", wrongKind, T1, null, expense(1000, salary)),
                change("transaction", ok, T1, null, expense(1000, food)),
                change("loanDetails", UUID.randomUUID(), T1, null, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[3].code").value("FIN-REFERENCE"))
                .andExpect(jsonPath("$.results[4].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[5].detail").value("La categoría no corresponde al tipo de movimiento"))
                .andExpect(jsonPath("$.results[6].status").value("APPLIED"))
                // An entity without a handler yet; its empty data breaks the contract on purpose, so no contract check here
                .andExpect(jsonPath("$.results[7].status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void anAccountKeepsItsTypeAndCategoriesKeepOneLevel() throws Exception {
        Map<String, Object> asSavings = new LinkedHashMap<>(account("Bancolombia"));
        asSavings.put("type", "SAVINGS");
        UUID lunch = UUID.randomUUID();
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("category", salary, T1, null, category("Salario", "INCOME", null)),
                change("category", restaurants, T1, null, category("Restaurantes", "EXPENSE", food)))
                .andExpect(jsonPath("$.results[*].status", everyItem(is("APPLIED"))));
        push(
                change("account", account, T2, null, asSavings),
                change("account", account, T2, null, account("Bancolombia Nómina")),
                change("category", lunch, T1, null, category("Almuerzos", "EXPENSE", restaurants)),
                change("category", food, T2, null, category("Comida", "EXPENSE", salary)),
                change("category", UUID.randomUUID(), T1, null, category("Bonos", "EXPENSE", salary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].detail").value("El tipo de una cuenta no se puede cambiar"))
                .andExpect(jsonPath("$.results[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[2].detail").value("Las subcategorías tienen un solo nivel"))
                .andExpect(jsonPath("$.results[3].detail").value("Las subcategorías tienen un solo nivel"))
                .andExpect(jsonPath("$.results[4].detail").value("La subcategoría debe ser del mismo tipo que su categoría"))
                .andExpect(Contract.matchesComposed());
        assertThat(jdbc.queryForObject("SELECT type FROM accounts WHERE id = ?", String.class, account)).isEqualTo("DEBIT");
    }

    @Test
    void aDuplicateNameRollsBackOnlyThatChange() throws Exception {
        push(
                change("tag", UUID.randomUUID(), T1, null, Map.of("name", "Viaje", "pinned", false)),
                change("tag", UUID.randomUUID(), T1, null, Map.of("name", "viaje", "pinned", false)),
                change("account", account, T1, null, account("Bancolombia")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].detail").value("Ya existe un registro con ese nombre"))
                .andExpect(jsonPath("$.results[2].status").value("APPLIED"));
    }

    @Test
    void invalidAmountsAndOversizedBatchesAreRefused() throws Exception {
        push(change("transaction", UUID.randomUUID(), T1, null, expense(0, food)))
                .andExpect(jsonPath("$.results[0].code").value("FIN-VALIDATION"));

        Object[] tooMany = new Object[201];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = change("tag", UUID.randomUUID(), T1, null, Map.of("name", "t" + i, "pinned", false));
        }
        push(tooMany).andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("FIN-VALIDATION"));
        push().andExpect(status().isBadRequest());
    }

    private ResultActions push(Object... changes) throws Exception {
        String body = json.writeValueAsString(Map.of("deviceId", "device-0001", "changes", List.of(changes)));
        return mvc.perform(post("/api/v1/sync/push").contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", bearer()));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private static Map<String, Object> change(String entity, UUID id, Instant updatedAt, Instant deletedAt, Map<String, Object> data) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", entity);
        change.put("id", id.toString());
        change.put("createdAt", T1.toString());
        change.put("updatedAt", updatedAt.toString());
        change.put("deletedAt", deletedAt == null ? null : deletedAt.toString());
        change.put("data", data);
        return change;
    }

    private Map<String, Object> account(String name) {
        return Map.of("name", name, "type", "DEBIT", "initialBalance", "1250000.0000", "includeInTotal", true, "active", true,
                "sortOrder", 0);
    }

    private static Map<String, Object> loanAccount(String name) {
        return Map.of("name", name, "type", "LOAN", "initialBalance", "0.0000", "includeInTotal", true, "active", true,
                "sortOrder", 0);
    }

    private static Map<String, Object> category(String name, String kind, UUID parent) {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("name", name, "kind", kind, "isBase", true, "sortOrder", 0));
        data.put("parentId", parent == null ? null : parent.toString());
        return data;
    }

    private Map<String, Object> expense(long pesos, UUID category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "EXPENSE");
        data.put("amount", pesos + ".0000");
        data.put("occurredAt", T1.toString());
        data.put("accountId", account.toString());
        data.put("categoryId", category == null ? null : category.toString());
        data.put("installments", 1);
        return data;
    }

    private static Map<String, Object> withAccount(Map<String, Object> data, UUID accountId) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("accountId", accountId.toString());
        return copy;
    }

    private static Map<String, Object> history(UUID transaction, String action, Map<String, Object> before,
                                               Map<String, Object> after) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transactionId", transaction.toString());
        data.put("action", action);
        data.put("beforeValue", before);
        data.put("afterValue", after);
        data.put("changedAt", T1.toString());
        return data;
    }

    private Map<String, Object> recurring(String name, UUID category, String frequency, Integer count, String unit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("estimatedAmount", "900000.0000");
        data.put("lastPaidAmount", "900000.0000");
        data.put("accountId", account.toString());
        data.put("categoryId", category.toString());
        data.put("startDate", "2026-10-05");
        data.put("frequency", frequency);
        data.put("intervalCount", count);
        data.put("intervalUnit", unit);
        data.put("nextDueDate", "2026-11-05");
        data.put("lastNotifiedOn", null);
        data.put("active", true);
        return data;
    }

    private static Map<String, Object> card(String name) {
        return Map.of("name", name, "type", "CREDIT_CARD", "initialBalance", "0.0000", "includeInTotal", true, "active", true,
                "sortOrder", 0);
    }

    private static Map<String, Object> cardTerms() {
        return Map.of("creditLimit", "5000000.0000", "cutOffDay", 20, "paymentDay", 5, "rate", "0.020000", "rateType", "MV",
                "firstInstallmentInterestFree", true);
    }

    private static Map<String, Object> deferred(UUID card, UUID purchase, String amount) {
        return Map.of("cardAccountId", card.toString(), "purchaseTransactionId", purchase.toString(), "amount", amount,
                "installmentsCount", 2, "rate", "0.020000", "rateType", "MV", "firstInstallmentInterestFree", true,
                "firstDueDate", "2026-11-05");
    }

    private static Map<String, Object> installment(UUID purchase, UUID loan, int number, String principal, String interest,
                                                   String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deferredPurchaseId", purchase == null ? null : purchase.toString());
        data.put("loanAccountId", loan == null ? null : loan.toString());
        data.put("number", number);
        data.put("dueDate", "2026-1" + number + "-05");
        data.put("principal", principal);
        data.put("interest", interest);
        data.put("insurance", "0.0000");
        data.put("otherCosts", "0.0000");
        data.put("status", status);
        data.put("paidAt", null);
        return data;
    }

    private Map<String, Object> settings() {
        return Map.of("reminderLeadDays", 5, "notificationsEnabled", true, "homeBalanceView", "AVAILABLE", "amountDecimals", 0,
                "homeCards", new ArrayList<>());
    }
}
