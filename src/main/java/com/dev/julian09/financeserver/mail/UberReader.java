package com.dev.julian09.financeserver.mail;

import static com.dev.julian09.financeserver.mail.ReceiptParsers.BOGOTA;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.amount;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * An Uber trip receipt (noreply@uber.com), in English: «Total COP 4,917 … Mastercard ••••9533 (Nu CC) COP 4,917 11/2/26 10:24 AM».
 * The time is the payment's, month first and 12-hour; the header's is when the trip was requested. Only the first payment
 * method is read (roadmap-v2.3 §4; split payments, tips and Uber Eats wait for real receipts, B-06).
 */
@Component
public class UberReader implements ReceiptReader {

    static final String SOURCE = "UBER";
    // Uber writes a narrow no-break space (U+202F) before AM/PM
    private static final String SPACE = "[\\s\\u202F]";
    private static final Pattern TOTAL = Pattern.compile("Total" + SPACE + "+COP");
    private static final Pattern PAYMENT = Pattern.compile("[•*]{4}" + SPACE + "?(\\d{4})(?:" + SPACE + "*\\([^)]*\\))?" + SPACE
            + "*COP" + SPACE + "?([0-9][0-9.,]*)" + SPACE + "+(\\d{1,2})/(\\d{1,2})/(\\d{2})" + SPACE + "+(\\d{1,2}):(\\d{2})" + SPACE
            + "*([AP])M");

    @Override
    public Optional<ParsedReceipt> read(String text) {
        Matcher m = PAYMENT.matcher(text);
        // HTML-only receipts put «Total» and «COP» in separate cells, with a line break between them (#134)
        if (!TOTAL.matcher(text).find() || !m.find()) {
            return Optional.empty();
        }
        int hour = Integer.parseInt(m.group(6)) % 12 + (m.group(8).equals("P") ? 12 : 0);
        LocalDateTime paid = LocalDateTime.of(2000 + Integer.parseInt(m.group(5)), Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), hour, Integer.parseInt(m.group(7)));
        return Optional.of(new ParsedReceipt(SOURCE, "EXPENSE", amount(m.group(2)), paid.atZone(BOGOTA).toInstant(), "Uber", m.group(1)));
    }
}
