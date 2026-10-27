package com.dev.julian09.financeserver.notify;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * FCM HTTP v1 with the Firebase service account in FCM_CREDENTIALS (its JSON), signed here instead of pulling in the
 * Firebase Admin SDK. Only a data message travels: the phone counts its own drafts (ADR-0016).
 */
@Component
public class FcmSender {

    public enum Result { SENT, GONE, FAILED }

    private final RestClient http = RestClient.create();
    private final Clock clock;
    private final JsonNode account;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public FcmSender(@Value("${finance.notify.fcm-credentials:}") String credentials, JsonMapper json, Clock clock) {
        this.clock = clock;
        this.account = credentials.isBlank() ? null : json.readTree(credentials);
    }

    public boolean enabled() {
        return account != null;
    }

    /** GONE: the phone uninstalled the app or the token rotated, so it should be forgotten. */
    public Result send(String deviceToken) {
        try {
            http.post()
                    .uri("https://fcm.googleapis.com/v1/projects/{p}/messages:send", account.get("project_id").asText())
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", Map.of("token", deviceToken, "data", Map.of("kind", "drafts"),
                            "android", Map.of("priority", "high"))))
                    .retrieve().toBodilessEntity();
            return Result.SENT;
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404)) ? Result.GONE : Result.FAILED;
        } catch (RuntimeException e) {
            return Result.FAILED;
        }
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(tokenExpires.minusSeconds(60))) {
            return token;
        }
        try {
            String pem = account.get("private_key").asText()
                    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            SignedJWT assertion = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                    .issuer(account.get("client_email").asText())
                    .audience("https://oauth2.googleapis.com/token")
                    .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build());
            assertion.sign(new RSASSASigner(key));
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion.serialize());
            JsonNode answer = http.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            token = answer.get("access_token").asText();
            tokenExpires = now.plusSeconds(answer.get("expires_in").asLong());
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("Could not get an FCM access token", e);
        }
    }
}
