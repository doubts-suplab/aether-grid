package com.suplab.aether.agents.temporal;

import com.suplab.aether.agents.harness.HarnessSupport;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmProvider;
import com.suplab.aether.agents.llm.LlmResponse;
import com.suplab.aether.agents.spi.*;
import com.suplab.aether.core.domain.ApiCallId;
import com.suplab.aether.core.domain.MemoryRecord;
import com.suplab.aether.core.domain.MemoryType;
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
class TemporalPredictionAgentTest {

    @Mock
    private LlmClient llmClient;

    private TemporalPredictionAgent agent;

    private static final ApiCallId CALL_ID = ApiCallId.generate();
    private static final TenantId TENANT_ID = TenantId.generate();
    private static final float[] FAKE_EMBEDDING = new float[384];

    @BeforeEach
    void setUp() {
        agent = new TemporalPredictionAgent(llmClient, HarnessSupport.governanceHarness());
    }

    @Test
    void execute_withEpisodicMemories_returnsAlert() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(
                new LlmResponse(
                        "{\"decision\":\"ALERT\",\"confidence\":0.85,\"rationale\":\"High failure rate detected\"}",
                        "gemma2:2b", LlmProvider.OLLAMA, 50, 30, 120));

        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "connection timeout", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "service unavailable", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.ALERT);
        assertThat(output.confidence()).isEqualTo(0.85);
        assertThat(output.rationale()).isEqualTo("High failure rate detected");
    }

    @Test
    void execute_withNoMemories_returnsDeferWithLowConfidence() {
        var output = agent.execute(buildInput(List.of()));

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.confidence()).isEqualTo(0.3);
        assertThat(output.rationale()).contains("insufficient historical data");
    }

    @Test
    void execute_withOnlyProceduralMemories_returnsDeferWithoutCallingLlm() {
        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "successful call", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.confidence()).isEqualTo(0.5);
    }

    @Test
    void execute_onLlmFailure_returnsDefer() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenThrow(new RuntimeException("LLM unavailable"));

        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "timeout", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.rationale()).contains("Prediction unavailable");
    }

    @Test
    void agentType_returnsCorrectType() {
        assertThat(agent.agentType()).isEqualTo("TemporalPredictionAgent");
    }

    @Test
    void capabilities_includesTemporalPrediction() {
        assertThat(agent.capabilities()).contains(AgentCapability.TEMPORAL_PREDICTION);
    }

    private AgentInput buildInput(List<MemoryRecord> memories) {
        return new AgentInput(CALL_ID, TENANT_ID, AgentCapability.TEMPORAL_PREDICTION,
                "GET /v1/data 500 250ms", memories, Map.of());
    }
}
