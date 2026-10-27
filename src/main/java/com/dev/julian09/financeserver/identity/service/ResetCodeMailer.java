package com.dev.julian09.financeserver.identity.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends the 6-digit code through Brevo's transactional HTTP API (REQ-AUT-09). HTTP and not SMTP: Render's free plan
 * blocks outgoing SMTP ports. Without BREVO_API_KEY (local, tests) nothing is sent and the code is never logged.
 */
@Component
public class ResetCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(ResetCodeMailer.class);

    private final RestClient http;
    private final String apiKey;
    private final String from;

    public ResetCodeMailer(@Value("${finance.mail.brevo-api-key:}") String apiKey,
                           @Value("${finance.mail.from:cofferapp1@gmail.com}") String from) {
        this.http = RestClient.create("https://api.brevo.com/v3");
        this.apiKey = apiKey;
        this.from = from;
    }

    /** Best effort: a failure is logged and the user can ask again; the response never says whether it went out. */
    public void send(String to, String name, String code) {
        if (apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not set: password reset code not sent");
            return;
        }
        String text = "Hola, " + name + ".\n\nTu código para cambiar la contraseña de Coffer es " + code
                + ".\nVence en 15 minutos. Si no lo pediste, ignora este correo: tu contraseña sigue igual.";
        try {
            http.post().uri("/smtp/email")
                    .header("api-key", apiKey)
                    .body(Map.of("sender", Map.of("name", "Coffer", "email", from),
                            "to", List.of(Map.of("email", to)),
                            "subject", "Tu código de Coffer: " + code,
                            "textContent", text))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Brevo did not accept the password reset email: {}", e.getMessage());
        }
    }
}
