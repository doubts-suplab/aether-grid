package com.suplab.aether.core.domain;

import java.time.Instant;

/**
 * Feedback about an Aether Grid agent decision that involved this user, consumed from
 * the {@code aether.core.feedback} Kafka topic.
 *
 * <p>Core learns from Grid outcomes: correct decisions become PROCEDURAL memories
 * (reusable how-to knowledge), and engagement signals update EMOTIONAL memory. The
 * message format is a flat JSON document — mirroring the REST contract, no DTO module
 * is shared between the two repositories.</p>
 *
 * @param tenantId         tenant the decision was made in
 * @param userId           user the decision concerned
 * @param agentType        Grid agent that made the decision (e.g. {@code GovernanceAgent})
 * @param decisionSummary  one-line human-readable summary of the decision
 * @param outcome          how the decision turned out
 * @param confidence       the agent's confidence at decision time (0.0–1.0)
 * @param engagementSignal observed user engagement (0.0–1.0), or negative when absent
 * @param occurredAt       when the decision outcome was determined
 */
public record AgentDecisionFeedback(
        String tenantId,
        String userId,
        String agentType,
        String decisionSummary,
        DecisionOutcome outcome,
        double confidence,
        double engagementSignal,
        Instant occurredAt
) {
    public AgentDecisionFeedback {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (agentType == null || agentType.isBlank()) throw new IllegalArgumentException("agentType required");
        if (decisionSummary == null || decisionSummary.isBlank()) throw new IllegalArgumentException("decisionSummary required");
        if (outcome == null) throw new IllegalArgumentException("outcome required");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be 0-1");
        if (occurredAt == null) occurredAt = Instant.now();
    }

    /** True when Grid observed an engagement signal for this interaction. */
    public boolean hasEngagementSignal() {
        return engagementSignal >= 0 && engagementSignal <= 1;
    }
}
