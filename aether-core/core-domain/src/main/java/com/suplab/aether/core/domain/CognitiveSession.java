package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A multi-turn reasoning context for a user within a tenant.
 *
 * <p>Sessions capture the emotional arc and engagement level across an interaction,
 * allowing subsequent agent decisions to be tuned to the user's current cognitive state.
 * A user has at most one {@link SessionStatus#ACTIVE} session per tenant.</p>
 *
 * <p>The record is immutable — {@link #withTurn(String, String, double)} and
 * {@link #close()} return new instances. Turn summaries are a defensive copy.</p>
 */
public record CognitiveSession(
        UUID sessionId,
        String userId,
        String tenantId,
        List<String> turnSummaries,
        String emotionalState,
        double engagementScore,
        SessionStatus status,
        Instant startedAt,
        Instant lastActiveAt
) {
    public CognitiveSession {
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        turnSummaries = turnSummaries != null ? List.copyOf(turnSummaries) : List.of();
        if (emotionalState == null || emotionalState.isBlank()) emotionalState = "NEUTRAL";
        if (engagementScore < 0 || engagementScore > 1) engagementScore = 0.5;
        if (status == null) status = SessionStatus.ACTIVE;
        if (startedAt == null) startedAt = Instant.now();
        if (lastActiveAt == null) lastActiveAt = startedAt;
    }

    /**
     * Factory for a new ACTIVE session with no turns yet.
     */
    public static CognitiveSession start(String tenantId, String userId) {
        var now = Instant.now();
        return new CognitiveSession(UUID.randomUUID(), userId, tenantId, List.of(),
                "NEUTRAL", 0.5, SessionStatus.ACTIVE, now, now);
    }

    /**
     * Returns a new instance with the turn summary appended and cognitive state updated.
     *
     * @param turnSummary     one-line summary of the turn just completed
     * @param emotionalState  emotional state observed in this turn (null/blank keeps current)
     * @param engagementScore engagement observed in this turn (out-of-range keeps current)
     * @throws IllegalStateException if the session is already closed
     */
    public CognitiveSession withTurn(String turnSummary, String emotionalState, double engagementScore) {
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("cannot add a turn to a closed session");
        }
        if (turnSummary == null || turnSummary.isBlank()) {
            throw new IllegalArgumentException("turnSummary required");
        }
        var turns = new ArrayList<>(turnSummaries);
        turns.add(turnSummary);
        var nextEmotional = (emotionalState == null || emotionalState.isBlank())
                ? this.emotionalState : emotionalState.toUpperCase();
        var nextEngagement = (engagementScore < 0 || engagementScore > 1)
                ? this.engagementScore : engagementScore;
        return new CognitiveSession(sessionId, userId, tenantId, turns,
                nextEmotional, nextEngagement, status, startedAt, Instant.now());
    }

    /**
     * Returns a CLOSED copy of this session. Closing an already-closed session is a no-op.
     */
    public CognitiveSession close() {
        if (status == SessionStatus.CLOSED) {
            return this;
        }
        return new CognitiveSession(sessionId, userId, tenantId, turnSummaries,
                emotionalState, engagementScore, SessionStatus.CLOSED, startedAt, Instant.now());
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
}
