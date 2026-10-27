package com.dev.julian09.financeserver.identity;

import java.util.Map;
import java.util.UUID;

import com.dev.julian09.financeserver.Contract;
import com.dev.julian09.financeserver.TestcontainersConfiguration;
import com.dev.julian09.financeserver.identity.service.ResetCodeMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REQ-AUT-09 and REQ-AUT-10 against PostgreSQL 16; the mailer keeps the last code instead of calling Brevo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PasswordResetIT.CapturingMailer.class})
class PasswordResetIT {

    @TestConfiguration
    static class CapturingMailer {
        @Bean
        @Primary
        Captured capturedMailer() {
            return new Captured();
        }
    }

    static class Captured extends ResetCodeMailer {
        final Map<String, String> codes = new java.util.concurrent.ConcurrentHashMap<>();

        Captured() {
            super("", "test@coffer");
        }

        @Override
        public void send(String to, String name, String code) {
            codes.put(to.toLowerCase(), code);
        }
    }

    private static final String DEVICE = "device-reset-01";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    Captured mailer;

    @Test
    void aCodeChangesThePasswordOnceAndSignsOutOtherDevices() throws Exception {
        String username = newUsername();
        String email = username + "@correo.com";
        JsonNode session = register(username, email).andReturn().getResponse().getContentAsString().transform(json::readTree);

        requestCode(email.toUpperCase()).andExpect(status().isAccepted()).andExpect(Contract.matches());
        String code = mailer.codes.get(email);
        assertThat(code).matches("[0-9]{6}");

        confirm(email, code, "clave-nueva-123").andExpect(status().isNoContent());
        login(username, "clave-nueva-123").andExpect(status().isOk());
        login(username, "clave-segura-1").andExpect(status().isUnauthorized());
        // The session from before the change is gone
        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        // A used code does not work twice
        confirm(email, code, "otra-clave-123").andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswer() throws Exception {
        requestCode("nadie-" + UUID.randomUUID() + "@correo.com").andExpect(status().isAccepted());
        confirm("nadie-" + UUID.randomUUID() + "@correo.com", "123456", "clave-nueva-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIN-VALIDATION"));
    }

    @Test
    void fiveWrongGuessesBurnTheCode() throws Exception {
        String username = newUsername();
        String email = username + "@correo.com";
        register(username, email);
        requestCode(email);
        String code = mailer.codes.get(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            confirm(email, wrong, "clave-nueva-123", "10.0.9." + i).andExpect(status().isBadRequest());
        }
        confirm(email, code, "clave-nueva-123", "10.0.9.9").andExpect(status().isBadRequest());
    }

    private ResultActions register(String username, String email) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "displayName", "Persona de prueba", "email", email, "username", username, "password", "clave-segura-1", "deviceId", DEVICE));
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions requestCode(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirm(String email, String code, String password) throws Exception {
        return confirm(email, code, password, "10.0.8.1");
    }

    private ResultActions confirm(String email, String code, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "code", code, "newPassword", password)))
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", identifier, "password", password, "deviceId", DEVICE)))
);
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token, "deviceId", DEVICE))));
    }

    private static String newUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
