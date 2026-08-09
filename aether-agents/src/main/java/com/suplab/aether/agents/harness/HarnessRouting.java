package com.suplab.aether.agents.harness;

import com.suplab.agentharness.Harness;
import com.suplab.agentharness.interop.LegacyAgentAdapter;
import com.suplab.agentharness.interop.LegacyAgentAdapter.LegacyResult;
import com.suplab.agentharness.model.AuthorityLevel;
import com.suplab.agentharness.model.DecisionAction;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;

import java.util.Map;
import java.util.Set;

/**
 * Routes a Grid agent's proposed decision through the agent-harness
 * {@link Harness} for gating. The agent
 * keeps its own logic and per-branch metadata; the harness applies the
 * centralized confidence gate and sets
 * {@code autoEnforced} (a BLOCK auto-enforces only at ≥ 0.95). Every migrated
 * agent gates through this one
 * path, so enforcement lives in exactly one place and no agent can bypass it.
 */
public final class HarnessRouting {

        // Grid agents may emit any decision; authority BLOCK keeps every emitted action
        // within authority, and
        // the harness gate ensures only a BLOCK at ≥ 0.95 auto-enforces.
        private static final AuthorityLevel AUTHORITY = AuthorityLevel.BLOCK;
        private static final Set<DecisionAction> CAPABILITIES = Set.of(
                        DecisionAction.ALLOW, DecisionAction.BLOCK, DecisionAction.ALERT,
                        DecisionAction.SUGGEST, DecisionAction.DEFER);

        private HarnessRouting() {
        }

        /**
         * Gate a proposed grid decision through the harness, preserving the agent's
         * output metadata.
         */
        public static AgentOutput gate(Harness harness, String agentType, AgentInput input,
                        AgentDecision decision, double confidence, String rationale,
                        Map<String, Object> metadata) {
                var proposed = new LegacyResult(DecisionAction.valueOf(decision.name()), confidence, rationale);
                var adapter = new LegacyAgentAdapter(agentType, AUTHORITY, CAPABILITIES, hi -> proposed);
                var request = new com.suplab.agentharness.model.AgentInput(
                                input.tenantId().value().toString(),
                                input.callId().value().toString(),
                                Map.of(),
                                Map.of("capability", input.capability().name()));
                var gated = harness.invoke(adapter, request).decision();
                return new AgentOutput(input.callId(), agentType,
                                AgentDecision.valueOf(gated.action().name()),
                                gated.confidence(), gated.autoEnforced(), gated.rationale(),
                                metadata, null);
        }
}
