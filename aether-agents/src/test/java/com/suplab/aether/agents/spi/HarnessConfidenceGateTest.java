package com.suplab.aether.agents.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The centralized confidence gate (delegating to the agent-harness ConfidenceGate). Grid auto-enforces
 * BLOCK at confidence &ge; 0.95; everything else is never auto-enforced.
 */
class HarnessConfidenceGateTest {

    @Test
    void blockAtOrAboveThresholdAutoEnforces() {
        assertThat(HarnessConfidenceGate.autoEnforced(AgentDecision.BLOCK, 0.95)).isTrue();
        assertThat(HarnessConfidenceGate.autoEnforced(AgentDecision.BLOCK, 0.99)).isTrue();
    }

    @Test
    void blockBelowThresholdDoesNotAutoEnforce() {
        assertThat(HarnessConfidenceGate.autoEnforced(AgentDecision.BLOCK, 0.94)).isFalse();
        assertThat(HarnessConfidenceGate.autoEnforced(AgentDecision.BLOCK, 0.8)).isFalse();
    }

    @Test
    void nonBlockDecisionsNeverAutoEnforce() {
        for (AgentDecision d : AgentDecision.values()) {
            if (d != AgentDecision.BLOCK) {
                assertThat(HarnessConfidenceGate.autoEnforced(d, 1.0)).isFalse();
            }
        }
    }
}
