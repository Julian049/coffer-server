package com.dev.julian09.financeserver.identity.service;

import java.util.UUID;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import com.dev.julian09.financeserver.identity.domain.User;
import com.dev.julian09.financeserver.identity.dto.AuthResponse;
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

    public AuthService(UserService users, TokenService tokens, LoginRateLimiter rateLimiter) {
        this.users = users;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
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

    public AuthResponse refresh(RefreshRequest request) {
        TokenService.Tokens rotated = tokens.rotate(request.refreshToken(), request.deviceId());
        return response(users.get(rotated.userId()), rotated);
    }

    public void logout(RefreshRequest request, UUID userId) {
        tokens.revoke(request.refreshToken(), userId);
    }

    public UserDto me(UUID userId) {
        return UserDto.of(users.get(userId));
    }

    private static AuthResponse response(User user, TokenService.Tokens issued) {
        return new AuthResponse(issued.accessToken(), issued.accessTokenExpiresAt(), issued.refreshToken(), UserDto.of(user));
    }
}
