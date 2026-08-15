package com.suplab.aether.agents.harness;

import com.suplab.agentharness.model.AuthorityLevel;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.core.domain.ApiCallId;
import com.suplab.aether.core.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the per-agent authority ceilings actually bite: an agent whose ceiling is below {@code BLOCK}
 * cannot auto-enforce a {@code BLOCK} even when its logic proposes one at high confidence — the harness
 * turns the over-authority action into a safe, non-enforcing {@code DEFER}. A within-ceiling action
 * passes through unchanged. This is the governance fix for the previous blanket-{@code BLOCK} routing.
 */
class HarnessRoutingTest {

    private static AgentInput input() {
        return new AgentInput(ApiCallId.generate(), TenantId.generate(),
                AgentCapability.RETRY_STRATEGY, "GET /v1/data 500 250ms", List.of(), Map.of());
    }

    @Test
    void suggestCeilingAgentCannotAutoEnforceBlock() {
        // RetryAgent's ceiling is SUGGEST — a proposed BLOCK at 0.99 is above authority.
        var out = HarnessRouting.gate(HarnessSupport.governanceHarness(), "RetryAgent", input(),
                AgentDecision.BLOCK, 0.99, "retry now", Map.of());

        assertThat(out.autoEnforced()).isFalse();               // never auto-enforced
        assertThat(out.decision()).isEqualTo(AgentDecision.DEFER); // clamped to a safe, human-routed defer
        assertThat(out.decision()).isNotEqualTo(AgentDecision.BLOCK);
    }

    @Test
    void withinCeilingActionPassesThrough() {
        var out = HarnessRouting.gate(HarnessSupport.governanceHarness(), "RetryAgent", input(),
                AgentDecision.SUGGEST, 0.99, "recommend exponential backoff", Map.of());

        assertThat(out.decision()).isEqualTo(AgentDecision.SUGGEST); // in-contract, preserved
        assertThat(out.autoEnforced()).isFalse();                    // SUGGEST never auto-enforces
    }

    @Test
    void authorityCeilingsAreHonestPerAgent() {
        assertThat(HarnessRouting.authorityFor("RetryAgent")).isEqualTo(AuthorityLevel.SUGGEST);
        assertThat(HarnessRouting.authorityFor("HallucinationDetectorAgent")).isEqualTo(AuthorityLevel.ALERT);
        assertThat(HarnessRouting.authorityFor("TemporalPredictionAgent")).isEqualTo(AuthorityLevel.ALERT);
        assertThat(HarnessRouting.authorityFor("AetherCoreBridgeAgent")).isEqualTo(AuthorityLevel.OBSERVE);
        // an unprofiled agent fails safe to observe-only — it can never inherit BLOCK
        assertThat(HarnessRouting.authorityFor("SomeNewUnprofiledAgent")).isEqualTo(AuthorityLevel.OBSERVE);
    }
}
