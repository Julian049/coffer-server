package com.dev.julian09.financeserver.mail;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * The {@link ReceiptReader}s, one per provider, written from the PO's real emails; no AI (ADR-0014), plus what they share. A text
 * no reader knows is empty: the message stays FAILED with its body for a week, so a new reader can be written from it.
 */
@Component
public class ReceiptParsers {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final String MONEY = "\\$\\s?([0-9][0-9.,]*)";

    private final List<ReceiptReader> readers;

    public ReceiptParsers(List<ReceiptReader> readers) {
        this.readers = readers;
    }

    /** The first reader that understands [body]; Spring hands the readers over in their {@code @Order}. */
    public Optional<ParsedReceipt> parse(String body) {
        String text = body.replace('\u00a0', ' ').replace("\r", "");
        return readers.stream().map(r -> r.read(text)).flatMap(Optional::stream).findFirst();
    }

    /**
     * «$146,350.29», «$3,000.00», «$22.980», «$50.000,00»: the last separator is decimal only when two digits follow it.
     */
    static BigDecimal amount(String raw) {
        String s = raw.replaceAll("[.,]+$", "");
        int last = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
        boolean decimals = last >= 0 && s.length() - last - 1 == 2;
        String whole = decimals ? s.substring(0, last) : s;
        String cents = decimals ? s.substring(last + 1) : "0";
        return new BigDecimal(whole.replaceAll("[.,]", "") + "." + cents).setScale(4);
    }

    // «26/10/2026 21:33:20» or «26/10/26 12:53», Colombian time
    static Instant dayFirst(String date, String time) {
        String year = date.substring(date.lastIndexOf('/') + 1).length() == 2 ? "yy" : "yyyy";
        return LocalDateTime.parse(date + " " + time, DateTimeFormatter.ofPattern("d/M/" + year + " H:mm[:ss]")).atZone(BOGOTA).toInstant();
    }

    static String clean(String value) {
        String v = value.trim();
        return v.isEmpty() ? null : v.length() > 200 ? v.substring(0, 200) : v;
    }

    /** Gmail's forwarding confirmation, «(#123456789) Gmail Forwarding Confirmation…» or its Spanish version. */
    static Optional<String> forwardingCode(String from, String subject) {
        if (from == null || !from.contains("forwarding-noreply@google.com") || subject == null) {
            return Optional.empty();
        }
        Matcher m = Pattern.compile("\\((?:#|n\\.?\\s?º\\s?)\\s*(\\d{6,12})\\)").matcher(subject);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The user's code from cofferapp1+<code>@gmail.com among the recipient headers. */
    static Optional<String> userCode(List<String> recipients) {
        Pattern plus = Pattern.compile("\\+([a-z0-9]{4,12})@", Pattern.CASE_INSENSITIVE);
        return recipients.stream().map(plus::matcher).filter(Matcher::find).map(r -> r.group(1).toLowerCase()).findFirst();
    }
}
