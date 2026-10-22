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

    /**
     * SEQ-01 §1.3: by Google subject; else the account with that (verified) email gets linked; else a new user with a
     * username taken from the email.
     */
    @Transactional
    public User findOrCreateByGoogle(GoogleTokenVerifier.GoogleIdentity google) {
        Optional<User> bySubject = users.findByGoogleSubject(google.subject());
        if (bySubject.isPresent()) {
            return bySubject.get();
        }
        Optional<User> byEmail = users.findByEmail(google.email().trim());
        if (byEmail.isPresent()) {
            byEmail.get().linkGoogle(google.subject(), clock.instant());
            return byEmail.get();
        }
        String name = google.name() == null || google.name().isBlank() ? google.email().split("@")[0] : google.name().trim();
        return users.save(User.forGoogle(freeUsername(google.email()), google.email().trim(),
                name.length() > 100 ? name.substring(0, 100) : name, google.subject(), clock.instant()));
    }

    /** The email's local part with the register rules (3–40 of a-z, 0-9, dot, underscore), numbered if taken. */
    private String freeUsername(String email) {
        String base = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9._]", "");
        if (base.length() < 3) {
            base = "usuario";
        }
        base = base.length() > 36 ? base.substring(0, 36) : base;
        String candidate = base;
        for (int n = 2; users.usernameTaken(candidate); n++) {
            candidate = base + n;
        }
        return candidate;
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

    /** False for Google-only users, who have no hash to compare. */
    public boolean passwordMatches(User user, String password) {
        String hash = user.getPasswordHash();
        return hash != null && hash.startsWith("$2") && passwords.matches(password, hash);
    }

    /** The database cascades the delete to every row the user owns (REQ-AUT-04, data-model §4). */
    @Transactional
    public void delete(UUID id) {
        users.deleteById(id);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Inicia sesión de nuevo."));
    }
}
