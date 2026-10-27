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

/**
 * Rules per bank, written from the PO's real emails (roadmap-v2.0 D3); no AI (ADR-0014). They read the body only,
 * because a forwarded email may keep the bank or the user as sender. A text no rule knows is empty: the message stays
 * FAILED with its body for a week, so a new rule can be written from it.
 */
public final class ReceiptParsers {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String MONEY = "\\$\\s?([0-9][0-9.,]*)";

    // «Pagaste $146,350.29 a Banco Caja Social S A Comerc desde tu producto 2309 el 26/10/2026 21:33:20»
    private static final Pattern BANCOLOMBIA_PAID = Pattern.compile(
            "Pagaste " + MONEY + " a (.+?) desde tu producto \\*?(\\d{4}) el (\\d{2}/\\d{2}/\\d{2,4}) (?:a las )?(\\d{1,2}:\\d{2}(?::\\d{2})?)");
    // «recibiste una transferencia de JORGE ... por $3,000.00 en tu cuenta *2309 conectada a la llave xx el 26/10/26 a las 12:53»
    private static final Pattern BANCOLOMBIA_RECEIVED = Pattern.compile(
            "recibiste una transferencia de (.+?) por " + MONEY + " en tu cuenta \\*?(\\d{4}).*? el (\\d{2}/\\d{2}/\\d{2,4}) "
                    + "(?:a las )?(\\d{1,2}:\\d{2}(?::\\d{2})?)", Pattern.DOTALL);
    private static final Pattern RAPPI_AMOUNT = Pattern.compile("Monto\\s+" + MONEY);
    private static final Pattern RAPPI_CARD = Pattern.compile("M[ée]todo de pago\\s+\\*?(\\d{4})");
    private static final Pattern RAPPI_MERCHANT = Pattern.compile("Comercio\\s+([^\\n]+)");
    private static final Pattern RAPPI_DATE = Pattern.compile("Fecha de la transacci[óo]n\\s+(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?::\\d{2})?)");

    private ReceiptParsers() {
    }

    public static Optional<ParsedReceipt> parse(String body) {
        String text = body.replace(' ', ' ').replace("\r", "");
        String flat = text.replaceAll("\\s+", " ");
        Matcher m = BANCOLOMBIA_PAID.matcher(flat);
        if (m.find()) {
            return Optional.of(new ParsedReceipt("BANCOLOMBIA", "EXPENSE", amount(m.group(1)), dayFirst(m.group(4), m.group(5)),
                    clean(m.group(2)), m.group(3)));
        }
        m = BANCOLOMBIA_RECEIVED.matcher(flat);
        if (m.find()) {
            return Optional.of(new ParsedReceipt("BANCOLOMBIA", "INCOME", amount(m.group(2)), dayFirst(m.group(4), m.group(5)),
                    clean(m.group(1)), m.group(3)));
        }
        return rappi(text);
    }

    // RappiCard writes each label and its value on separate lines
    private static Optional<ParsedReceipt> rappi(String text) {
        Matcher amount = RAPPI_AMOUNT.matcher(text);
        Matcher date = RAPPI_DATE.matcher(text);
        if (!text.contains("No. de autorizaci") || !amount.find() || !date.find()) {
            return Optional.empty();
        }
        Matcher card = RAPPI_CARD.matcher(text);
        Matcher merchant = RAPPI_MERCHANT.matcher(text);
        String time = date.group(1).length() == 16 ? date.group(1) + ":00" : date.group(1);
        return Optional.of(new ParsedReceipt("RAPPICARD", "EXPENSE", amount(amount.group(1)),
                LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(BOGOTA).toInstant(),
                merchant.find() ? clean(merchant.group(1)) : null, card.find() ? card.group(1) : null));
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
    private static Instant dayFirst(String date, String time) {
        String year = date.substring(date.lastIndexOf('/') + 1).length() == 2 ? "yy" : "yyyy";
        return LocalDateTime.parse(date + " " + time, DateTimeFormatter.ofPattern("d/M/" + year + " H:mm[:ss]")).atZone(BOGOTA).toInstant();
    }

    private static String clean(String value) {
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
