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
 * Routes a Grid agent's proposed decision through the agent-harness {@link Harness} for gating. The
 * agent keeps its own logic and per-branch metadata; the harness applies the centralized confidence
 * gate and sets {@code autoEnforced} (a BLOCK auto-enforces only at ≥ 0.95). Every migrated agent
 * gates through this one path, so enforcement lives in exactly one place and no agent can bypass it.
 *
 * <p><b>Per-agent authority ceilings.</b> Each agent is wrapped at its <em>honest</em> authority level
 * — the highest {@link DecisionAction} it may emit (spec §3.1) — not a blanket {@code BLOCK}. The
 * harness enforces this: an action above the ceiling, or outside the declared capability set, becomes a
 * security event and a safe non-enforcing decision (see {@code Harness.runAgent}). So a suggestion-only
 * agent (e.g. {@code RetryAgent}) can never auto-enforce a {@code BLOCK} even if its logic produced one.
 * Only {@code GovernanceAgent} carries {@code BLOCK} authority, and it wraps its own adapter directly.
 * Centralising the ceilings here keeps the whole grid's authority policy auditable in a single place.</p>
 */
public final class HarnessRouting {

    /** An agent's static authority ceiling + the decision actions it may emit (spec §3.1/§3.3). */
    private record Authority(AuthorityLevel level, Set<DecisionAction> capabilities) {
    }

    // Ceilings honest to what each agent actually emits (ALLOW/DEFER require OBSERVE, SUGGEST→SUGGEST,
    // ALERT→ALERT, BLOCK→BLOCK). DEFER is always included as the universal safe fallback.
    private static final Authority RETRY = new Authority(AuthorityLevel.SUGGEST,
            Set.of(DecisionAction.ALLOW, DecisionAction.SUGGEST, DecisionAction.DEFER));
    private static final Authority ALERTING = new Authority(AuthorityLevel.ALERT,
            Set.of(DecisionAction.ALLOW, DecisionAction.ALERT, DecisionAction.DEFER));
    private static final Authority TEMPORAL = new Authority(AuthorityLevel.ALERT,
            Set.of(DecisionAction.ALERT, DecisionAction.DEFER));
    private static final Authority SUGGESTING = new Authority(AuthorityLevel.SUGGEST,
            Set.of(DecisionAction.ALLOW, DecisionAction.SUGGEST, DecisionAction.DEFER));
    private static final Authority IMPROVING = new Authority(AuthorityLevel.SUGGEST,
            Set.of(DecisionAction.SUGGEST, DecisionAction.DEFER));
    private static final Authority OBSERVING = new Authority(AuthorityLevel.OBSERVE,
            Set.of(DecisionAction.ALLOW, DecisionAction.DEFER));

    private static final Map<String, Authority> PROFILES = Map.of(
            "RetryAgent", RETRY,                          // recommends retries → SUGGEST
            "HallucinationDetectorAgent", ALERTING,       // flags divergent rules → ALERT
            "TemporalPredictionAgent", TEMPORAL,          // predicts failure windows → ALERT
            "ReflectionAgent", SUGGESTING,                // reflects on decisions → SUGGEST
            "SelfImprovingAgent", IMPROVING,              // proposes improvements → SUGGEST
            "AetherCoreBridgeAgent", OBSERVING);          // enriches context, allows → OBSERVE

    // Fail-safe default for an unprofiled agent: observe-only. A new agent must declare a ceiling
    // (add a profile) to gain authority to ALERT/SUGGEST/BLOCK — it can never silently inherit BLOCK.
    private static final Authority DEFAULT = OBSERVING;

    private HarnessRouting() {
    }

    /** The declared authority ceiling for an agent type (fail-safe observe-only when unprofiled). */
    static AuthorityLevel authorityFor(String agentType) {
        return PROFILES.getOrDefault(agentType, DEFAULT).level();
    }

    /**
     * Gate a proposed grid decision through the harness, preserving the agent's output metadata. The
     * wrapping authority/capabilities come from the agent's declared ceiling, so the harness rejects
     * any over-authority action as a safe, audited failure.
     */
    public static AgentOutput gate(Harness harness, String agentType, AgentInput input,
                    AgentDecision decision, double confidence, String rationale,
                    Map<String, Object> metadata) {
        var profile = PROFILES.getOrDefault(agentType, DEFAULT);
        var proposed = new LegacyResult(DecisionAction.valueOf(decision.name()), confidence, rationale);
        var adapter = new LegacyAgentAdapter(agentType, profile.level(), profile.capabilities(), hi -> proposed);
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
