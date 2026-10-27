package com.dev.julian09.financeserver.identity.dto;

import java.util.UUID;

import com.dev.julian09.financeserver.identity.domain.User;

/** [forwardingAddress] and [forwardingConfirmation] feed Conectar correo (REQ-COR-01, 2.0). */
public record UserDto(UUID id, String username, String displayName, String email, String currency, boolean googleLinked,
                      String forwardingAddress, String forwardingConfirmation) {

    /** [inbox] is Coffer's Gmail: cofferapp1@gmail.com gives cofferapp1+<code>@gmail.com. */
    public static UserDto of(User user, String inbox) {
        int at = inbox.indexOf('@');
        String address = at < 0 ? null : inbox.substring(0, at) + "+" + user.getMailCode() + inbox.substring(at);
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getCurrency(),
                user.getGoogleSubject() != null, address, user.getForwardingConfirmation());
    }
}
