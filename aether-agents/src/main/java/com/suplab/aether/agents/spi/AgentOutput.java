package com.suplab.aether.agents.spi;

import com.suplab.aether.core.domain.ApiCallId;

import java.time.Instant;
import java.util.Map;

public record AgentOutput(
        ApiCallId callId,
        String agentType,
        AgentDecision decision,
        double confidence,
        boolean autoEnforced,
        String rationale,
        Map<String, Object> metadata,
        Instant decidedAt
) {
    public AgentOutput {
        if (callId == null) throw new IllegalArgumentException("callId must not be null");
        if (agentType == null || agentType.isBlank()) throw new IllegalArgumentException("agentType must not be blank");
        if (decision == null) throw new IllegalArgumentException("decision must not be null");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        if (rationale == null || rationale.isBlank()) throw new IllegalArgumentException("rationale must not be blank");
        if (metadata == null) metadata = Map.of();
        if (decidedAt == null) decidedAt = Instant.now();

        // Confidence gate (centralized in the agent-harness ConfidenceGate): an agent may only KEEP
        // autoEnforced when the gate agrees. Grid auto-enforces BLOCK at confidence >= 0.95.
        if (!HarnessConfidenceGate.autoEnforced(decision, confidence)) {
            autoEnforced = false;
        }
    }

    public boolean requiresHumanReview() {
        return decision == AgentDecision.BLOCK && !autoEnforced;
    }
}
