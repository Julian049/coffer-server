package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.ledger.validation.LedgerValidators;
import com.dev.julian09.financeserver.sync.dto.EntityData.CategoryData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Categories have one level of subcategories (REQ-CAT-02); parents are written before their children. */
@Component
public class CategorySyncHandler extends SyncHandler<CategoryData> {

    private static final String UPSERT = """
            INSERT INTO categories AS t (id, user_id, parent_id, name, kind, is_base, system_key, icon, sort_order,
                created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :parentId, :name, :kind, :isBase, :systemKey, :icon, :sortOrder, :createdAt, :updatedAt,
                :deletedAt)
            ON CONFLICT (id) DO UPDATE SET parent_id = EXCLUDED.parent_id, name = EXCLUDED.name, kind = EXCLUDED.kind,
                is_base = EXCLUDED.is_base, system_key = EXCLUDED.system_key, icon = EXCLUDED.icon,
                sort_order = EXCLUDED.sort_order, updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    private final LedgerValidators ledger;

    public CategorySyncHandler(NamedParameterJdbcTemplate jdbc, LedgerValidators ledger) {
        super(jdbc, CategoryData.class);
        this.ledger = ledger;
    }

    @Override
    public String entity() {
        return "category";
    }

    @Override
    protected String table() {
        return "categories";
    }

    @Override
    public int order(CategoryData data) {
        return data.parentId() == null ? 2 : 3;
    }

    @Override
    public Optional<Rejection> check(UUID userId, IncomingChange change, CategoryData d) {
        if (d.parentId() == null) {
            return Optional.empty();
        }
        if (d.parentId().equals(change.id())) {
            return Optional.of(Rejection.invalid("Una categoría no puede ser su propia subcategoría"));
        }
        var parent = ledger.category(userId, d.parentId());
        if (parent.isEmpty()) {
            return Optional.of(Rejection.reference("La categoría principal no existe"));
        }
        if (parent.get().parentId() != null) {
            return Optional.of(Rejection.invalid("Las subcategorías tienen un solo nivel"));
        }
        if (!parent.get().kind().equals(d.kind())) {
            return Optional.of(Rejection.invalid("La subcategoría debe ser del mismo tipo que su categoría"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, CategoryData d) {
        return returning(UPSERT, base(userId, change)
                .addValue("parentId", d.parentId())
                .addValue("name", d.name())
                .addValue("kind", d.kind())
                .addValue("isBase", d.isBase())
                .addValue("systemKey", d.systemKey())
                .addValue("icon", d.icon())
                .addValue("sortOrder", d.sortOrder()));
    }
}
