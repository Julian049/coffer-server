package com.dev.julian09.financeserver.notify;

import java.util.List;

import com.dev.julian09.financeserver.mail.DraftCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * REQ-COR-10: after a draft is committed, a data message to every phone of the user, unless Ajustes turned it off.
 * It runs on its own virtual thread, so a slow FCM never delays the mail reading.
 */
@Component
public class DraftNotifier {

    private static final Logger log = LoggerFactory.getLogger(DraftNotifier.class);

    private final JdbcTemplate jdbc;
    private final FcmSender fcm;

    public DraftNotifier(JdbcTemplate jdbc, FcmSender fcm) {
        this.jdbc = jdbc;
        this.fcm = fcm;
    }

    @TransactionalEventListener
    public void onDraft(DraftCreatedEvent event) {
        if (!fcm.enabled()) {
            return;
        }
        Boolean wanted = jdbc.queryForList("SELECT notify_drafts FROM user_settings WHERE user_id = ?", Boolean.class, event.userId())
                .stream().findFirst().orElse(true);
        if (!wanted) {
            return;
        }
        List<String> tokens = jdbc.queryForList("SELECT token FROM device_tokens WHERE user_id = ?", String.class, event.userId());
        Thread.ofVirtual().name("fcm").start(() -> tokens.forEach(token -> {
            FcmSender.Result result = fcm.send(token);
            if (result == FcmSender.Result.GONE) {
                jdbc.update("DELETE FROM device_tokens WHERE token = ?", token);
            } else if (result == FcmSender.Result.FAILED) {
                log.warn("FCM did not take a drafts notice");
            }
        }));
    }
}
