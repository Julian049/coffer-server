package com.dev.julian09.financeserver.sync.handler;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.sync.dto.EntityData.TagData;
import com.dev.julian09.financeserver.sync.dto.IncomingChange;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TagSyncHandler extends SyncHandler<TagData> {

    private static final String UPSERT = """
            INSERT INTO tags AS t (id, user_id, name, pinned, created_at, updated_at, deleted_at)
            VALUES (:id, :userId, :name, :pinned, :createdAt, :updatedAt, :deletedAt)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, pinned = EXCLUDED.pinned,
                updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
            WHERE t.user_id = EXCLUDED.user_id AND EXCLUDED.updated_at > t.updated_at
            RETURNING version
            """;

    public TagSyncHandler(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, TagData.class);
    }

    @Override
    public String entity() {
        return "tag";
    }

    @Override
    protected String table() {
        return "tags";
    }

    @Override
    public int order(TagData data) {
        return 4;
    }

    @Override
    public Optional<Long> upsert(UUID userId, IncomingChange change, TagData d) {
        return returning(UPSERT, base(userId, change).addValue("name", d.name()).addValue("pinned", d.pinned()));
    }
}
