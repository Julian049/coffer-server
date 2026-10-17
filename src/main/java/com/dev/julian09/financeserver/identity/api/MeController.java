package com.dev.julian09.financeserver.identity.api;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import com.dev.julian09.financeserver.identity.dto.UserDto;
import com.dev.julian09.financeserver.identity.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
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
}
