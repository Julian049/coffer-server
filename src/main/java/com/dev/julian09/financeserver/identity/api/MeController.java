package com.dev.julian09.financeserver.identity.api;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import com.dev.julian09.financeserver.identity.dto.UserDto;
import com.dev.julian09.financeserver.identity.service.AuthService;
import com.dev.julian09.financeserver.identity.dto.DeleteAccountRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final AuthService auth;

    public MeController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/api/v1/me")
    public UserDto me(CurrentUser user) {
        return auth.me(user.id());
    }

    @DeleteMapping("/api/v1/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest request, CurrentUser user, HttpServletRequest http) {
        auth.deleteAccount(request, user.id(), http.getRemoteAddr());
    }
}
