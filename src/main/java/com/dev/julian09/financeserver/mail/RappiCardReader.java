package com.dev.julian09.financeserver.mail;

import static com.dev.julian09.financeserver.mail.ReceiptParsers.BOGOTA;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.MONEY;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.amount;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.clean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A RappiCard purchase (noreply@rappicard.co), which writes each label and its value on separate lines. */
@Component
@Order(3)
public class RappiCardReader implements ReceiptReader {

    static final String SOURCE = "RAPPICARD";
    private static final Pattern AMOUNT = Pattern.compile("Monto\\s+" + MONEY);
    private static final Pattern CARD = Pattern.compile("M[ée]todo de pago\\s+\\*?(\\d{4})");
    private static final Pattern MERCHANT = Pattern.compile("Comercio\\s+([^\\n]+)");
    private static final Pattern DATE = Pattern.compile("Fecha de la transacci[óo]n\\s+(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?::\\d{2})?)");

    @Override
    public Optional<ParsedReceipt> read(String text) {
        Matcher amount = AMOUNT.matcher(text);
        Matcher date = DATE.matcher(text);
        if (!text.contains("No. de autorizaci") || !amount.find() || !date.find()) {
            return Optional.empty();
        }
        Matcher card = CARD.matcher(text);
        Matcher merchant = MERCHANT.matcher(text);
        String time = date.group(1).length() == 16 ? date.group(1) + ":00" : date.group(1);
        return Optional.of(new ParsedReceipt(SOURCE, "EXPENSE", amount(amount.group(1)),
                LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(BOGOTA).toInstant(),
                merchant.find() ? clean(merchant.group(1)) : null, card.find() ? card.group(1) : null));
    }
}
