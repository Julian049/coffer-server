package com.dev.julian09.financeserver.mail;

import java.util.Optional;

/**
 * Reads the emails of one provider (ADR-0014, roadmap-v2.3 2.3-01). Each one is a {@code @Component}, so a new provider is a new
 * class and its test; {@code @Order} only matters between readers whose texts look alike.
 */
public interface ReceiptReader {

    /**
     * The receipt in [text] (the body with no-break spaces as spaces and no carriage returns), or empty when it is not this
     * provider's. Readers decide by the body, because a forwarded email may keep the bank or the user as sender.
     */
    Optional<ParsedReceipt> read(String text);
}
