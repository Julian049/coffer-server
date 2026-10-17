package com.dev.julian09.financeserver.sync.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import com.dev.julian09.financeserver.sync.dto.ChangeResult;
import com.dev.julian09.financeserver.sync.dto.EntityData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import com.dev.julian09.financeserver.sync.dto.PushResponse;
import com.dev.julian09.financeserver.sync.handler.SyncHandler;
import com.dev.julian09.financeserver.sync.handler.SyncHandler.Rejection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/sync/push (SEQ-08). One transaction per batch; every change gets its own result and one bad change never
 * sinks the batch: references are checked before writing (deferred FKs would only fail at commit) and each write runs
 * under a savepoint, so a constraint violation rolls back only that change.
 */
@Service
public class PushService {

    static final int MAX_BATCH = 200;

    private record Prepared<D extends EntityData>(int index, IncomingChange change, SyncHandler<D> handler, D data) {

        int order() {
            return handler.order(data);
        }
    }

    private final SyncEntityRegistry registry;
    private final JsonMapper json;
    private final Validator validator;
    private final JdbcTemplate jdbc;

    public PushService(SyncEntityRegistry registry, JsonMapper json, Validator validator, JdbcTemplate jdbc) {
        this.registry = registry;
        this.json = json;
        this.validator = validator;
        this.jdbc = jdbc;
    }

    @Transactional
    public PushResponse push(UUID userId, List<IncomingChange> changes) {
        if (changes.size() > MAX_BATCH) {
            throw new ApiException(ErrorCode.BATCH_TOO_LARGE, "Envía como máximo " + MAX_BATCH + " cambios por lote.");
        }
        ChangeResult[] results = new ChangeResult[changes.size()];
        List<Prepared<?>> ready = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            IncomingChange change = changes.get(i);
            Optional<SyncHandler<?>> handler = registry.handlerFor(change.entity());
            if (handler.isEmpty()) {
                results[i] = ChangeResult.rejected(change.id(), "FIN-VALIDATION", "Este tipo de dato todavía no se sincroniza");
                continue;
            }
            Object prepared = prepare(i, change, handler.get());
            if (prepared instanceof ChangeResult rejected) {
                results[i] = rejected;
            } else {
                ready.add((Prepared<?>) prepared);
            }
        }
        // What others reference is written first; the sort is stable, so equal kinds keep the device's order
        ready.sort(Comparator.comparingInt(Prepared::order));
        for (Prepared<?> p : ready) {
            results[p.index()] = apply(userId, p);
        }
        return new PushResponse(List.of(results));
    }

    private <D extends EntityData> Object prepare(int index, IncomingChange change, SyncHandler<D> handler) {
        D data;
        try {
            data = json.treeToValue(change.data(), handler.dataType());
        } catch (JacksonException | IllegalArgumentException e) {
            return ChangeResult.rejected(change.id(), "FIN-VALIDATION", "Datos inválidos para " + change.entity());
        }
        Optional<ConstraintViolation<D>> violation = validator.validate(data).stream().findFirst();
        if (violation.isPresent()) {
            ConstraintViolation<D> v = violation.get();
            return ChangeResult.rejected(change.id(), "FIN-VALIDATION", v.getPropertyPath() + ": " + v.getMessage());
        }
        return new Prepared<>(index, change, handler, data);
    }

    private <D extends EntityData> ChangeResult apply(UUID userId, Prepared<D> p) {
        UUID id = p.change().id();
        Optional<Rejection> rejection = p.handler().check(userId, p.change(), p.data());
        if (rejection.isPresent()) {
            return ChangeResult.rejected(id, rejection.get().code(), rejection.get().detail());
        }
        Savepoint savepoint = jdbc.execute((ConnectionCallback<Savepoint>) Connection::setSavepoint);
        try {
            Optional<Long> version = p.handler().upsert(userId, p.change(), p.data());
            jdbc.execute((ConnectionCallback<Void>) con -> {
                con.releaseSavepoint(savepoint);
                return null;
            });
            if (version.isPresent()) {
                return ChangeResult.applied(id, version.get());
            }
            // The WHERE of the upsert did not hold: our row is as new or newer, or the id belongs to someone else
            return p.handler().owner(id).filter(userId::equals).isPresent()
                    ? ChangeResult.stale(id)
                    : ChangeResult.rejected(id, "FIN-NOT-OWNER", "Este registro pertenece a otra cuenta");
        } catch (DataIntegrityViolationException e) {
            jdbc.execute((ConnectionCallback<Void>) con -> {
                con.rollback(savepoint);
                return null;
            });
            return ChangeResult.rejected(id, "FIN-VALIDATION", describe(e));
        }
    }

    private static String describe(DataAccessException e) {
        String state = e.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : "";
        return "23505".equals(state) ? "Ya existe un registro con ese nombre" : "Datos inválidos";
    }
}
