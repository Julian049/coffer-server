package com.dev.julian09.financeserver.identity.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import com.dev.julian09.financeserver.identity.domain.User;
import com.dev.julian09.financeserver.identity.dto.RegisterRequest;
import com.dev.julian09.financeserver.identity.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;
    // Compared against when the user does not exist, so both cases take the same BCrypt time
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
        this.dummyHash = passwords.encode("timing-equalizer");
    }

    @Transactional
    public User create(RegisterRequest request) {
        if (users.usernameTaken(request.username())) {
            throw new ApiException(ErrorCode.CONFLICT, "Ese nombre de usuario ya está registrado.");
        }
        if (users.emailTaken(request.email())) {
            throw new ApiException(ErrorCode.CONFLICT, "Ese correo ya está registrado.");
        }
        User user = new User(request.username(), request.email().trim(), request.displayName().trim(),
                passwords.encode(request.password()), clock.instant());
        return users.save(user);
    }

    /** Empty for an unknown user or a wrong password: callers must not tell them apart (SEQ-01). */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String identifier, String password) {
        Optional<User> user = users.findByIdentifier(identifier.trim());
        // Google-only users and the MVP "owner" ('!') have no BCrypt hash and can never sign in with a password
        boolean hasPassword = user.map(User::getPasswordHash).filter(h -> h.startsWith("$2")).isPresent();
        boolean matches = passwords.matches(password, hasPassword ? user.get().getPasswordHash() : dummyHash);
        return hasPassword && matches ? user : Optional.empty();
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Inicia sesión de nuevo."));
    }
}
