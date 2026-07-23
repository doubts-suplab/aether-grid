package com.suplab.aether.agents.bridge;

import com.suplab.aether.agents.harness.HarnessSupport;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.core.domain.ApiCallId;
import com.suplab.aether.core.domain.PersonalContext;
import com.suplab.aether.core.domain.TenantId;
import com.suplab.aether.core.ports.PersonalContextPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AetherCoreBridgeAgentTest {

    private final PersonalContextPort port = mock(PersonalContextPort.class);
    private final AetherCoreBridgeAgent agent = new AetherCoreBridgeAgent(port, HarnessSupport.governanceHarness());

    private static final TenantId TENANT_ID = TenantId.generate();
    private static final ApiCallId CALL_ID = ApiCallId.generate();

    @Test
    void enrich_withUserId_andContext_returnsHighConfidenceAllow() {
        var ctx = new PersonalContext(
                "user42", TENANT_ID,
                List.of("remembered Paris trip"),
                Map.of("language", "fr"),
                "CURIOUS", 0.85, Instant.now()
        );
        when(port.fetchFor(any(), eq("user42"))).thenReturn(Optional.of(ctx));

        var input = buildInput(Map.of("userId", "user42"));
        var output = agent.execute(input);

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(output.metadata()).containsKey("personalMemories");
        assertThat(output.metadata()).containsKey("emotionalState");
    }

    @Test
    void enrich_noUserId_skipsEnrichment() {
        var input = buildInput(Map.of());
        var output = agent.execute(input);

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isEqualTo(0.5);
        assertThat(output.rationale()).contains("skipped");
    }

    @Test
    void enrich_portReturnsEmpty_returnsAllow() {
        when(port.fetchFor(any(), eq("user99"))).thenReturn(Optional.empty());

        var input = buildInput(Map.of("userId", "user99"));
        var output = agent.execute(input);

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isEqualTo(0.6);
        assertThat(output.rationale()).contains("no personal context available");
    }

    @Test
    void enrich_portThrows_neverBlocks() {
        when(port.fetchFor(any(), eq("userX")))
                .thenThrow(new RuntimeException("upstream failure"));

        var input = buildInput(Map.of("userId", "userX"));
        var output = agent.execute(input);

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isEqualTo(0.5);
    }

    @Test
    void capabilities_includesPersonalContextEnrichment() {
        assertThat(agent.capabilities()).contains(AgentCapability.PERSONAL_CONTEXT_ENRICHMENT);
    }

    @Test
    void agentType_isCorrect() {
        assertThat(agent.agentType()).isEqualTo("AetherCoreBridgeAgent");
    }

    private AgentInput buildInput(Map<String, Object> contextEntries) {
        Map<String, Object> context = new HashMap<>(contextEntries);
        return new AgentInput(
                CALL_ID, TENANT_ID,
                AgentCapability.PERSONAL_CONTEXT_ENRICHMENT,
                "test-api-call",
                List.of(),
                context
        );
    }
}
