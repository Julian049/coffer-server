package com.dev.julian09.financeserver.identity.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** password_reset_codes (V15); plain JDBC, the rows never leave the identity module. */
@Repository
public class PasswordResetCodeRepository {

    public record Code(UUID id, String codeHash, Instant expiresAt, int attempts) {
    }

    private final JdbcTemplate jdbc;

    public PasswordResetCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countSince(UUID userId, Instant since) {
        return jdbc.queryForObject("SELECT count(*) FROM password_reset_codes WHERE user_id = ? AND created_at > ?",
                Integer.class, userId, utc(since));
    }

    /** A new code replaces the ones still waiting: only the last email works. */
    public void replace(UUID userId, String codeHash, Instant now, Instant expiresAt) {
        jdbc.update("UPDATE password_reset_codes SET used_at = ? WHERE user_id = ? AND used_at IS NULL", utc(now), userId);
        jdbc.update("INSERT INTO password_reset_codes (id, user_id, code_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, codeHash, utc(expiresAt), utc(now));
    }

    /** The waiting code, locked so two guesses cannot both pass the attempt limit. */
    public Optional<Code> lockActive(UUID userId) {
        return jdbc.query("""
                        SELECT id, code_hash, expires_at, attempts FROM password_reset_codes
                        WHERE user_id = ? AND used_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE""",
                (rs, i) -> new Code(rs.getObject("id", UUID.class), rs.getString("code_hash"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(), rs.getInt("attempts")),
                userId).stream().findFirst();
    }

    public void countAttempt(UUID id) {
        jdbc.update("UPDATE password_reset_codes SET attempts = attempts + 1 WHERE id = ?", id);
    }

    public void markUsed(UUID id, Instant now) {
        jdbc.update("UPDATE password_reset_codes SET used_at = ? WHERE id = ?", utc(now), id);
    }

    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM password_reset_codes WHERE created_at < ?", utc(cutoff));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
