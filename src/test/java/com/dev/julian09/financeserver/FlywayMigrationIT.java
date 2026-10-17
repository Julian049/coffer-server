package com.dev.julian09.financeserver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Every migration on an empty database and on one holding MVP data, as production and staging have (S3-QA01). */
class FlywayMigrationIT {

    @Test
    void migratesAnEmptyDatabaseWithoutCreatingTheOwner() {
        try (PostgreSQLContainer db = new PostgreSQLContainer("postgres:16-alpine")) {
            db.start();
            flyway(db, null).migrate();
            JdbcTemplate jdbc = jdbc(db);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        }
    }

    @Test
    void movesMvpTransactionsToTheOwnerAndKeepsTheOldTable() {
        try (PostgreSQLContainer db = new PostgreSQLContainer("postgres:16-alpine")) {
            db.start();
            flyway(db, "2").migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("""
                    INSERT INTO transactions (local_id, title, value, type, description, date, synchronized) VALUES
                    ('11111111-1111-1111-1111-111111111111', 'Almuerzo', -28000, true, 'con Ana', '2026-05-01T12:00:00Z', true),
                    ('22222222-2222-2222-2222-222222222222', 'Sueldo', 3500000, false, NULL, '2026-05-02T08:00:00Z', true),
                    ('33333333-3333-3333-3333-333333333333', 'Cero', 0, false, NULL, '2026-05-03T08:00:00Z', true)
                    """);

            flyway(db, null).migrate();

            assertThat(jdbc.queryForObject("SELECT username FROM users", String.class)).isEqualTo("owner");
            List<Map<String, Object>> moved = jdbc.queryForList(
                    "SELECT type, amount, note, category_id::text AS category FROM transactions ORDER BY occurred_at");
            assertThat(moved).hasSize(2); // a zero amount cannot exist in the new model
            assertThat(moved.get(0)).containsEntry("type", "EXPENSE").containsEntry("note", "Almuerzo · con Ana")
                    .containsEntry("category", "00000000-0000-0000-0000-0000000000b1");
            assertThat((BigDecimal) moved.get(0).get("amount")).isEqualByComparingTo("28000");
            assertThat(moved.get(1)).containsEntry("type", "INCOME").containsEntry("note", "Sueldo");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions_mvp", Integer.class)).isEqualTo(3);
            // The trigger gave every migrated row a version, so the owner's data is pullable
            assertThat(jdbc.queryForObject("SELECT min(version) FROM transactions", Long.class)).isPositive();
        }
    }

    private static Flyway flyway(PostgreSQLContainer db, String target) {
        var config = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
