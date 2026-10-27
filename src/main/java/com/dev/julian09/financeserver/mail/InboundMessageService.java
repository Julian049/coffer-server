package com.dev.julian09.financeserver.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One received email → inbound_messages → a transaction draft of the user of its +code (ADR-0014). */
@Service
public class InboundMessageService {

    public enum Outcome { DUPLICATE, IGNORED, PARSED, FAILED }

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InboundMessageService(JdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Outcome receive(String messageId, List<String> recipients, String from, String subject, String body) {
        OffsetDateTime now = utc(clock.instant());
        UUID id = UUID.randomUUID();
        // REQ-COR-03: the same Message-ID twice is one row
        int inserted = jdbc.update("""
                INSERT INTO inbound_messages (id, message_id, status, subject, body, received_at)
                VALUES (?, ?, 'RECEIVED', ?, ?, ?) ON CONFLICT (message_id) DO NOTHING""",
                id, messageId, cut(subject, 300), body, now);
        if (inserted == 0) {
            return Outcome.DUPLICATE;
        }
        Optional<String> code = ReceiptParsers.userCode(recipients);
        Optional<UUID> user = code.flatMap(c ->
                jdbc.queryForList("SELECT id FROM users WHERE mail_code = ?", UUID.class, c).stream().findFirst());
        if (user.isEmpty()) {
            return finish(id, user, Outcome.IGNORED,
                    code.isEmpty() ? "El destinatario no trae el +código" : "Ningún usuario tiene ese +código");
        }
        Optional<String> confirmation = ReceiptParsers.forwardingCode(from, subject);
        if (confirmation.isPresent()) {
            jdbc.update("UPDATE users SET forwarding_confirmation = ?, updated_at = ? WHERE id = ?", confirmation.get(), now, user.get());
            return finish(id, user, Outcome.IGNORED, "Confirmación de reenvío de Gmail");
        }
        Optional<ParsedReceipt> receipt = ReceiptParsers.parse(body == null ? "" : body);
        if (receipt.isEmpty()) {
            log.warn("No reader understood inbound message {}", id);
            return finish(id, user, Outcome.FAILED, "Ningún lector reconoce el correo");
        }
        ParsedReceipt r = receipt.get();
        jdbc.update("""
                INSERT INTO transaction_drafts (id, user_id, source, type, amount, occurred_at, merchant, last_digits, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), user.get(), r.source(), r.type(), r.amount(), utc(r.occurredAt()), r.merchant(), r.lastDigits(), now, now);
        events.publishEvent(new DraftCreatedEvent(user.get()));
        return finish(id, user, Outcome.PARSED, null);
    }

    // REQ-COR-09: only what was extracted stays; a failed body helps fixing the reader
    private Outcome finish(UUID id, Optional<UUID> user, Outcome outcome, String error) {
        jdbc.update("""
                UPDATE inbound_messages SET status = ?, user_id = ?, error = ?, processed_at = ?,
                    body = CASE WHEN ? = 'FAILED' THEN body END, attempts = attempts + 1 WHERE id = ?""",
                outcome.name(), user.orElse(null), error, utc(clock.instant()), outcome.name(), id);
        if (error != null) {
            // The reason is also in inbound_messages.error; in the log it explains an IGNORED or FAILED without Neon
            log.info("Inbound message {} {}: {}", id, outcome, error);
        }
        return outcome;
    }

    /** REQ-COR-09: failed bodies live 7 days; the rows (and their Message-ID guard) 90. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        jdbc.update("UPDATE inbound_messages SET body = NULL WHERE body IS NOT NULL AND received_at < ?", utc(now.minus(Duration.ofDays(7))));
        jdbc.update("DELETE FROM inbound_messages WHERE received_at < ?", utc(now.minus(Duration.ofDays(90))));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
