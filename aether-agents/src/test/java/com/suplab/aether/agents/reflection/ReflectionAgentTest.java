package com.suplab.aether.agents.reflection;

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
class ReflectionAgentTest {

    @Mock
    private LlmClient llmClient;

    private ReflectionAgent agent;

    private static final ApiCallId CALL_ID = ApiCallId.generate();
    private static final TenantId TENANT_ID = TenantId.generate();
    private static final float[] FAKE_EMBEDDING = new float[384];

    @BeforeEach
    void setUp() {
        agent = new ReflectionAgent(llmClient, HarnessSupport.governanceHarness());
    }

    @Test
    void execute_withPoorHealth_returnsSuggest() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenReturn(
                new LlmResponse(
                        "{\"decision\":\"SUGGEST\",\"confidence\":0.75,\"rationale\":\"Retry with backoff and reduce timeout\"}",
                        "gemma2:2b", LlmProvider.OLLAMA, 50, 30, 120));

        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "service timeout", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "connection refused", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.SUGGEST);
        assertThat(output.confidence()).isEqualTo(0.75);
        assertThat(output.rationale()).isEqualTo("Retry with backoff and reduce timeout");
    }

    @Test
    void execute_withGoodHealth_returnsAllow() {
        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "call succeeded", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "call succeeded", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "call succeeded", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "call succeeded", FAKE_EMBEDDING, null),
                MemoryRecord.create(TENANT_ID, MemoryType.PROCEDURAL, "call succeeded", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.ALLOW);
        assertThat(output.confidence()).isGreaterThan(0.5);
        assertThat(output.rationale()).contains("nominal");
    }

    @Test
    void execute_withNoMemories_returnsDefer() {
        var output = agent.execute(buildInput(List.of()));

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.confidence()).isEqualTo(0.4);
        assertThat(output.rationale()).contains("insufficient data");
    }

    @Test
    void execute_onLlmFailure_returnsDefer() {
        when(llmClient.provider()).thenReturn(LlmProvider.OLLAMA);
        when(llmClient.complete(any())).thenThrow(new RuntimeException("LLM down"));

        var memories = List.of(
                MemoryRecord.create(TENANT_ID, MemoryType.EPISODIC, "timeout", FAKE_EMBEDDING, null)
        );

        var output = agent.execute(buildInput(memories));

        assertThat(output.decision()).isEqualTo(AgentDecision.DEFER);
        assertThat(output.rationale()).contains("insufficient data");
    }

    @Test
    void agentType_returnsCorrectType() {
        assertThat(agent.agentType()).isEqualTo("ReflectionAgent");
    }

    @Test
    void capabilities_includesReflection() {
        assertThat(agent.capabilities()).contains(AgentCapability.REFLECTION);
    }

    private AgentInput buildInput(List<MemoryRecord> memories) {
        return new AgentInput(CALL_ID, TENANT_ID, AgentCapability.REFLECTION,
                "GET /v1/health 200 120ms", memories, Map.of());
    }
}
