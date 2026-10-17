package com.dev.julian09.financeserver.identity.dto;

import java.util.UUID;

import com.dev.julian09.financeserver.identity.domain.User;

public record UserDto(UUID id, String username, String displayName, String email, String currency, boolean googleLinked) {

    public static UserDto of(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getCurrency(),
                user.getGoogleSubject() != null);
    }
}
