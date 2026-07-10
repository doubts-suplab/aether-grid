package com.suplab.aether.core.memory.lifecycle;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
class JdbcMemoryLifecycleServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcMemoryLifecycleService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbc = new NamedParameterJdbcTemplate(dataSource);
        // decayRate 0.01/day, grace period 7 days, archive below 0.1
        service = new JdbcMemoryLifecycleService(jdbc, 0.01, 7, 0.1);
    }

    @Test
    void runLifecycle_decaysMemoriesOlderThanGracePeriod() {
        var userId = "user-" + UUID.randomUUID();
        var id = insertMemory(userId, 1.0, Instant.now().minus(10, ChronoUnit.DAYS));

        var result = service.runLifecycle();

        assertThat(result.decayedCount()).isGreaterThanOrEqualTo(1);
        // 10 days since access × 0.01/day = -0.1
        assertThat(strengthOf(id)).isCloseTo(0.9, within(0.01));
    }

    @Test
    void runLifecycle_leavesRecentlyAccessedMemoriesUntouched() {
        var userId = "user-" + UUID.randomUUID();
        var id = insertMemory(userId, 0.8, Instant.now().minus(2, ChronoUnit.DAYS));

        service.runLifecycle();

        assertThat(strengthOf(id)).isEqualTo(0.8);
    }

    @Test
    void runLifecycle_archivesFadedMemories() {
        var userId = "user-" + UUID.randomUUID();
        var id = insertMemory(userId, 0.05, Instant.now().minus(1, ChronoUnit.DAYS));

        var result = service.runLifecycle();

        assertThat(result.archivedCount()).isGreaterThanOrEqualTo(1);
        assertThat(existsInActive(id)).isFalse();
        assertThat(existsInArchive(id)).isTrue();
    }

    @Test
    void runLifecycle_decayBelowThresholdArchivesInSameRun() {
        var userId = "user-" + UUID.randomUUID();
        // 0.4 strength, 40 days stale → decay of 0.4 → 0.0 → below 0.1 → archived
        var id = insertMemory(userId, 0.4, Instant.now().minus(40, ChronoUnit.DAYS));

        service.runLifecycle();

        assertThat(existsInActive(id)).isFalse();
        assertThat(existsInArchive(id)).isTrue();
    }

    @Test
    void runLifecycle_strengthNeverGoesNegative() {
        var userId = "user-" + UUID.randomUUID();
        // 0.2 strength, 100 days stale → raw decay 1.0 → floored at 0, then archived
        var id = insertMemory(userId, 0.2, Instant.now().minus(100, ChronoUnit.DAYS));

        service.runLifecycle();

        var archivedStrength = jdbc.queryForObject(
                "SELECT strength FROM personal_memories_archive WHERE id = :id",
                new MapSqlParameterSource("id", id), Double.class);
        assertThat(archivedStrength).isEqualTo(0.0);
    }

    @Test
    void runLifecycle_reportsTotalRemaining() {
        var userId = "user-" + UUID.randomUUID();
        insertMemory(userId, 1.0, Instant.now());
        insertMemory(userId, 0.05, Instant.now()); // will be archived

        var result = service.runLifecycle();

        var remaining = jdbc.queryForObject("SELECT COUNT(*) FROM personal_memories",
                new MapSqlParameterSource(), Long.class);
        assertThat(result.totalRemaining()).isEqualTo(remaining);
    }

    private UUID insertMemory(String userId, double strength, Instant lastAccessedAt) {
        var id = UUID.randomUUID();
        var sql = """
                INSERT INTO personal_memories
                    (id, user_id, memory_type, content, strength, access_count, created_at, last_accessed_at)
                VALUES
                    (:id, :userId, 'EPISODIC', 'test memory', :strength, 0, :createdAt, :lastAccessedAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("strength", strength)
                .addValue("createdAt", Timestamp.from(lastAccessedAt))
                .addValue("lastAccessedAt", Timestamp.from(lastAccessedAt)));
        return id;
    }

    private double strengthOf(UUID id) {
        Double strength = jdbc.queryForObject(
                "SELECT strength FROM personal_memories WHERE id = :id",
                new MapSqlParameterSource("id", id), Double.class);
        return strength != null ? strength : -1;
    }

    private boolean existsInActive(UUID id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM personal_memories WHERE id = :id",
                new MapSqlParameterSource("id", id), Long.class);
        return count != null && count > 0;
    }

    private boolean existsInArchive(UUID id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM personal_memories_archive WHERE id = :id",
                new MapSqlParameterSource("id", id), Long.class);
        return count != null && count > 0;
    }
}
