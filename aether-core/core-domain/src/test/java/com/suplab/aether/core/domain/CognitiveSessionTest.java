package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CognitiveSessionTest {

    @Test
    void start_createsActiveSessionWithDefaults() {
        var session = CognitiveSession.start("acme", "user-1");

        assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.isActive()).isTrue();
        assertThat(session.turnSummaries()).isEmpty();
        assertThat(session.emotionalState()).isEqualTo("NEUTRAL");
        assertThat(session.engagementScore()).isEqualTo(0.5);
        assertThat(session.tenantId()).isEqualTo("acme");
        assertThat(session.userId()).isEqualTo("user-1");
        assertThat(session.sessionId()).isNotNull();
    }

    @Test
    void withTurn_appendsSummaryAndUpdatesState() {
        var session = CognitiveSession.start("acme", "user-1");

        var updated = session.withTurn("Asked about Q3 goals", "focused", 0.8);

        assertThat(updated.turnSummaries()).containsExactly("Asked about Q3 goals");
        assertThat(updated.emotionalState()).isEqualTo("FOCUSED");
        assertThat(updated.engagementScore()).isCloseTo(0.8, within(0.001));
        assertThat(updated.sessionId()).isEqualTo(session.sessionId());
        assertThat(updated.startedAt()).isEqualTo(session.startedAt());
    }

    @Test
    void withTurn_keepsStateWhenNotProvided() {
        var session = CognitiveSession.start("acme", "user-1")
                .withTurn("first turn", "motivated", 0.9);

        var updated = session.withTurn("second turn", null, -1.0);

        assertThat(updated.turnSummaries()).containsExactly("first turn", "second turn");
        assertThat(updated.emotionalState()).isEqualTo("MOTIVATED");
        assertThat(updated.engagementScore()).isCloseTo(0.9, within(0.001));
    }

    @Test
    void withTurn_rejectsBlankSummary() {
        var session = CognitiveSession.start("acme", "user-1");

        assertThatThrownBy(() -> session.withTurn("  ", null, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turnSummary required");
    }

    @Test
    void withTurn_throwsOnClosedSession() {
        var closed = CognitiveSession.start("acme", "user-1").close();

        assertThatThrownBy(() -> closed.withTurn("late turn", null, 0.5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void close_transitionsToClosedAndIsIdempotent() {
        var session = CognitiveSession.start("acme", "user-1");

        var closed = session.close();
        assertThat(closed.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(closed.isActive()).isFalse();

        var closedAgain = closed.close();
        assertThat(closedAgain).isSameAs(closed);
    }

    @Test
    void close_preservesTurnsAndState() {
        var session = CognitiveSession.start("acme", "user-1")
                .withTurn("discussed roadmap", "engaged", 0.75);

        var closed = session.close();

        assertThat(closed.turnSummaries()).containsExactly("discussed roadmap");
        assertThat(closed.emotionalState()).isEqualTo("ENGAGED");
        assertThat(closed.engagementScore()).isCloseTo(0.75, within(0.001));
    }

    @Test
    void constructor_rejectsBlankUserIdAndTenantId() {
        assertThatThrownBy(() -> new CognitiveSession(null, "", "acme", null, null, 0.5, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId required");

        assertThatThrownBy(() -> new CognitiveSession(null, "user-1", " ", null, null, 0.5, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void constructor_defaultsNullStatusToActive() {
        var session = new CognitiveSession(null, "user-1", "acme", null, null, 0.5, null, null, null);

        assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
    }
}
