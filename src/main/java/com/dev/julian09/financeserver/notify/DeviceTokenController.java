package com.dev.julian09.financeserver.notify;

import java.time.Clock;
import java.time.ZoneOffset;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADR-0016: each phone registers its FCM token after signing in and removes it when signing out. */
@RestController
public class DeviceTokenController {

    public record DeviceTokenRequest(@NotBlank(message = "Falta el token") @Size(max = 300, message = "Token no válido") String token) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DeviceTokenController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A token moves to whoever signed in last on that phone. */
    @PutMapping("/api/v1/me/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody DeviceTokenRequest request, CurrentUser user) {
        jdbc.update("""
                INSERT INTO device_tokens (token, user_id, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, updated_at = EXCLUDED.updated_at""",
                request.token(), user.id(), clock.instant().atOffset(ZoneOffset.UTC));
    }

    @DeleteMapping("/api/v1/me/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody DeviceTokenRequest request, CurrentUser user) {
        jdbc.update("DELETE FROM device_tokens WHERE token = ? AND user_id = ?", request.token(), user.id());
    }
}
