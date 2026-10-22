package com.dev.julian09.financeserver.identity.service;

import java.util.UUID;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import com.dev.julian09.financeserver.identity.domain.User;
import com.dev.julian09.financeserver.identity.dto.AuthResponse;
import com.dev.julian09.financeserver.identity.dto.DeleteAccountRequest;
import com.dev.julian09.financeserver.identity.dto.GoogleLoginRequest;
import com.dev.julian09.financeserver.identity.dto.LoginRequest;
import com.dev.julian09.financeserver.identity.dto.RefreshRequest;
import com.dev.julian09.financeserver.identity.dto.RegisterRequest;
import com.dev.julian09.financeserver.identity.dto.UserDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Register, login, refresh and logout (SEQ-01, SEQ-03). */
@Service
public class AuthService {

    private final UserService users;
    private final TokenService tokens;
    private final LoginRateLimiter rateLimiter;
    private final GoogleTokenVerifier google;

    public AuthService(UserService users, TokenService tokens, LoginRateLimiter rateLimiter, GoogleTokenVerifier google) {
        this.users = users;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.google = google;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = users.create(request);
        return response(user, tokens.issue(user.getId(), request.deviceId()));
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        rateLimiter.check(clientIp, request.identifier());
        // Same message whether the user exists or not (RNF-07, SEQ-01)
        User user = users.authenticate(request.identifier(), request.password())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Usuario o contraseña incorrectos."));
        return response(user, tokens.issue(user.getId(), request.deviceId()));
    }

    /** REQ-AUT-03: signs in, links by email or signs up with a verified Google ID token. */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleIdentity identity = google.verify(request.idToken())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "No pudimos validar tu cuenta de Google."));
        User user = users.findOrCreateByGoogle(identity);
        return response(user, tokens.issue(user.getId(), request.deviceId()));
    }

    public AuthResponse refresh(RefreshRequest request) {
        TokenService.Tokens rotated = tokens.rotate(request.refreshToken(), request.deviceId());
        return response(users.get(rotated.userId()), rotated);
    }

    public void logout(RefreshRequest request, UUID userId) {
        tokens.revoke(request.refreshToken(), userId);
    }

    /** S14-H01: deletes the account and everything it owns after the user proves who they are again. */
    @Transactional
    public void deleteAccount(DeleteAccountRequest request, UUID userId, String clientIp) {
        if (request.password() == null && request.idToken() == null) {
            throw new ApiException(ErrorCode.VALIDATION, "Confirma con tu contraseña o con tu cuenta de Google.");
        }
        // A stolen access token must not be able to guess the password
        rateLimiter.check(clientIp, "delete:" + userId);
        User user = users.get(userId);
        boolean confirmed = request.password() != null && users.passwordMatches(user, request.password())
                || request.idToken() != null && user.getGoogleSubject() != null
                && google.verify(request.idToken()).filter(i -> i.subject().equals(user.getGoogleSubject())).isPresent();
        if (!confirmed) {
            throw new ApiException(ErrorCode.CONFIRMATION, "No pudimos confirmar tu identidad.");
        }
        users.delete(userId);
    }

    public UserDto me(UUID userId) {
        return UserDto.of(users.get(userId));
    }

    private static AuthResponse response(User user, TokenService.Tokens issued) {
        return new AuthResponse(issued.accessToken(), issued.accessTokenExpiresAt(), issued.refreshToken(), UserDto.of(user));
    }
}
