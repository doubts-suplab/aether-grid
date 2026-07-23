package com.suplab.aether.agents.reflection;

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

public class ReflectionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReflectionAgent.class);
    private static final String AGENT_TYPE = "ReflectionAgent";

    private static final String SYSTEM_PROMPT = """
            You are a system reflection agent. Evaluate the current system health based on memory patterns.
            Identify what is going wrong and suggest concrete optimizations.
            Return JSON with exactly three fields:
            {"decision":"SUGGEST|DEFER","confidence":0.0-1.0,"rationale":"brief reason"}
            - SUGGEST: low health detected — provide actionable improvement suggestions
            - DEFER: insufficient data for meaningful reflection
            Keep rationale under 200 chars. Reply ONLY with JSON, no markdown.
            """;

    private final LlmClient llmClient;
    private final Harness harness;

    public ReflectionAgent(LlmClient llmClient, Harness harness) {
        this.llmClient = llmClient;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.REFLECTION);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        var memories = input.relevantMemories();

        if (memories.isEmpty()) {
            log.debug("ReflectionAgent: no memories for callId={}, skipping reflection", input.callId());
            return skipped(input);
        }

        long proceduralCount = memories.stream()
                .filter(m -> m.memoryType() == MemoryType.PROCEDURAL)
                .count();
        double healthScore = (double) proceduralCount / (memories.size() + 1);

        log.debug("ReflectionAgent: callId={} healthScore={} proceduralCount={} totalMemories={}",
                input.callId(), healthScore, proceduralCount, memories.size());

        if (healthScore >= 0.5) {
            int healthPercent = (int) Math.round(healthScore * 100);
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALLOW,
                    healthScore, "System health nominal at " + healthPercent + "%",
                    Map.of("healthScore", healthScore, "proceduralCount", proceduralCount));
        }

        var userPrompt = String.format(
                "System health score: %.2f (procedural memories: %d / total: %d).%n" +
                "API call context: %s%nAnalyse what is going wrong and suggest optimizations.",
                healthScore, proceduralCount, memories.size(), input.serialisedApiCall()
        );

        try {
            var response = llmClient.complete(LlmRequest.of(
                    llmClient.provider().name().toLowerCase() + ":reflection",
                    SYSTEM_PROMPT,
                    userPrompt
            ));
            return parseResponse(input, response.content(), healthScore, proceduralCount);
        } catch (Exception e) {
            log.warn("ReflectionAgent LLM call failed for callId={}: {}", input.callId(), e.getMessage());
            return skipped(input);
        }
    }

    private AgentOutput skipped(AgentInput input) {
        return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.DEFER,
                0.4, "Reflection skipped — insufficient data", Map.of());
    }

    private AgentOutput parseResponse(AgentInput input, String content, double healthScore, long proceduralCount) {
        try {
            var json = extractJson(content);
            var decision = AgentDecision.valueOf(extractStringValue(json, "decision").toUpperCase());
            var confidence = Double.parseDouble(extractNumberValue(json, "confidence"));
            var rationale = extractStringValue(json, "rationale");
            return HarnessRouting.gate(harness, AGENT_TYPE, input, decision, confidence, rationale,
                    Map.of("healthScore", healthScore, "proceduralCount", proceduralCount,
                            "provider", llmClient.provider().name()));
        } catch (Exception e) {
            log.warn("ReflectionAgent failed to parse LLM response for callId={}: {}", input.callId(), e.getMessage());
            return skipped(input);
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
