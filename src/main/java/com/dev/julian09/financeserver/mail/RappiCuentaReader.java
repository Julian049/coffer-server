package com.dev.julian09.financeserver.mail;

import static com.dev.julian09.financeserver.mail.ReceiptParsers.BOGOTA;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.MONEY;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.amount;
import static com.dev.julian09.financeserver.mail.ReceiptParsers.clean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Money received in RappiCuenta (noreply@rappipay.co): «Monto recibido $1.000.000 Banco Bancolombia Nro. de transacción 3876759
 * Fecha de la transacción 29 de octubre de 2026 Hora de la transacción 10:36 am». It brings no digits, so only a mail rule gives
 * it an account (2.3-02). Before {@link RappiCardReader}, whose emails look alike.
 */
@Component
@Order(2)
public class RappiCuentaReader implements ReceiptReader {

    static final String SOURCE = "RAPPICUENTA";
    private static final Pattern AMOUNT = Pattern.compile("Monto recibido\\s+" + MONEY);
    private static final Pattern BANK = Pattern.compile("Banco\\s+(.+?)\\s+Nro\\.");
    private static final Pattern DATE = Pattern.compile(
            "Fecha de la transacci[óo]n\\s+(\\d{1,2}) de ([a-záéíóú]+) de (\\d{4})\\s+Hora de la transacci[óo]n\\s+(\\d{1,2}:\\d{2})\\s*([ap])\\.?\\s?m",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> MONTHS = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre");

    @Override
    public Optional<ParsedReceipt> read(String text) {
        String flat = text.replaceAll("\\s+", " ");
        Matcher amount = AMOUNT.matcher(flat);
        Matcher date = DATE.matcher(flat);
        int month = date.find() ? MONTHS.indexOf(date.group(2).toLowerCase()) : -1;
        if (!flat.contains("Recibiste una transferencia") || !amount.find() || month < 0) {
            return Optional.empty();
        }
        String[] hm = date.group(4).split(":");
        int hour = Integer.parseInt(hm[0]) % 12 + (date.group(5).equalsIgnoreCase("p") ? 12 : 0);
        Matcher bank = BANK.matcher(flat);
        return Optional.of(new ParsedReceipt(SOURCE, "INCOME", amount(amount.group(1)),
                LocalDateTime.of(Integer.parseInt(date.group(3)), month + 1, Integer.parseInt(date.group(1)), hour, Integer.parseInt(hm[1]))
                        .atZone(BOGOTA).toInstant(),
                bank.find() ? clean(bank.group(1)) : null, null));
    }
}
