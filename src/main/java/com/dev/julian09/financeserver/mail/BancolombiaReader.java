package com.dev.julian09.financeserver.mail;

import static com.dev.julian09.financeserver.mail.ReceiptParsers.MONEY;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.amount;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.clean;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.dayFirst;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bancolombia sends every alert from one address with one subject, so the body tells the two providers apart: a purchase with
 * «T.Cred» is {@code BANCOLOMBIA_TC}, anything else the account, {@code BANCOLOMBIA} (roadmap-v2.3 §2).
 */
@Component
@Order(1)
public class BancolombiaReader implements ReceiptReader {

    static final String ACCOUNT = "BANCOLOMBIA";
    static final String CREDIT_CARD = "BANCOLOMBIA_TC";
    private static final String DATE_TIME = "(\\d{2}/\\d{2}/\\d{2,4}) (?:a las )?(\\d{1,2}:\\d{2}(?::\\d{2})?)";

    // «Pagaste $146,350.29 a Banco Caja Social S A Comerc desde tu producto 2309 el 26/10/2026 21:33:20»
    private static final Pattern PAID = Pattern.compile("Pagaste " + MONEY + " a (.+?) desde tu producto \\*?(\\d{4}) el " + DATE_TIME);
    // «recibiste una transferencia de JORGE ... por $3,000.00 en tu cuenta *2309 conectada a la llave xx el 26/10/26 a las 12:53»
    private static final Pattern RECEIVED = Pattern.compile(
            "recibiste una transferencia de (.+?) por " + MONEY + " en tu cuenta \\*?(\\d{4}).*? el " + DATE_TIME, Pattern.DOTALL);
    // «Compraste COP24.900,00 en ALMACENES EXITO INVE con tu T.Cred *6858, el 28/10/2026 a las 09:02»
    private static final Pattern BOUGHT = Pattern.compile(
            "Compraste (?:COP|\\$)\\s?([0-9][0-9.,]*) en (.+?) con tu ([^*]{1,20})\\*(\\d{4}),? el " + DATE_TIME);
    // «transferiste $1,000,000.00 la llave @rp123 desde tu cuenta *2309 Julian ... el 29/10/26 a las 10:36»
    private static final Pattern SENT = Pattern.compile(
            "[Tt]ransferiste " + MONEY + " (?:a la llave |la llave |a )?(.+?) desde tu cuenta \\*?(\\d{4})(?: (?:a )?(.+?))? el " + DATE_TIME);
    // «Transferiste $6,600.00 desde tu cuenta *2309 a la cuenta *3203104051 el 29/10/26 a las 11:50»
    private static final Pattern SENT_ACCOUNT = Pattern.compile(
            "[Tt]ransferiste " + MONEY + " desde tu cuenta \\*?(\\d{4}) a (?:la )?(cuenta \\*?\\d+) el " + DATE_TIME);
    // «Recibiste un pago de Nomina de ALMACENES EXITO por $425,264.00 en tu cuenta de Ahorros el 29/10/2026 a las 12:46» (no digits)
    private static final Pattern PAYMENT = Pattern.compile("Recibiste un pago de (.+?) por " + MONEY + " en tu cuenta de \\w+ el " + DATE_TIME);

    @Override
    public Optional<ParsedReceipt> read(String text) {
        String flat = text.replaceAll("\\s+", " ");
        Matcher m = PAID.matcher(flat);
        if (m.find()) {
            return expense(ACCOUNT, m.group(1), m.group(4), m.group(5), m.group(2), m.group(3));
        }
        m = BOUGHT.matcher(flat);
        if (m.find()) {
            String source = m.group(3).contains("T.Cred") ? CREDIT_CARD : ACCOUNT;
            return expense(source, m.group(1), m.group(5), m.group(6), m.group(2), m.group(4));
        }
        m = SENT.matcher(flat);
        if (m.find()) {
            // The name after the account is who got it; without it, the key it went to
            return expense(ACCOUNT, m.group(1), m.group(5), m.group(6), m.group(4) != null ? m.group(4) : m.group(2), m.group(3));
        }
        m = SENT_ACCOUNT.matcher(flat);
        if (m.find()) {
            return expense(ACCOUNT, m.group(1), m.group(4), m.group(5), m.group(3), m.group(2));
        }
        m = PAYMENT.matcher(flat);
        if (m.find()) {
            return Optional.of(new ParsedReceipt(ACCOUNT, "INCOME", amount(m.group(2)), dayFirst(m.group(3), m.group(4)), clean(m.group(1)), null));
        }
        m = RECEIVED.matcher(flat);
        if (m.find()) {
            return Optional.of(new ParsedReceipt(ACCOUNT, "INCOME", amount(m.group(2)), dayFirst(m.group(4), m.group(5)),
                    clean(m.group(1)), m.group(3)));
        }
        return Optional.empty();
    }

    private static Optional<ParsedReceipt> expense(String source, String money, String date, String time, String merchant, String digits) {
        return Optional.of(new ParsedReceipt(source, "EXPENSE", amount(money), dayFirst(date, time), clean(merchant), digits));
    }
}
