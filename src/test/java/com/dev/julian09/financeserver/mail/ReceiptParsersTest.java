package com.dev.julian09.financeserver.mail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The PO's real email bodies (roadmap-v2.0 D3, 2026-10-26), with personal data kept as sent. */
class ReceiptParsersTest {

    @Test
    void aBancolombiaPaymentIsAnExpenseOfThatProduct() {
        ParsedReceipt r = ReceiptParsers.parse("""
                Bancolombia: Pagaste $146,350.29 a Banco Caja Social S A Comerc desde tu producto 2309 el 26/10/2026 21:33:20. \
                ¿Dudas? Llamanos al 6045109095. Estamos cerca
                """).orElseThrow();
        assertThat(r).isEqualTo(new ParsedReceipt("BANCOLOMBIA", "EXPENSE", new BigDecimal("146350.2900"),
                Instant.parse("2026-10-27T02:33:20Z"), "Banco Caja Social S A Comerc", "2309"));
    }

    @Test
    void aBancolombiaTransferReceivedIsAnIncomeFromTheSender() {
        ParsedReceipt r = ReceiptParsers.parse("""
                Bancolombia: Julian, recibiste una transferencia de JORGE ANDRES GONZALEZ DIAZ por $3,000.00 en tu cuenta *2309 \
                conectada a la llave xx  el 26/10/26 a las 12:53. Con llaves es de una y gratis. Dudas al 018000912345.
                """).orElseThrow();
        assertThat(r).isEqualTo(new ParsedReceipt("BANCOLOMBIA", "INCOME", new BigDecimal("3000.0000"),
                Instant.parse("2026-10-26T17:53:00Z"), "JORGE ANDRES GONZALEZ DIAZ", "2309"));
    }

    @Test
    void aBancolombiaCardPurchaseIsAnExpenseAtThatMerchant() {
        // As MailListener leaves the HTML-only email (2026-10-28), entities still in
        String direct = """
                ¡Listo! Todo sali&oacute; bien con tus movimientos Bancolombia: Compraste COP24.900,00 en ALMACENES EXITO INVE \
                con tu T.Cred *6858, el 28/10/2026 a las 09:02. Si tienes dudas, encuentranos aqui: 6045109095 o 018000931987.
                """;
        ParsedReceipt expected = new ParsedReceipt("BANCOLOMBIA", "EXPENSE", new BigDecimal("24900.0000"),
                Instant.parse("2026-10-28T14:02:00Z"), "ALMACENES EXITO INVE", "6858");
        assertThat(ReceiptParsers.parse(direct)).contains(expected);
        // The same email forwarded from Gmail
        assertThat(ReceiptParsers.parse("""
                [image: yellow-icon] ¡Listo! Todo salió bien con tus movimientos Bancolombia: Compraste COP24.900,00 en \
                ALMACENES EXITO INVE con tu T.Cred *6858, el 28/10/2026 a las 09:02. Si tienes dudas, encuentranos aqui: \
                6045109095 <(604)%20510-9095> o 018000931987.
                """)).contains(expected);
    }

    @Test
    void aRappiCardPurchaseReadsItsLabelledLines() {
        ParsedReceipt r = ReceiptParsers.parse("""
                ¡Hola, JULIAN DAVID BOCANEGRA SEGURA!
                Realizaste una compra con tu RappiCard.

                Detalle de tu transacción:

                Monto

                $22.980

                Método de pago

                *5378

                No. de autorización

                994394

                Comercio

                Tiendas D1

                Fecha de la transacción

                2026-10-26 19:16:04
                """).orElseThrow();
        assertThat(r).isEqualTo(new ParsedReceipt("RAPPICARD", "EXPENSE", new BigDecimal("22980.0000"),
                Instant.parse("2026-10-27T00:16:04Z"), "Tiendas D1", "5378"));
        // The PO's second example came without the greeting
        assertThat(ReceiptParsers.parse("Detalle de tu transacción:\n\nMonto\n\n$16.300\n\nMétodo de pago\n\n*5378\n\n"
                + "No. de autorización\n\n669292\n\nComercio\n\nRAPPI\n\nFecha de la transacción\n\n2026-10-24 21:18:36\n"))
                .get().extracting(ParsedReceipt::amount, ParsedReceipt::merchant)
                .containsExactly(new BigDecimal("16300.0000"), "RAPPI");
    }

    @Test
    void amountsReadBothSeparatorStyles() {
        assertThat(ReceiptParsers.amount("146,350.29")).isEqualByComparingTo("146350.29");
        assertThat(ReceiptParsers.amount("22.980")).isEqualByComparingTo("22980");
        assertThat(ReceiptParsers.amount("50.000,00")).isEqualByComparingTo("50000");
        assertThat(ReceiptParsers.amount("1.250.000")).isEqualByComparingTo("1250000");
        assertThat(ReceiptParsers.amount("3,000.00.")).isEqualByComparingTo("3000");
    }

    @Test
    void anUnknownTextIsNotGuessed() {
        assertThat(ReceiptParsers.parse("Hola, tu extracto de octubre ya está disponible")).isEmpty();
    }

    @Test
    void theUserCodeAndGmailsConfirmationAreRead() {
        assertThat(ReceiptParsers.userCode(List.of("julian@gmail.com", "cofferapp1+ab12cd34@gmail.com"))).contains("ab12cd34");
        assertThat(ReceiptParsers.forwardingCode("Gmail Team <forwarding-noreply@google.com>",
                "(#123456789) Gmail Forwarding Confirmation - Receive Mail from julian@gmail.com")).contains("123456789");
        assertThat(ReceiptParsers.forwardingCode("alguien@gmail.com", "(#123456789) algo")).isEmpty();
    }
}
