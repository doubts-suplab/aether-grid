package com.suplab.aether.agents.selfimproving;

import com.agentharness.Harness;
import com.suplab.aether.agents.harness.HarnessRouting;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmRequest;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;
import com.suplab.aether.core.domain.AgentFeedback;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.ports.AgentFeedbackPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SelfImprovingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SelfImprovingAgent.class);
    private static final String AGENT_TYPE = "SelfImprovingAgent";
    private static final int FEEDBACK_LIMIT = 20;

    private static final String SYSTEM_PROMPT = """
            You are a self-improvement agent. Analyse agent decision feedback to identify patterns \
            of incorrect decisions. Suggest specific threshold adjustments or behavior changes.
            Return JSON with exactly three fields:
            {"decision":"SUGGEST","confidence":0.0-1.0,"rationale":"specific improvement suggestion"}
            Keep rationale under 200 chars. Reply ONLY with JSON, no markdown.
            """;

    private final LlmClient llmClient;
    private final AgentFeedbackPort feedbackPort;
    private final Harness harness;

    public SelfImprovingAgent(LlmClient llmClient, AgentFeedbackPort feedbackPort, Harness harness) {
        this.llmClient = llmClient;
        this.feedbackPort = feedbackPort;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.SELF_IMPROVEMENT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentOutput execute(AgentInput input) {
        var tenantId = input.tenantId();

        var agentTypesToAnalyse = input.context().containsKey("agentTypes")
                ? (List<String>) input.context().get("agentTypes")
                : List.of("GovernanceAgent", "RetryAgent", "HallucinationDetectorAgent",
                          "TemporalPredictionAgent", "ReflectionAgent");

        var allFeedback = agentTypesToAnalyse.stream()
                .flatMap(type -> feedbackPort.findByAgentType(tenantId, type, FEEDBACK_LIMIT).stream())
                .toList();

        if (allFeedback.isEmpty()) {
            log.info("SelfImprovingAgent: no feedback data found for tenant={}", tenantId);
            return deferWithRationale(input, "No feedback data available for self-improvement", 0.3);
        }

        var summary = buildFeedbackSummary(allFeedback);
        log.info("SelfImprovingAgent: analysing {} feedback records for tenant={}", allFeedback.size(), tenantId);

        var request = LlmRequest.of(
                llmClient.provider().name().toLowerCase() + ":self-improvement",
                SYSTEM_PROMPT,
                summary
        );

        try {
            var response = llmClient.complete(request);
            return parseResponse(input, response.content());
        } catch (Exception e) {
            log.warn("SelfImprovingAgent LLM call failed for callId={}: {}", input.callId(), e.getMessage());
            return deferWithRationale(input, "Self-improvement analysis unavailable", 0.4);
        }
    }

    private String buildFeedbackSummary(List<AgentFeedback> records) {
        var statsByAgent = records.stream().collect(Collectors.groupingBy(AgentFeedback::agentType));

        var sb = new StringBuilder("Agent feedback summary:\n");
        statsByAgent.forEach((agentType, feedbackList) -> {
            long total = feedbackList.size();
            long correct = feedbackList.stream()
                    .filter(f -> f.outcome() == DecisionOutcome.CORRECT)
                    .count();
            long incorrect = feedbackList.stream()
                    .filter(f -> f.outcome() == DecisionOutcome.INCORRECT)
                    .count();
            double accuracy = total > 0 ? (correct * 100.0 / total) : 0.0;

            sb.append(String.format("Agent: %s | total=%d correct=%d incorrect=%d accuracy=%.1f%%\n",
                    agentType, total, correct, incorrect, accuracy));

            feedbackList.stream()
                    .filter(f -> f.outcome() == DecisionOutcome.INCORRECT)
                    .limit(3)
                    .forEach(f -> sb.append(String.format(
                            "  Incorrect: decision=%s confidence=%.2f\n",
                            f.originalDecision(), f.originalConfidence())));
        });

        return sb.toString();
    }

    private AgentOutput parseResponse(AgentInput input, String llmContent) {
        try {
            var json = extractJson(llmContent);
            var decision = parseDecision(json);
            var confidence = parseConfidence(json);
            var rationale = parseRationale(json);
            return HarnessRouting.gate(harness, AGENT_TYPE, input, decision, confidence, rationale,
                    Map.of("provider", llmClient.provider().name()));
        } catch (Exception e) {
            log.warn("Failed to parse SelfImprovingAgent LLM response: {}", e.getMessage());
            return deferWithRationale(input, "Self-improvement analysis unavailable", 0.4);
        }
    }

    private AgentOutput deferWithRationale(AgentInput input, String rationale, double confidence) {
        return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.DEFER, confidence, rationale, Map.of());
    }

    private String extractJson(String content) {
        var start = content.indexOf('{');
        var end = content.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object found in LLM response");
        }
        return content.substring(start, end + 1);
    }

    private AgentDecision parseDecision(String json) {
        var match = extractStringValue(json, "decision");
        return AgentDecision.valueOf(match.toUpperCase());
    }

    private double parseConfidence(String json) {
        var match = extractNumberValue(json, "confidence");
        return Double.parseDouble(match);
    }

    private String parseRationale(String json) {
        return extractStringValue(json, "rationale");
    }

    private String extractStringValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("Key not found: " + key);
        return matcher.group(1);
    }

    private String extractNumberValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("Key not found: " + key);
        return matcher.group(1);
    }
}
