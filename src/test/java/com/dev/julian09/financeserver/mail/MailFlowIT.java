package com.dev.julian09.financeserver.mail;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-0014 end to end without Gmail: an email → a draft of the +code's user → pulled → deleted from the phone. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MailFlowIT {

    private static final String RECEIPT = "Bancolombia: Pagaste $146,350.29 a Banco Caja Social S A Comerc desde tu producto 2309 "
            + "el 26/10/2026 21:33:20. ¿Dudas? Llamanos al 6045109095. Estamos cerca";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InboundMessageService inbound;

    private String token;
    private String address;

    @BeforeEach
    void user() throws Exception {
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = json.writeValueAsString(Map.of("displayName", "Correo", "email", name + "@correo.com", "username", name,
                "password", "clave-segura-1", "deviceId", "device-0001"));
        JsonNode session = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        token = "Bearer " + session.get("accessToken").asText();
        address = session.get("user").get("forwardingAddress").asText();
    }

    @Test
    void aForwardedReceiptBecomesOneDraftThatThePhonePullsAndDeletes() throws Exception {
        assertThat(address).matches("cofferapp1\\+[a-z2-9]{8}@gmail\\.com");
        String messageId = "<" + UUID.randomUUID() + "@bancolombia.com.co>";

        assertThat(inbound.receive(messageId, List.of("yo@gmail.com", address), "alertas@bancolombia.com.co", "Alertas", RECEIPT))
                .isEqualTo(InboundMessageService.Outcome.PARSED);
        // REQ-COR-03: Gmail sending it again changes nothing
        assertThat(inbound.receive(messageId, List.of(address), "alertas@bancolombia.com.co", "Alertas", RECEIPT))
                .isEqualTo(InboundMessageService.Outcome.DUPLICATE);

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", token))
                .andExpect(Contract.matchesComposed())
                .andReturn().getResponse().getContentAsString());
        List<JsonNode> drafts = pulled.get("changes").valueStream().filter(c -> c.get("entity").asText().equals("transactionDraft")).toList();
        assertThat(drafts).hasSize(1);
        JsonNode draft = drafts.getFirst();
        assertThat(draft.get("data").get("amount").asText()).isEqualTo("146350.2900");
        assertThat(draft.get("data").get("lastDigits").asText()).isEqualTo("2309");

        // Applied on the phone: it comes back deleted, and its data cannot be rewritten
        Map<String, Object> change = new java.util.LinkedHashMap<>();
        change.put("entity", "transactionDraft");
        change.put("id", draft.get("id").asText());
        change.put("createdAt", draft.get("createdAt").asText());
        change.put("updatedAt", "2030-01-01T00:00:00Z");
        change.put("deletedAt", "2030-01-01T00:00:00Z");
        change.put("data", Map.of("source", "BANCOLOMBIA", "type", "EXPENSE", "amount", "1.0000", "occurredAt", "2026-10-27T02:33:20Z"));
        mvc.perform(post("/api/v1/sync/push").contentType(MediaType.APPLICATION_JSON).header("Authorization", token)
                        .content(json.writeValueAsString(Map.of("deviceId", "device-0001", "changes", List.of(change)))))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
        Map<String, Object> row = jdbc.queryForMap("SELECT amount, deleted_at FROM transaction_drafts WHERE id = ?::uuid", draft.get("id").asText());
        assertThat(row.get("amount").toString()).isEqualTo("146350.2900");
        assertThat(row.get("deleted_at")).isNotNull();
        // The email's body is not kept once it was read (REQ-COR-09)
        assertThat(jdbc.queryForObject("SELECT body FROM inbound_messages WHERE message_id = ?", String.class, messageId)).isNull();
    }

    @Test
    void gmailsConfirmationShowsInTheProfileAndAnUnknownTextIsKeptToFixTheReader() throws Exception {
        inbound.receive("<" + UUID.randomUUID() + "@google.com>", List.of(address), "Gmail Team <forwarding-noreply@google.com>",
                "(#987654321) Gmail Forwarding Confirmation - Receive Mail from yo@gmail.com", "Para confirmar…");
        mvc.perform(get("/api/v1/me").header("Authorization", token))
                .andExpect(jsonPath("$.forwardingConfirmation").value("987654321"))
                .andExpect(Contract.matches());

        String messageId = "<" + UUID.randomUUID() + "@bancolombia.com.co>";
        assertThat(inbound.receive(messageId, List.of(address), "alertas@bancolombia.com.co", "Extracto", "Tu extracto está listo"))
                .isEqualTo(InboundMessageService.Outcome.FAILED);
        assertThat(jdbc.queryForObject("SELECT body FROM inbound_messages WHERE message_id = ?", String.class, messageId))
                .isEqualTo("Tu extracto está listo");
        assertThat(inbound.receive("<" + UUID.randomUUID() + "@x>", List.of("cofferapp1+nadie999@gmail.com"), "x", "x", RECEIPT))
                .isEqualTo(InboundMessageService.Outcome.IGNORED);
    }

    @Test
    void aPhoneRegistersItsToken() throws Exception {
        mvc.perform(put("/api/v1/me/device-token").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-token-1\"}"))
                .andExpect(status().isNoContent())
                .andExpect(Contract.matches());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token = 'fcm-token-1'", Integer.class)).isEqualTo(1);
    }
}
