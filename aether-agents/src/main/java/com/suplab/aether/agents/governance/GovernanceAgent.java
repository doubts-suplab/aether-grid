package com.suplab.aether.agents.governance;

import com.suplab.agentharness.Harness;
import com.suplab.agentharness.interop.LegacyAgentAdapter;
import com.suplab.agentharness.model.AuthorityLevel;
import com.suplab.agentharness.model.DecisionAction;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmRequest;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class GovernanceAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAgent.class);
    private static final String AGENT_TYPE = "GovernanceAgent";

    // A governance agent may propose any decision; the harness gate decides
    // enforcement.
    private static final Set<DecisionAction> CAPABILITIES = Set.of(
            DecisionAction.ALLOW, DecisionAction.BLOCK, DecisionAction.ALERT,
            DecisionAction.SUGGEST, DecisionAction.DEFER);

    private static final String SYSTEM_PROMPT = """
            You are an API governance agent. Analyse the API call and relevant memory context.
            Return JSON with exactly two fields:
            {"decision":"ALLOW|BLOCK|ALERT|SUGGEST","confidence":0.0-1.0,"rationale":"brief reason"}
            Decision rules:
            - ALLOW: call looks normal based on historical patterns
            - ALERT: suspicious parameters or unusual patterns detected
            - SUGGEST: sub-optimal usage pattern, suggest improvement
            - BLOCK: only if clear policy violation with confidence >= 0.8
            Keep rationale under 200 chars. Reply ONLY with JSON, no markdown.
            """;

    private final LlmClient llmClient;
    private final Harness harness;

    public GovernanceAgent(LlmClient llmClient, Harness harness) {
        this.llmClient = llmClient;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.GOVERNANCE);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        // Route through the agent-harness: this agent only proposes a decision; the
        // harness applies the
        // centralized confidence gate (BLOCK auto-enforces at >= 0.95) and sets
        // autoEnforced.
        var adapter = new LegacyAgentAdapter(AGENT_TYPE, AuthorityLevel.BLOCK, CAPABILITIES,
                harnessInput -> propose(input));
        var request = new com.suplab.agentharness.model.AgentInput(
                input.tenantId().value().toString(),
                input.callId().value().toString(),
                Map.of(),
                Map.of("capability", input.capability().name()));
        var decision = harness.invoke(adapter, request).decision();
        return new AgentOutput(
                input.callId(), AGENT_TYPE,
                AgentDecision.valueOf(decision.action().name()),
                decision.confidence(), decision.autoEnforced(), decision.rationale(),
                Map.of("provider", llmClient.provider().name()), null);
    }

    /**
     * Produce a proposed decision from the LLM. Fails open to ALLOW; the harness
     * gate decides enforcement.
     */
    private LegacyAgentAdapter.LegacyResult propose(AgentInput input) {
        var request = LlmRequest.of(
                llmClient.provider().name().toLowerCase() + ":governance", SYSTEM_PROMPT, buildPrompt(input));
        try {
            var json = extractJson(llmClient.complete(request).content());
            var action = DecisionAction.valueOf(extractStringValue(json, "decision").toUpperCase());
            var confidence = Double.parseDouble(extractNumberValue(json, "confidence"));
            var rationale = extractStringValue(json, "rationale");
            return new LegacyAgentAdapter.LegacyResult(action, confidence, rationale);
        } catch (Exception e) {
            log.warn("GovernanceAgent decision failed for callId={}: {}", input.callId(), e.getMessage());
            return new LegacyAgentAdapter.LegacyResult(
                    DecisionAction.ALLOW, 0.5, "LLM unavailable or unparseable — defaulting to ALLOW");
        }
    }

    private String buildPrompt(AgentInput input) {
        var memoryCount = input.relevantMemories().size();
        return String.format(
                "API call: %s\nRelevant memory records: %d\nCall context: %s",
                input.serialisedApiCall(),
                memoryCount,
                input.context().isEmpty() ? "none" : input.context().toString());
    }

    private String extractJson(String content) {
        var start = content.indexOf('{');
        var end = content.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start)
            throw new IllegalArgumentException("No JSON object found in LLM response");
        return content.substring(start, end + 1);
    }

    private String extractStringValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find())
            throw new IllegalArgumentException("Key not found: " + key);
        return matcher.group(1);
    }

    private String extractNumberValue(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find())
            throw new IllegalArgumentException("Key not found: " + key);
        return matcher.group(1);
    }
}
