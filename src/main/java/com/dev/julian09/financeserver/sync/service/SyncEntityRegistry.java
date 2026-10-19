package com.dev.julian09.financeserver.sync.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.dev.julian09.financeserver.sync.handler.SyncHandler;
import org.springframework.stereotype.Component;

/** entity name → handler. Loan details (S10) have no handler yet. */
@Component
public class SyncEntityRegistry {

    private final Map<String, SyncHandler<?>> handlers;

    public SyncEntityRegistry(List<SyncHandler<?>> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(SyncHandler::entity, Function.identity()));
    }

    public Optional<SyncHandler<?>> handlerFor(String entity) {
        return Optional.ofNullable(handlers.get(entity));
    }
}
