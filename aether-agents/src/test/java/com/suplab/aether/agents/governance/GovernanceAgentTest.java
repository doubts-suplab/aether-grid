package com.suplab.aether.agents.governance;

import com.suplab.aether.agents.harness.HarnessSupport;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmProvider;
import com.suplab.aether.agents.llm.LlmResponse;
import com.suplab.aether.agents.spi.*;
import com.suplab.aether.core.domain.ApiCallId;
import com.suplab.aether.core.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GovernanceAgentTest {

    @Mock
    private LlmClient llmClient;

    private GovernanceAgent agent;

    private static final ApiCallId CALL_ID = ApiCallId.generate();
    private static final TenantId TENANT_ID = TenantId.generate();

    @BeforeEach
    void setUp() {
        agent = new GovernanceAgent(llmClient, HarnessSupport.governanceHarness());
    }

    @Test
    void execute_parsesAllowDecision() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(
                new LlmResponse("""
                        {"decision":"ALLOW","confidence":0.92,"rationale":"normal request pattern"}
                        """, "gemma2:2b", LlmProvider.OLLAMA, 50, 30, 120));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isEqualTo(0.92);
        assertThat(output.rationale()).isEqualTo("normal request pattern");
        assertThat(output.autoEnforced()).isFalse();
    }

    @Test
    void execute_parsesBlockDecision_aboveThreshold_autoEnforced() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(
                new LlmResponse("""
                        {"decision":"BLOCK","confidence":0.95,"rationale":"SQL injection attempt"}
                        """, "gemma2:2b", LlmProvider.OLLAMA, 50, 30, 100));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.BLOCK);
        assertThat(output.autoEnforced()).isTrue();
    }

    @Test
    void execute_parsesBlockDecision_belowThreshold_notAutoEnforced() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(
                new LlmResponse("""
                        {"decision":"BLOCK","confidence":0.65,"rationale":"suspicious but not certain"}
                        """, "gemma2:2b", LlmProvider.OLLAMA, 50, 30, 100));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.BLOCK);
        assertThat(output.autoEnforced()).isFalse();
        assertThat(output.requiresHumanReview()).isTrue();
    }

    @Test
    void execute_defaultsToAllow_onLlmFailure() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenThrow(new RuntimeException("LLM down"));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isLessThan(0.8);
    }

    @Test
    void agentType_and_capabilities_correct() {
        assertThat(agent.agentType()).isEqualTo("GovernanceAgent");
        assertThat(agent.capabilities()).contains(AgentCapability.GOVERNANCE);
    }

    private AgentInput buildInput() {
        return new AgentInput(CALL_ID, TENANT_ID, AgentCapability.GOVERNANCE,
                "GET /v1/data 200 45ms", List.of(), Map.of());
    }
}
