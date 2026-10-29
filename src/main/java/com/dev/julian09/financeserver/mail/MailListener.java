package com.dev.julian09.financeserver.mail;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Keeps an IMAP IDLE connection to Coffer's Gmail (ADR-0014): on connecting and on every wake-up it reads what is in the
 * inbox, hands each email to {@link InboundMessageService} and deletes it. Without MAIL_IMAP_USER and MAIL_IMAP_PASSWORD it
 * never starts, and a failure only retries with backoff: the rest of the server never depends on it.
 */
@Component
public class MailListener {

    private static final Logger log = LoggerFactory.getLogger(MailListener.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final InboundMessageService inbound;
    private final String user;
    private final String password;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(5);

    private volatile boolean running = true;
    private volatile boolean connected;

    public MailListener(InboundMessageService inbound, @Value("${finance.mail.imap-user:}") String user,
                        @Value("${finance.mail.imap-password:}") String password) {
        this.inbound = inbound;
        this.user = user;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (user.isBlank() || password.isBlank()) {
            log.info("Mail reading is off: MAIL_IMAP_USER or MAIL_IMAP_PASSWORD is not set");
            return;
        }
        // A platform thread: IMAP IDLE blocks inside synchronized code, which pins a virtual thread's carrier on Java 21.
        // With one CPU (Render's free plan) that was the only carrier, so every request hung and the health check failed
        Thread.ofPlatform().daemon().name("mail-listener").start(this::loop);
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        Duration backoff = INITIAL_BACKOFF;
        while (running) {
            connected = false;
            try {
                listen();
                backoff = INITIAL_BACKOFF;
            } catch (MessagingException | RuntimeException e) {
                // A connection that worked and was then dropped by Gmail (about every 40 min) retries soon; only failing to connect backs off
                if (connected) {
                    backoff = INITIAL_BACKOFF;
                }
                log.warn("Mail connection lost, retrying in {} s: {}", backoff.toSeconds(), e.getMessage());
                sleep(backoff);
                backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
            }
        }
    }

    private void listen() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.connectiontimeout", "30000");
        props.put("mail.imaps.timeout", "60000");
        Store store = Session.getInstance(props).getStore("imaps");
        store.connect("imap.gmail.com", 993, user, password);
        try (store; IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX")) {
            inbox.open(Folder.READ_WRITE);
            connected = true;
            log.info("Mail reading connected");
            while (running && store.isConnected()) {
                drain(inbox);
                // Returns on new mail or when Gmail ends the IDLE (about every 29 minutes); then the inbox is read again
                inbox.idle(true);
            }
        }
    }

    private void drain(IMAPFolder inbox) throws MessagingException {
        Message[] messages = inbox.getMessages();
        for (Message message : messages) {
            try {
                String[] ids = message.getHeader("Message-ID");
                String id = ids != null && ids.length > 0 ? ids[0] : inbox.getFullName() + ":" + inbox.getUID(message);
                InboundMessageService.Outcome outcome =
                        inbound.receive(id, recipients(message), from(message), message.getSubject(), text(message));
                log.info("Inbound email {}", outcome);
            } catch (IOException | RuntimeException e) {
                // A bad email must not stop the others; it stays in the inbox and comes back with the next drain
                log.warn("Could not read an email: {}", e.getMessage());
                continue;
            }
            message.setFlag(Flags.Flag.DELETED, true);
        }
        inbox.expunge();
    }

    // Gmail keeps the original To of a forwarded email; the +code is in Delivered-To
    private static List<String> recipients(Message message) throws MessagingException {
        List<String> all = new ArrayList<>();
        for (String header : new String[] {"Delivered-To", "X-Forwarded-To", "To"}) {
            String[] values = message.getHeader(header);
            if (values != null) {
                all.addAll(List.of(values));
            }
        }
        return all;
    }

    private static String from(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        return from == null || from.length == 0 ? null : from[0].toString();
    }

    /** text/plain when there is one; otherwise the HTML without its tags. */
    static String text(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return part.getContent().toString();
        }
        if (part.isMimeType("text/html")) {
            // Entities become letters («transacci&oacute;n»), or the readers' labels would not match (RappiCard in HTML)
            return HtmlUtils.htmlUnescape(part.getContent().toString()
                    .replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", " ")
                    .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</tr>|</td>", "\n")
                    .replaceAll("<[^>]+>", " "));
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String html = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String value = text(child);
                if (child.isMimeType("text/plain") && value != null && !value.isBlank()) {
                    return value;
                }
                if (value != null && html == null) {
                    html = value;
                }
            }
            return html;
        }
        return null;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
