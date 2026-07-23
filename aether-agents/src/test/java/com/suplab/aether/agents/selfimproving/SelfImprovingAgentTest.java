package com.suplab.aether.agents.selfimproving;

import com.suplab.aether.agents.harness.HarnessSupport;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmProvider;
import com.suplab.aether.agents.llm.LlmResponse;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.core.domain.AgentFeedback;
import com.suplab.aether.core.domain.ApiCallId;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.domain.TenantId;
import com.suplab.aether.core.ports.AgentFeedbackPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfImprovingAgentTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private AgentFeedbackPort feedbackPort;

    private SelfImprovingAgent agent;

    private static final ApiCallId CALL_ID = ApiCallId.generate();
    private static final TenantId TENANT_ID = TenantId.generate();

    @BeforeEach
    void setUp() {
        agent = new SelfImprovingAgent(llmClient, feedbackPort, HarnessSupport.governanceHarness());
    }

    @Test
    void execute_withFeedbackData_returnsSuggest() {
        var feedbackRecords = buildMixedFeedback(10);
        when(feedbackPort.findByAgentType(any(TenantId.class), anyString(), anyInt()))
                .thenReturn(feedbackRecords);
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"decision\":\"SUGGEST\",\"confidence\":0.82,\"rationale\":\"Reduce BLOCK threshold from 0.8 to 0.85 for GovernanceAgent\"}",
                "gemma2:2b", LlmProvider.OLLAMA, 200, 80, 300));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.SUGGEST);
        assertThat(output.confidence()).isEqualTo(0.82);
        assertThat(output.rationale()).contains("Reduce BLOCK threshold");
        assertThat(output.autoEnforced()).isFalse();
    }

    @Test
    void execute_withNoFeedback_returnsDeferWithLowConfidence() {
        when(feedbackPort.findByAgentType(any(TenantId.class), anyString(), anyInt()))
                .thenReturn(List.of());

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.confidence()).isEqualTo(0.3);
        assertThat(output.rationale()).contains("No feedback data available");
    }

    @Test
    void execute_onLlmFailure_returnsDefer() {
        var feedbackRecords = buildMixedFeedback(5);
        when(feedbackPort.findByAgentType(any(TenantId.class), anyString(), anyInt()))
                .thenReturn(feedbackRecords);
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenThrow(new RuntimeException("LLM unavailable"));

        var output = agent.execute(buildInput());

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.confidence()).isEqualTo(0.4);
        assertThat(output.rationale()).contains("Self-improvement analysis unavailable");
    }

    @Test
    void agentType_and_capabilities_correct() {
        assertThat(agent.agentType()).isEqualTo("SelfImprovingAgent");
        assertThat(agent.capabilities()).contains(AgentCapability.SELF_IMPROVEMENT);
    }

    private AgentInput buildInput() {
        return new AgentInput(
                CALL_ID, TENANT_ID, AgentCapability.SELF_IMPROVEMENT,
                "weekly-review", List.of(),
                Map.of("agentTypes", List.of("GovernanceAgent"))
        );
    }

    private List<AgentFeedback> buildMixedFeedback(int count) {
        var records = new ArrayList<AgentFeedback>();
        for (int i = 0; i < count; i++) {
            var outcome = i % 3 == 0 ? DecisionOutcome.INCORRECT : DecisionOutcome.CORRECT;
            records.add(AgentFeedback.create(
                    TENANT_ID,
                    "GovernanceAgent",
                    UUID.randomUUID(),
                    "BLOCK",
                    0.75 + (i * 0.01),
                    outcome,
                    outcome == DecisionOutcome.INCORRECT ? "False positive detected" : null
            ));
        }
        return records;
    }
}
