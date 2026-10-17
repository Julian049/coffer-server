package com.dev.julian09.financeserver.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import com.dev.julian09.financeserver.identity.domain.RefreshToken;
import com.dev.julian09.financeserver.identity.repo.RefreshTokenRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access tokens: RS256 JWT, 15 min, sub = userId. Refresh tokens: 256 random bits, stored as SHA-256,
 * rotated on every use with a 180-day sliding expiry; reusing a revoked one revokes the whole family (ADR-0007).
 */
@Service
public class TokenService {

    public record Tokens(UUID userId, String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;
    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public TokenService(RefreshTokenRepository refreshTokens, JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    /** New session: a new refresh family. */
    @Transactional
    public Tokens issue(UUID userId, String deviceId) {
        return mint(userId, UUID.randomUUID(), deviceId, clock.instant()).tokens();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Tokens rotate(String refreshToken, String deviceId) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(sha256(refreshToken)).orElseThrow(TokenService::revoked);
        if (!current.isUsable(now) || !current.getDeviceId().equals(deviceId)) {
            // A revoked token coming back means it leaked: nobody in that family is trusted any more
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw revoked();
        }
        Minted next = mint(current.getUserId(), current.getFamilyId(), deviceId, now);
        current.revoke(now, next.id());
        return next.tokens();
    }

    /** Logout: idempotent, and only the owner can revoke its own token. */
    @Transactional
    public void revoke(String refreshToken, UUID userId) {
        Instant now = clock.instant();
        refreshTokens.findByTokenHash(sha256(refreshToken))
                .filter(t -> t.getUserId().equals(userId) && t.getRevokedAt() == null)
                .ifPresent(t -> t.revoke(now, null));
    }

    private record Minted(UUID id, Tokens tokens) {
    }

    private Minted mint(UUID userId, UUID familyId, String deviceId, Instant now) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        RefreshToken row = refreshTokens.save(
                new RefreshToken(userId, familyId, sha256(refresh), deviceId, now, now.plus(props.refreshTtl())));

        Instant expiresAt = now.plus(props.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("coffer-server")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        return new Minted(row.getId(), new Tokens(userId, access, expiresAt, refresh));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static ApiException revoked() {
        return new ApiException(ErrorCode.AUTH_REVOKED, "Tu sesión terminó. Inicia sesión de nuevo.");
    }
}
