package com.suplab.aether.agents.spi;

import com.suplab.agentharness.ConfidenceGate;
import com.suplab.agentharness.model.AuthorityLevel;
import com.suplab.agentharness.model.Decision;
import com.suplab.agentharness.model.DecisionAction;

/**
 * Single source of truth for Grid's confidence gate, delegating to the
 * agent-harness
 * {@link ConfidenceGate}. Replaces the hardcoded {@code 0.8} checks previously
 * duplicated across
 * {@code AgentOutput}, {@code GovernanceAgent}, and
 * {@code TemporalPredictionAgent}.
 *
 * <p>
 * Grid only auto-enforces {@code BLOCK}; the harness supplies the threshold. A
 * {@code BLOCK}-authority
 * decision auto-enforces at confidence &ge; 0.95 (the AIEL authority ladder),
 * superseding Grid's former
 * flat 0.8.
 */
public final class HarnessConfidenceGate {

    private static final ConfidenceGate GATE = new ConfidenceGate();

    private HarnessConfidenceGate() {
    }

    public static boolean autoEnforced(AgentDecision decision, double confidence) {
        if (decision != AgentDecision.BLOCK) {
            return false;
        }
        Decision gated = GATE.evaluate(
                Decision.propose(DecisionAction.BLOCK, confidence, "grid governance decision"),
                AuthorityLevel.BLOCK);
        return gated.autoEnforced();
    }
}
