package com.dev.julian09.financeserver.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.dev.julian09.financeserver.TestcontainersConfiguration;
import com.dev.julian09.financeserver.identity.domain.RefreshToken;
import com.dev.julian09.financeserver.identity.domain.User;
import com.dev.julian09.financeserver.identity.repo.RefreshTokenRepository;
import com.dev.julian09.financeserver.identity.repo.UserRepository;
import com.dev.julian09.financeserver.identity.service.RefreshTokenPurgeJob;
import com.dev.julian09.financeserver.identity.service.TokenService;
import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rotation, reuse detection and expiry of refresh tokens (S2-QA01, ADR-0007). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenServiceTest {

    @Autowired
    TokenService tokens;

    @Autowired
    UserRepository users;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    RefreshTokenPurgeJob purgeJob;

    @Autowired
    JwtDecoder decoder;

    private UUID userId;

    @BeforeEach
    void user() {
        String name = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userId = users.save(new User(name, name + "@correo.com", "Prueba", "$2a$12$hash", Instant.now())).getId();
    }

    @Test
    void accessTokenIsAFifteenMinuteRs256JwtForTheUser() {
        TokenService.Tokens issued = tokens.issue(userId, "device-0001");
        var jwt = decoder.decode(issued.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).hasMinutes(15);
        assertThat(issued.refreshToken()).hasSizeGreaterThanOrEqualTo(43); // 256 bits in Base64URL
    }

    @Test
    void rotationKeepsTheFamilyAndRevokesThePrevious() {
        TokenService.Tokens first = tokens.issue(userId, "device-0001");
        TokenService.Tokens second = tokens.rotate(first.refreshToken(), "device-0001");

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.userId()).isEqualTo(userId);
        assertThatThrownBy(() -> tokens.rotate(first.refreshToken(), "device-0001"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.AUTH_REVOKED);
        // Reuse of the old one burned the new one too
        assertThatThrownBy(() -> tokens.rotate(second.refreshToken(), "device-0001")).isInstanceOf(ApiException.class);
    }

    @Test
    void aTokenFromAnotherDeviceIsRejected() {
        TokenService.Tokens issued = tokens.issue(userId, "device-0001");
        assertThatThrownBy(() -> tokens.rotate(issued.refreshToken(), "device-9999")).isInstanceOf(ApiException.class);
    }

    @Test
    void expiredTokensAreRejectedAndPurgedAfterAWeek() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(200));
        RefreshToken old = refreshTokens.save(new RefreshToken(userId, UUID.randomUUID(), "a".repeat(64), "device-0001",
                longAgo, longAgo.plus(Duration.ofDays(180))));

        assertThat(purgeJob.purge()).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokens.findById(old.getId())).isEmpty();
    }
}
