package com.dev.julian09.financeserver.identity.api;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import com.dev.julian09.financeserver.identity.dto.AuthResponse;
import com.dev.julian09.financeserver.identity.dto.GoogleLoginRequest;
import com.dev.julian09.financeserver.identity.dto.LoginRequest;
import com.dev.julian09.financeserver.identity.dto.PasswordResetConfirmRequest;
import com.dev.julian09.financeserver.identity.dto.PasswordResetRequest;
import com.dev.julian09.financeserver.identity.dto.RefreshRequest;
import com.dev.julian09.financeserver.identity.dto.RegisterRequest;
import com.dev.julian09.financeserver.identity.service.AuthService;
import com.dev.julian09.financeserver.identity.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final PasswordResetService reset;

    public AuthController(AuthService auth, PasswordResetService reset) {
        this.auth = auth;
        this.reset = reset;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Behind Render this is the client IP (server.forward-headers-strategy: native)
        return auth.login(request, http.getRemoteAddr());
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return auth.loginWithGoogle(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request);
    }

    /** REQ-AUT-10: 202 whether or not the email exists. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        reset.request(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest http) {
        reset.confirm(request, http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request, CurrentUser user) {
        auth.logout(request, user.id());
    }
}
