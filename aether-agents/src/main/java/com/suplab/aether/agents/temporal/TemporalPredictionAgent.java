package com.suplab.aether.agents.temporal;

import com.agentharness.Harness;
import com.suplab.aether.agents.harness.HarnessRouting;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmRequest;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;
import com.suplab.aether.core.domain.MemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class TemporalPredictionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TemporalPredictionAgent.class);
    private static final String AGENT_TYPE = "TemporalPredictionAgent";
    private static final String UNAVAILABLE = "Prediction unavailable — insufficient historical data or LLM error";

    private static final String SYSTEM_PROMPT = """
            You are a temporal prediction agent. Analyse historical failure and error patterns.
            Based on episodic memories (failures/timeouts) and semantic memories (4xx errors),
            predict whether a future failure window or latency spike is likely.
            Return JSON with exactly three fields:
            {"decision":"ALERT|DEFER","confidence":0.0-1.0,"rationale":"brief reason"}
            - ALERT: a failure window or latency spike is predicted
            - DEFER: insufficient data to make a confident prediction
            Keep rationale under 200 chars. Reply ONLY with JSON, no markdown.
            """;

    private final LlmClient llmClient;
    private final Harness harness;

    public TemporalPredictionAgent(LlmClient llmClient, Harness harness) {
        this.llmClient = llmClient;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.TEMPORAL_PREDICTION);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        var memories = input.relevantMemories();

        if (memories.isEmpty()) {
            log.debug("TemporalPredictionAgent: no memories for callId={}, returning DEFER", input.callId());
            return unavailable(input, 0.3);
        }

        long episodicCount = memories.stream()
                .filter(m -> m.memoryType() == MemoryType.EPISODIC)
                .count();
        long semanticCount = memories.stream()
                .filter(m -> m.memoryType() == MemoryType.SEMANTIC)
                .count();

        if (episodicCount + semanticCount == 0) {
            log.debug("TemporalPredictionAgent: no episodic/semantic memories for callId={}, returning DEFER", input.callId());
            return unavailable(input, 0.5);
        }

        var userPrompt = String.format(
                "Historical context: %d episodic (failures/timeouts) and %d semantic (4xx errors) memories detected.%n" +
                "Total memory records: %d%nAPI call context: %s",
                episodicCount, semanticCount, memories.size(), input.serialisedApiCall()
        );

        try {
            var response = llmClient.complete(LlmRequest.of(
                    llmClient.provider().name().toLowerCase() + ":temporal-prediction",
                    SYSTEM_PROMPT,
                    userPrompt
            ));
            return parseResponse(input, response.content());
        } catch (Exception e) {
            log.warn("TemporalPredictionAgent LLM call failed for callId={}: {}", input.callId(), e.getMessage());
            return unavailable(input, 0.5);
        }
    }

    private AgentOutput unavailable(AgentInput input, double confidence) {
        return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.DEFER, confidence, UNAVAILABLE, Map.of());
    }

    private AgentOutput parseResponse(AgentInput input, String content) {
        try {
            var json = extractJson(content);
            var decision = AgentDecision.valueOf(extractStringValue(json, "decision").toUpperCase());
            var confidence = Double.parseDouble(extractNumberValue(json, "confidence"));
            var rationale = extractStringValue(json, "rationale");
            return HarnessRouting.gate(harness, AGENT_TYPE, input, decision, confidence, rationale,
                    Map.of("provider", llmClient.provider().name()));
        } catch (Exception e) {
            log.warn("TemporalPredictionAgent failed to parse LLM response for callId={}: {}", input.callId(), e.getMessage());
            return unavailable(input, 0.5);
        }
    }

    private String extractJson(String content) {
        var start = content.indexOf('{');
        var end = content.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object found in LLM response");
        }
        return content.substring(start, end + 1);
    }

    private String extractStringValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("Key not found in JSON: " + key);
        return matcher.group(1);
    }

    private String extractNumberValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("Numeric key not found in JSON: " + key);
        return matcher.group(1);
    }
}
