package com.dev.julian09.financeserver.identity.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.dev.julian09.financeserver.identity.domain.User;
import com.dev.julian09.financeserver.identity.dto.PasswordResetConfirmRequest;
import com.dev.julian09.financeserver.identity.repo.PasswordResetCodeRepository;
import com.dev.julian09.financeserver.identity.repo.RefreshTokenRepository;
import com.dev.julian09.financeserver.identity.repo.UserRepository;
import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REQ-AUT-09 and REQ-AUT-10: a 6-digit code by email, valid 15 minutes, 5 attempts, same answer for every email. */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final LoginRateLimiter rateLimiter;
    private final ResetCodeMailer mailer;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes, RefreshTokenRepository refreshTokens,
                                PasswordEncoder passwords, LoginRateLimiter rateLimiter, ResetCodeMailer mailer, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.rateLimiter = rateLimiter;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Always silent: an unknown email, the hourly cap and a sent code look the same to the caller. */
    @Transactional
    public void request(String email, String clientIp) {
        rateLimiter.check(clientIp, "reset:" + email);
        Instant now = clock.instant();
        Optional<User> user = users.findByEmail(email.trim());
        if (user.isEmpty() || codes.countSince(user.get().getId(), now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            return;
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.replace(user.get().getId(), passwords.encode(code), now, now.plus(TTL));
        mailer.send(user.get().getEmail(), user.get().getDisplayName(), code);
    }

    /** Sets the new password and signs out every device (REQ-AUT-09). */
    @Transactional(noRollbackFor = ApiException.class)
    public void confirm(PasswordResetConfirmRequest request, String clientIp) {
        rateLimiter.check(clientIp, "reset-confirm:" + request.email());
        Instant now = clock.instant();
        User user = users.findByEmail(request.email().trim()).orElseThrow(PasswordResetService::invalid);
        PasswordResetCodeRepository.Code code = codes.lockActive(user.getId()).orElseThrow(PasswordResetService::invalid);
        if (!code.expiresAt().isAfter(now) || code.attempts() >= MAX_ATTEMPTS) {
            throw invalid();
        }
        if (!passwords.matches(request.code(), code.codeHash())) {
            // Committed even though the request fails (noRollbackFor), so a wrong guess always counts
            codes.countAttempt(code.id());
            throw invalid();
        }
        codes.markUsed(code.id(), now);
        user.changePassword(passwords.encode(request.newPassword()), now);
        refreshTokens.revokeAllOfUser(user.getId(), now);
    }

    // Used or expired codes are worthless after a day
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purge() {
        codes.deleteCreatedBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION, "El código no es válido o ya venció. Pide uno nuevo.");
    }
}
