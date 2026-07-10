package com.suplab.aether.core.memory.preference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcUserPreferenceStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcUserPreferenceStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        store = new JdbcUserPreferenceStore(
                new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void find_returnsEmptyMapForUnknownUser() {
        assertThat(store.find("user-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_andFind_roundTrip() {
        var userId = "user-" + UUID.randomUUID();
        store.save(userId, Map.of("communication-style", "async", "notification-frequency", "daily"));

        var found = store.find(userId);
        assertThat(found)
                .containsEntry("communication-style", "async")
                .containsEntry("notification-frequency", "daily");
    }

    @Test
    void save_replacesExistingPreferences() {
        var userId = "user-" + UUID.randomUUID();
        store.save(userId, Map.of("theme", "dark", "language", "en"));
        store.save(userId, Map.of("theme", "light"));

        var found = store.find(userId);
        assertThat(found).containsOnlyKeys("theme");
        assertThat(found).containsEntry("theme", "light");
    }

    @Test
    void save_emptyMapClearsPreferences() {
        var userId = "user-" + UUID.randomUUID();
        store.save(userId, Map.of("theme", "dark"));
        store.save(userId, Map.of());

        assertThat(store.find(userId)).isEmpty();
    }
}
