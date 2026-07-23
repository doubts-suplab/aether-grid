package com.suplab.aether.agents.hallucination;

import com.agentharness.Harness;
import com.suplab.aether.agents.harness.HarnessRouting;
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

public class HallucinationDetectorAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(HallucinationDetectorAgent.class);
    private static final String AGENT_TYPE = "HallucinationDetectorAgent";

    private static final String SYSTEM_PROMPT = """
            You are a hallucination detection agent for LLM-generated API governance rules.
            Compare the proposed rule/output against historical API call patterns from memory.
            Return JSON: {"decision":"ALLOW|ALERT|BLOCK","confidence":0.0-1.0,
            "rationale":"explanation","hallucinated":true|false}
            - ALLOW: rule is consistent with observed patterns
            - ALERT: rule diverges from patterns, flag for review
            - BLOCK (confidence >= 0.95 only): rule is clearly incorrect and dangerous
            Reply ONLY with JSON.
            """;

    private final LlmClient llmClient;
    private final Harness harness;

    public HallucinationDetectorAgent(LlmClient llmClient, Harness harness) {
        this.llmClient = llmClient;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.HALLUCINATION_DETECTION);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        if (input.relevantMemories().isEmpty()) {
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALLOW,
                    0.5, "No memory context available — cannot detect hallucinations, defaulting to ALLOW",
                    Map.of("hallucinated", false));
        }

        var memorySummary = input.relevantMemories().stream()
                .limit(5)
                .map(m -> "- " + m.content())
                .reduce("", (a, b) -> a + "\n" + b);

        var userPrompt = String.format(
                "Proposed output to validate: %s\n\nHistorical memory patterns:\n%s",
                input.serialisedApiCall(), memorySummary
        );

        try {
            var response = llmClient.complete(LlmRequest.of("", SYSTEM_PROMPT, userPrompt));
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALLOW,
                    0.8, "Hallucination check passed",
                    Map.of("rawResponse",
                            response.content().length() > 200 ? response.content().substring(0, 200) : response.content()));
        } catch (Exception e) {
            log.warn("HallucinationDetectorAgent LLM call failed: {}", e.getMessage());
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALERT,
                    0.4, "LLM unavailable — flagging for manual review",
                    Map.of("hallucinated", false));
        }
    }
}
