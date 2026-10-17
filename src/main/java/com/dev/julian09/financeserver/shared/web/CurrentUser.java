package com.dev.julian09.financeserver.shared.web;

import java.util.UUID;

/**
 * The authenticated user, taken from the JWT `sub`. Declare it as a controller parameter;
 * every query filters by this id (RNF-17).
 */
public record CurrentUser(UUID id) {
}
