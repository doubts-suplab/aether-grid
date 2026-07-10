package com.suplab.aether.core.memory.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.SessionStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcCognitiveSessionStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcCognitiveSessionStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        store = new JdbcCognitiveSessionStore(
                new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void save_andFindById_roundTrip() {
        var userId = "user-" + UUID.randomUUID();
        var session = CognitiveSession.start("acme", userId)
                .withTurn("discussed roadmap", "focused", 0.8);

        store.save(session);

        var found = store.findById(session.sessionId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().turnSummaries()).containsExactly("discussed roadmap");
        assertThat(found.get().emotionalState()).isEqualTo("FOCUSED");
        assertThat(found.get().status()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void findById_isScopedToUser() {
        var userId = "user-" + UUID.randomUUID();
        var session = CognitiveSession.start("acme", userId);
        store.save(session);

        assertThat(store.findById(session.sessionId(), "other-user")).isEmpty();
    }

    @Test
    void save_newActiveSessionClosesPreviousActive() {
        var userId = "user-" + UUID.randomUUID();
        var first = CognitiveSession.start("acme", userId);
        store.save(first);

        var second = CognitiveSession.start("acme", userId);
        store.save(second);

        var firstReloaded = store.findById(first.sessionId(), userId);
        assertThat(firstReloaded).isPresent();
        assertThat(firstReloaded.get().status()).isEqualTo(SessionStatus.CLOSED);

        var active = store.findActive("acme", userId);
        assertThat(active).isPresent();
        assertThat(active.get().sessionId()).isEqualTo(second.sessionId());
    }

    @Test
    void save_activeSessionsInDifferentTenantsCoexist() {
        var userId = "user-" + UUID.randomUUID();
        var acmeSession = CognitiveSession.start("acme", userId);
        var globexSession = CognitiveSession.start("globex", userId);

        store.save(acmeSession);
        store.save(globexSession);

        assertThat(store.findActive("acme", userId)).isPresent();
        assertThat(store.findActive("globex", userId)).isPresent();
    }

    @Test
    void save_upsertPersistsAppendedTurns() {
        var userId = "user-" + UUID.randomUUID();
        var session = CognitiveSession.start("acme", userId);
        store.save(session);

        var updated = session.withTurn("turn one", null, -1).withTurn("turn two", "engaged", 0.7);
        store.save(updated);

        var found = store.findById(session.sessionId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().turnSummaries()).containsExactly("turn one", "turn two");
        assertThat(found.get().engagementScore()).isEqualTo(0.7);
    }

    @Test
    void findActive_returnsEmptyAfterClose() {
        var userId = "user-" + UUID.randomUUID();
        var session = CognitiveSession.start("acme", userId);
        store.save(session);
        store.save(session.close());

        assertThat(store.findActive("acme", userId)).isEmpty();
    }

    @Test
    void findByUser_returnsMostRecentFirst() {
        var userId = "user-" + UUID.randomUUID();
        var first = CognitiveSession.start("acme", userId);
        store.save(first);
        var second = CognitiveSession.start("acme", userId); // closes first
        store.save(second);

        var sessions = store.findByUser("acme", userId, 10);
        assertThat(sessions).hasSize(2);
        assertThat(sessions.getFirst().sessionId()).isEqualTo(second.sessionId());
    }
}
